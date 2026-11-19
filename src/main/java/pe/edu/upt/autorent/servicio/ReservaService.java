package pe.edu.upt.autorent.servicio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upt.autorent.modelo.*;
import pe.edu.upt.autorent.servicio.contrato.ReservaUseCase;
import pe.edu.upt.autorent.servicio.evento.DevolucionRegistradaEvento;
import pe.edu.upt.autorent.servicio.evento.PagoConfirmadoEvento;
import pe.edu.upt.autorent.repositorio.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
/**
 * Servicio de negocio: coordina reservas, pagos, alquileres y devoluciones.
 *
 * Implementa el puerto de entrada {@link ReservaUseCase}. Solo conoce los puertos de salida
 * (interfaces del paquete repositorio), nunca JPA ni MySQL: así se respeta la arquitectura hexagonal.
 * La anotación @Transactional hace que cada método se ejecute en una sola transacción:
 * si algo falla a la mitad, no se guarda nada.
 *
 * Fechas: "hoy" y "ahora" salen del Clock configurado con la zona horaria de Perú
 * (ver ConfiguracionGeneral), para que el sistema funcione igual en un servidor en la nube.
 *
 * Eventos: cuando un pago se confirma o se registra una devolución se publica un evento.
 * Después de guardar, otro componente emite la boleta o la nota de crédito y hace el reembolso
 * en la pasarela (ver EventosDeNegocioListener). Así una falla de internet no deshace el pago.
 */

@Service
// READ COMMITTED: después de tomar un candado, cada consulta ve lo que otras transacciones ya guardaron.
// Con el modo por defecto de MySQL (REPEATABLE READ) la verificación de cruces leía una "foto" vieja
// y varias reservas simultáneas del mismo auto pasaban a la vez.
@Transactional(isolation = Isolation.READ_COMMITTED)
public class ReservaService implements ReservaUseCase {
    private static final Logger log = LoggerFactory.getLogger(ReservaService.class);
    /** Un cliente puede tener como máximo esta cantidad de reservas esperando pago (evita acaparar autos). */
    static final int MAXIMO_RESERVAS_PENDIENTES_POR_CLIENTE = 2;

    // Puertos de salida: Spring inyecta aquí los adaptadores del paquete dao.
    private final AutoRepositoryPort autos;
    private final UsuarioRepositoryPort usuarios;
    private final ReservaRepositoryPort reservas;
    private final PagoRepositoryPort pagos;
    private final DevolucionRepositoryPort devoluciones;
    private final ApplicationEventPublisher eventos;
    private final BitacoraReservas bitacora;
    private final Clock reloj;
    private final boolean payPalHabilitado;
    /** Horas que tiene el cliente para pagar el anticipo antes de que la reserva venza. */
    private final long horasParaPagar;
    /** Minutos que puede quedar abierto un pago en línea antes de considerarlo abandonado. */
    private final long minutosPagoEnLinea;

    public ReservaService(AutoRepositoryPort autos, UsuarioRepositoryPort usuarios,
                          ReservaRepositoryPort reservas, PagoRepositoryPort pagos,
                          DevolucionRepositoryPort devoluciones, ApplicationEventPublisher eventos,
                          BitacoraReservas bitacora, Clock reloj,
                          PayPalPort payPal,
                          @Value("${autorent.reservas.horas-para-pagar:24}") long horasParaPagar,
                          @Value("${autorent.pagos.minutos-pago-en-linea:30}") long minutosPagoEnLinea) {
        this.autos = autos; this.usuarios = usuarios; this.reservas = reservas;
        this.pagos = pagos; this.devoluciones = devoluciones; this.eventos = eventos; this.bitacora = bitacora; this.reloj = reloj;
        this.payPalHabilitado = payPal != null && payPal.configurado();
        this.horasParaPagar = horasParaPagar; this.minutosPagoEnLinea = minutosPagoEnLinea;
    }

    private LocalDate hoy() { return LocalDate.now(reloj); }
    private LocalDateTime ahora() { return LocalDateTime.now(reloj); }

    /** Guarda la reserva con su nuevo estado y anota el cambio en la bitácora. */
    private Reserva cambiarEstado(Reserva r, EstadoReserva nuevo, AccionReserva accion, String usuario, String detalle) {
        Reserva guardada = reservas.save(r.conEstado(nuevo));
        bitacora.anotar(r.id(), accion, r.estado(), nuevo, usuario, detalle);
        return guardada;
    }

    /** Rechaza un pago pendiente dejando quién, cuándo y por qué, y lo anota en la bitácora. */
    private void rechazar(Pago p, String usuario, String motivo) {
        pagos.save(p.procesado(EstadoPago.RECHAZADO, ahora(), usuario, motivo));
        bitacora.anotar(p.reservaId(), AccionReserva.PAGO_RECHAZADO, usuario, p.resumen() + ". Motivo: " + motivo);
    }

    /**
     * Deuda de un cliente: alquileres FINALIZADOS a los que todavía les falta pagar
     * (por ejemplo, un cargo por retraso o por daños). Con deuda no puede hacer otra reserva.
     */
    @Override @Transactional(readOnly = true) public BigDecimal deudaDelCliente(Long clienteId) {
        BigDecimal deuda = BigDecimal.ZERO;
        for (Reserva r : reservas.findByClienteId(clienteId)) {
            if (r.estado() != EstadoReserva.FINALIZADA) continue;
            BigDecimal saldo = r.total().subtract(totalPagado(r.id()));
            if (saldo.signum() > 0) deuda = deuda.add(saldo);
        }
        return deuda.setScale(2, RoundingMode.HALF_UP);
    }

    private void validarSinDeuda(Usuario cliente) {
        BigDecimal deuda = deudaDelCliente(cliente.id());
        if (deuda.signum() > 0)
            throw new IllegalArgumentException("El cliente tiene una deuda pendiente de S/ " + deuda
                    + " de un alquiler anterior. Debe pagarla antes de hacer una nueva reserva.");
    }

    private static String descripcionReserva(Reserva r) {
        return r.auto().marca() + " " + r.auto().modelo() + " (" + r.auto().placa() + "), del " + r.fechaInicio()
                + " al " + r.fechaFin() + ", " + r.dias() + " día(s) x S/ " + r.tarifaDiaria() + " = S/ " + r.total();
    }

    // =================================================================================
    // Creación de reservas
    // =================================================================================

    /**
     * Registra una reserva en línea y su anticipo.
     * Yape: la reserva queda PENDIENTE_VALIDACION hasta que un trabajador verifique la operación.
     * Efectivo: la reserva queda PENDIENTE_PAGO hasta que el cliente pague en la sucursal (plazo de 24 horas).
     * Tarjeta o PayPal: la reserva queda PENDIENTE_PAGO y el cliente pasa a la pantalla de pago en línea;
     * la pasarela la confirma al instante.
     */
    @Override public Reserva crearReservaOnline(String username, Long autoId, LocalDate inicio, LocalDate fin,
                                                 BigDecimal anticipo, MetodoPago metodo, String telefonoYape,
                                                 String codigoOperacion, String telefonoDevolucion) {
        Usuario cliente = usuarios.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la cuenta del cliente."));
        // Candado sobre el cliente: si envía varias solicitudes a la vez, se atienden una por una
        // y el límite de reservas pendientes y la regla de deuda se respetan siempre.
        cliente = bloquearCliente(cliente.id());
        if (cliente.rol() != Rol.CLIENTE || !cliente.activo()) throw new IllegalArgumentException("Solo un cliente activo puede reservar.");
        // Evita que un cliente bloquee varios autos sin pagar ninguno.
        long pendientes = reservas.findByClienteId(cliente.id()).stream()
                .filter(r -> r.estado() == EstadoReserva.PENDIENTE_PAGO || r.estado() == EstadoReserva.PENDIENTE_VALIDACION).count();
        if (pendientes >= MAXIMO_RESERVAS_PENDIENTES_POR_CLIENTE)
            throw new IllegalArgumentException("Tiene " + pendientes + " reservas esperando pago. Pague o cancele una antes de crear otra.");
        validarSinDeuda(cliente);
        // 1) Valida fechas, disponibilidad y anticipo mínimo; 2) valida los datos del pago.
        Reserva reserva = construirReserva(cliente, autoId, inicio, fin, anticipo, telefonoDevolucion);
        validarDatosPago(metodo, telefonoYape, codigoOperacion, true, true);
        if (metodo == MetodoPago.EFECTIVO && !telefonoValido(telefonoDevolucion))
            throw new IllegalArgumentException("Si reserva con efectivo, ingrese un número móvil válido para la devolución.");
        EstadoReserva estado = metodo == MetodoPago.YAPE ? EstadoReserva.PENDIENTE_VALIDACION : EstadoReserva.PENDIENTE_PAGO;
        reserva = reservas.save(reserva.conEstado(estado));
        bitacora.anotar(reserva.id(), AccionReserva.CREADA, null, estado, username, "Reserva web: " + descripcionReserva(reserva));
        // El anticipo se guarda como PENDIENTE: todavía nadie verificó el dinero.
        Pago anticipoPendiente = pagos.save(Pago.nuevo(reserva.id(), anticipo.setScale(2, RoundingMode.HALF_UP), metodo,
                TipoPago.ANTICIPO, EstadoPago.PENDIENTE, origenDelPago(metodo, telefonoYape, telefonoDevolucion),
                codigoDelPago(metodo, codigoOperacion), ahora(), username));
        bitacora.anotar(reserva.id(), AccionReserva.PAGO_REGISTRADO, username, anticipoPendiente.resumen() + " (pendiente)");
        return reserva;
    }

    /**
     * Registra una reserva desde el mostrador (la hace un trabajador).
     * Como el trabajador recibe el dinero en persona, el anticipo queda CONFIRMADO
     * y la reserva pasa directamente a CONFIRMADA.
     */
    @Override public Reserva crearReservaPresencial(Long clienteId, Long autoId, LocalDate inicio, LocalDate fin,
                                                     BigDecimal anticipo, MetodoPago metodo, String telefonoYape,
                                                     String codigoOperacion, String telefonoDevolucion, String trabajador) {
        Usuario cliente = usuarios.findById(clienteId)
                .orElseThrow(() -> new IllegalArgumentException("Seleccione un cliente registrado."));
        cliente = bloquearCliente(cliente.id());
        if (cliente.rol() != Rol.CLIENTE || !cliente.activo()) throw new IllegalArgumentException("La cuenta seleccionada no es un cliente activo.");
        validarSinDeuda(cliente);
        Reserva reserva = construirReserva(cliente, autoId, inicio, fin, anticipo, telefonoDevolucion);
        validarDatosPago(metodo, telefonoYape, codigoOperacion, metodo == MetodoPago.EFECTIVO, false);
        if (metodo == MetodoPago.EFECTIVO && !telefonoValido(telefonoDevolucion))
            throw new IllegalArgumentException("Registre un número móvil válido para la devolución de pagos en efectivo.");
        reserva = reservas.save(reserva.conEstado(EstadoReserva.CONFIRMADA));
        bitacora.anotar(reserva.id(), AccionReserva.CREADA, null, EstadoReserva.CONFIRMADA, trabajador,
                "Reserva en mostrador: " + descripcionReserva(reserva));
        Pago anticipoPagado = pagos.save(Pago.nuevo(reserva.id(), anticipo.setScale(2, RoundingMode.HALF_UP), metodo,
                TipoPago.ANTICIPO, EstadoPago.PENDIENTE, origenDelPago(metodo, telefonoYape, telefonoDevolucion),
                codigoDelPago(metodo, codigoOperacion), ahora(), trabajador)
                .procesado(EstadoPago.CONFIRMADO, ahora(), trabajador, "Recibido en mostrador"));
        bitacora.anotar(reserva.id(), AccionReserva.PAGO_CONFIRMADO, trabajador, anticipoPagado.resumen() + " (recibido en mostrador)");
        eventos.publishEvent(new PagoConfirmadoEvento(anticipoPagado.id()));
        return reserva;
    }

    /**
     * Arma la reserva y aplica las reglas comunes a cualquier canal:
     * vehículo DISPONIBLE, fechas válidas (no pasadas, máximo 30 días de alquiler y 180 de anticipación),
     * sin cruce con otra reserva y anticipo de al menos el 20 % del total.
     */
    private Reserva construirReserva(Usuario cliente, Long autoId, LocalDate inicio, LocalDate fin,
                                     BigDecimal anticipo, String telefonoDevolucion) {
        validarTelefonoDevolucionSiIngresado(telefonoDevolucion);
        // Bloqueo pesimista (SELECT ... FOR UPDATE): evita que dos personas reserven el mismo auto a la vez.
        Auto auto = autos.findByIdForUpdate(autoId).orElseThrow(() -> new IllegalArgumentException("Vehículo no encontrado."));
        if (auto.estado() != EstadoAuto.DISPONIBLE) throw new IllegalArgumentException("El vehículo no está habilitado para reservas.");
        int dias = ReglasAlquiler.validarPeriodo(inicio, fin, hoy());
        // Un cruce existe si otra reserva activa ocupa alguno de los mismos días.
        if (reservas.existsCruce(autoId, inicio, fin, EstadoReserva.BLOQUEAN_DISPONIBILIDAD))
            throw new IllegalArgumentException("El vehículo ya tiene una reserva que se cruza con esas fechas.");
        // Total = tarifa diaria x días, redondeado a 2 decimales (se usa BigDecimal para no perder céntimos).
        BigDecimal total = auto.tarifaDiaria().multiply(BigDecimal.valueOf(dias)).setScale(2, RoundingMode.HALF_UP);
        ReglasAlquiler.validarAnticipo(anticipo, total);
        return new Reserva(null, auto, cliente, inicio, fin, dias, auto.tarifaDiaria(), total,
                EstadoReserva.PENDIENTE_PAGO, telefonoValido(telefonoDevolucion) ? normalizarTelefono(telefonoDevolucion) : null,
                null, ahora());
    }

    /**
     * Valida los datos del pago según el método:
     * Yape exige celular de 9 dígitos y un código de operación que no se haya usado antes
     * (así se evita registrar dos veces el mismo comprobante).
     * Tarjeta y PayPal solo se aceptan en la web (el cliente paga en la pasarela), nunca en mostrador.
     */
    private void validarDatosPago(MetodoPago metodo, String telefono, String codigo, boolean efectivoPermitido, boolean enLineaPermitido) {
        if (metodo == null) throw new IllegalArgumentException("Seleccione un método de pago.");
        if (metodo.esEnLinea()) {
            if (!enLineaPermitido) throw new IllegalArgumentException("El pago con tarjeta o PayPal lo realiza el cliente desde la web.");
            if (metodo == MetodoPago.PAYPAL && !payPalHabilitado)
                throw new IllegalArgumentException("El pago con PayPal no está disponible en este momento. Use tarjeta.");
            return;
        }
        if (telefono != null && !telefono.isBlank() && !telefonoValido(telefono))
            throw new IllegalArgumentException("El número de Yape debe tener 9 dígitos y comenzar con 9, sin +51.");
        if (metodo == MetodoPago.YAPE) {
            if (!telefonoValido(telefono)) throw new IllegalArgumentException("Para Yape ingrese un número móvil válido de 9 dígitos.");
            if (codigo == null || !codigo.trim().matches("[A-Za-z0-9-]{4,40}"))
                throw new IllegalArgumentException("Ingrese el código de operación de Yape (4 a 40 caracteres).");
            if (pagos.existsCodigoOperacion(codigo.trim())) throw new IllegalArgumentException("Ese código de operación ya fue registrado.");
        } else if (!efectivoPermitido) {
            throw new IllegalArgumentException("El método seleccionado no es válido.");
        }
    }

    /** Teléfono desde el que pagó (Yape) o al que se devolvería el dinero (efectivo). Los pagos en línea no lo usan. */
    private String origenDelPago(MetodoPago metodo, String telefonoYape, String telefonoDevolucion) {
        if (metodo == MetodoPago.YAPE) return normalizarTelefono(telefonoYape);
        if (metodo == MetodoPago.EFECTIVO) return normalizarTelefono(telefonoDevolucion);
        return null;
    }

    /** El código de Yape se guarda en mayúsculas; el de la pasarela lo asigna ella misma al cobrar. */
    private String codigoDelPago(MetodoPago metodo, String codigoOperacion) {
        return metodo == MetodoPago.YAPE ? codigoOperacion.trim().toUpperCase(Locale.ROOT) : null;
    }

    /** Si el cliente escribió un número para devoluciones, comprueba que sea un celular peruano válido. */
    private void validarTelefonoDevolucionSiIngresado(String telefono) {
        if (telefono != null && !telefono.isBlank() && !telefonoValido(telefono))
            throw new IllegalArgumentException("El número de devolución debe tener 9 dígitos, comenzar con 9 y no incluir +51.");
    }

    /** Delegan en TelefonoPeru (modelo) la validación y el formato del celular. */
    private boolean telefonoValido(String telefono) {
        return TelefonoPeru.esMovilValido(telefono);
    }

    private String normalizarTelefono(String telefono) {
        return TelefonoPeru.normalizar(telefono);
    }

    // =================================================================================
    // Consultas
    // =================================================================================

    /** El cliente solo ve sus reservas; el personal ve todas. */
    @Override @Transactional(readOnly = true) public List<Reserva> listarReservas(String username) {
        Usuario u = usuarios.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        return u.rol() == Rol.CLIENTE ? reservas.findByClienteId(u.id()) : reservas.findAll();
    }
    @Override @Transactional(readOnly = true) public List<Reserva> listarTodas() { return reservas.findAll(); }
    @Override @Transactional(readOnly = true) public Reserva obtenerReserva(Long id) {
        return reservas.findById(id).orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada."));
    }

    /** Obtiene la reserva con bloqueo para que nadie la modifique mientras se procesa. */
    /** Bloquea al cliente hasta el fin de la transacción (orden de candados: primero cliente, luego auto). */
    private Usuario bloquearCliente(Long clienteId) {
        return usuarios.findByIdForUpdate(clienteId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la cuenta del cliente."));
    }

    private Reserva obtenerReservaParaActualizar(Long id) {
        return reservas.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada."));
    }

    // =================================================================================
    // Confirmación y rechazo de pagos (personal)
    // =================================================================================

    /**
     * El trabajador confirma un pago pendiente (por ejemplo, verificó el Yape en el celular de la empresa).
     * Los pagos en línea no se confirman a mano: los confirma la pasarela.
     */
    @Override public void confirmarPago(Long pagoId, String trabajador) {
        Pago consulta = pagos.findById(pagoId).orElseThrow(() -> new IllegalArgumentException("Pago no encontrado."));
        Reserva r = obtenerReservaParaActualizar(consulta.reservaId());
        Pago pago = pagos.findById(pagoId).orElseThrow(() -> new IllegalArgumentException("Pago no encontrado."));
        if (pago.estado() != EstadoPago.PENDIENTE)
            throw new IllegalArgumentException("Este pago ya fue procesado.");
        if (pago.metodo().esEnLinea())
            throw new IllegalArgumentException("Los pagos con tarjeta o PayPal los confirma la pasarela, no el personal.");
        if (pago.metodo() == MetodoPago.YAPE && (pago.codigoOperacion() == null || pago.telefonoOrigen() == null))
            throw new IllegalArgumentException("El pago Yape no tiene datos de operación completos.");
        aplicarPagoConfirmado(r, pago, trabajador);
    }

    /**
     * Regla común a cualquier pago confirmado (personal o pasarela):
     * el anticipo solo vale si la reserva sigue pendiente; el saldo, si está confirmada o tiene deuda;
     * nunca se cobra más que el total; si el anticipo llega al 20 %, la reserva pasa a CONFIRMADA.
     * Al final se publica el evento para emitir la boleta.
     */
    public Pago aplicarPagoConfirmado(Reserva r, Pago pago, String usuario) {
        BigDecimal totalConPago = verificarPagoAplicable(r, pago);
        String comoSeConfirmo = pago.metodo().esEnLinea() ? "Aprobado por la pasarela" : "Verificado por el personal";
        Pago confirmado = pagos.save(pago.procesado(EstadoPago.CONFIRMADO, ahora(), usuario, comoSeConfirmo));
        String detalle = confirmado.resumen() + ". " + comoSeConfirmo + ". Total pagado: S/ " + totalConPago + " de S/ " + r.total();
        if (pago.tipo() == TipoPago.ANTICIPO &&
                totalConPago.compareTo(ReglasAlquiler.minimoAnticipo(r.total())) >= 0) {
            cambiarEstado(r, EstadoReserva.CONFIRMADA, AccionReserva.PAGO_CONFIRMADO, usuario, detalle);
        } else {
            bitacora.anotar(r.id(), AccionReserva.PAGO_CONFIRMADO, usuario, detalle);
        }
        eventos.publishEvent(new PagoConfirmadoEvento(confirmado.id()));
        return confirmado;
    }

    /**
     * Comprueba, ANTES de cobrar, que el pago todavía se puede aplicar a la reserva.
     * La pasarela la usa antes de cobrar la tarjeta, para no cobrar algo que después no se podría registrar.
     * Devuelve el total pagado que tendría la reserva con este pago.
     */
    public BigDecimal verificarPagoAplicable(Reserva r, Pago pago) {
        if (pago.tipo() == TipoPago.ANTICIPO && r.estado() != EstadoReserva.PENDIENTE_PAGO &&
                r.estado() != EstadoReserva.PENDIENTE_VALIDACION)
            throw new IllegalArgumentException("La reserva ya no admite confirmar ese anticipo.");
        if (pago.tipo() == TipoPago.SALDO && r.estado() != EstadoReserva.CONFIRMADA && r.estado() != EstadoReserva.FINALIZADA)
            throw new IllegalArgumentException("La reserva ya no admite pagos de saldo.");
        // Nunca se puede cobrar más que el total de la reserva.
        BigDecimal totalConPago = totalPagado(r.id()).add(pago.monto());
        if (totalConPago.compareTo(r.total()) > 0)
            throw new IllegalArgumentException("El pago supera el total de la reserva.");
        return totalConPago;
    }

    /**
     * El trabajador rechaza un pago que no pudo verificar.
     * Si era el anticipo, la reserva vuelve a PENDIENTE_PAGO y el cliente puede reintentar.
     */
    @Override public void rechazarPago(Long pagoId, String trabajador, String motivo) {
        ValidacionDatos.textoExplicativo(motivo, "Indique el motivo del rechazo (por ejemplo: el Yape no llegó o el código no coincide).");
        Pago consulta = pagos.findById(pagoId).orElseThrow(() -> new IllegalArgumentException("Pago no encontrado."));
        Reserva r = obtenerReservaParaActualizar(consulta.reservaId());
        Pago pago = pagos.findById(pagoId).orElseThrow(() -> new IllegalArgumentException("Pago no encontrado."));
        if (pago.estado() != EstadoPago.PENDIENTE)
            throw new IllegalArgumentException("Solo puede rechazarse un pago pendiente.");
        if (r.estado() == EstadoReserva.CANCELADA || r.estado() == EstadoReserva.EN_CURSO || r.estado() == EstadoReserva.VENCIDA
                || r.estado() == EstadoReserva.NO_PRESENTADO)
            throw new IllegalArgumentException("La reserva ya está cerrada.");
        rechazar(pago, trabajador, motivo.trim());
        if (pago.tipo() == TipoPago.ANTICIPO && r.estado() != EstadoReserva.PENDIENTE_PAGO)
            cambiarEstado(r, EstadoReserva.PENDIENTE_PAGO, AccionReserva.PAGO_RECHAZADO, trabajador,
                    "La reserva vuelve a esperar el anticipo.");
    }

    /**
     * El cliente vuelve a enviar su anticipo después de un rechazo (o de un pago en línea que no terminó).
     * Solo se permite si la reserva es suya, sigue pendiente y no tiene otro pago en curso.
     */
    @Override public Reserva reintentarAnticipo(String username, Long reservaId, BigDecimal anticipo,
                                                MetodoPago metodo, String telefonoYape, String codigoOperacion,
                                                String telefonoDevolucion) {
        Reserva r = obtenerReservaParaActualizar(reservaId);
        Usuario u = usuarios.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado."));
        if (!u.activo() || u.rol() != Rol.CLIENTE || !u.id().equals(r.cliente().id()))
            throw new IllegalArgumentException("Esta reserva no pertenece a su cuenta activa.");
        if (r.estado() != EstadoReserva.PENDIENTE_PAGO && r.estado() != EstadoReserva.PENDIENTE_VALIDACION)
            throw new IllegalArgumentException("Esta reserva no admite reintentos de anticipo.");
        if (pagos.existePendiente(reservaId) || totalPagado(reservaId).signum() > 0)
            throw new IllegalArgumentException("Ya existe un pago pendiente o confirmado para esta reserva.");
        if (r.fechaInicio().isBefore(hoy()))
            throw new IllegalArgumentException("No se puede reintentar el anticipo de una reserva con fecha de inicio vencida.");
        ReglasAlquiler.validarAnticipo(anticipo, r.total());
        validarDatosPago(metodo, telefonoYape, codigoOperacion, true, true);
        validarTelefonoDevolucionSiIngresado(telefonoDevolucion);
        String destino = r.telefonoDevolucion();
        if (metodo == MetodoPago.EFECTIVO) {
            destino = telefonoValido(telefonoDevolucion) ? normalizarTelefono(telefonoDevolucion) : destino;
            if (!telefonoValido(destino))
                throw new IllegalArgumentException("Indique el número para una posible devolución en efectivo.");
        }
        EstadoReserva estado = metodo == MetodoPago.YAPE ? EstadoReserva.PENDIENTE_VALIDACION : EstadoReserva.PENDIENTE_PAGO;
        EstadoReserva antes = r.estado();
        r = reservas.save(new Reserva(r.id(), r.auto(), r.cliente(), r.fechaInicio(), r.fechaFin(), r.dias(),
                r.tarifaDiaria(), r.total(), estado, destino, r.observacion(), r.creadoEn()));
        Pago nuevo = pagos.save(Pago.nuevo(r.id(), anticipo.setScale(2, RoundingMode.HALF_UP), metodo,
                TipoPago.ANTICIPO, EstadoPago.PENDIENTE,
                metodo == MetodoPago.EFECTIVO ? destino : origenDelPago(metodo, telefonoYape, telefonoDevolucion),
                codigoDelPago(metodo, codigoOperacion), ahora(), username));
        bitacora.anotar(r.id(), AccionReserva.PAGO_REGISTRADO, antes, estado, username,
                "Nuevo intento de anticipo: " + nuevo.resumen() + " (pendiente)");
        return r;
    }

    /**
     * Registra en mostrador el pago del saldo (lo que falta después del anticipo, o un cargo por devolución tardía).
     * Efectivo: queda CONFIRMADO al momento. Yape: queda PENDIENTE hasta su verificación.
     */
    @Override public Pago registrarPagoSaldo(Long reservaId, BigDecimal monto, MetodoPago metodo,
                                              String telefonoYape, String codigoOperacion, String telefonoDevolucion, String trabajador) {
        Reserva r = obtenerReservaParaActualizar(reservaId);
        if (r.estado() != EstadoReserva.CONFIRMADA && r.estado() != EstadoReserva.FINALIZADA)
            throw new IllegalArgumentException("Solo se puede completar el pago de una reserva confirmada o con deuda pendiente.");
        ReglasAlquiler.validarMonto(monto);
        if (pagos.existePendiente(reservaId))
            throw new IllegalArgumentException("Hay un pago pendiente de validación. Confírmelo o rechácelo antes de registrar otro.");
        BigDecimal restante = r.total().subtract(totalPagado(reservaId)).setScale(2, RoundingMode.HALF_UP);
        if (restante.signum() <= 0) throw new IllegalArgumentException("La reserva no tiene saldo pendiente.");
        if (monto.compareTo(restante) > 0)
            throw new IllegalArgumentException("El pago supera el saldo pendiente de S/ " + restante + ".");
        validarDatosPago(metodo, telefonoYape, codigoOperacion, true, false);
        validarTelefonoDevolucionSiIngresado(telefonoDevolucion);
        if (metodo == MetodoPago.EFECTIVO) {
            String destino = (telefonoDevolucion == null || telefonoDevolucion.isBlank()) ? r.telefonoDevolucion() : telefonoDevolucion;
            if (!telefonoValido(destino))
                throw new IllegalArgumentException("Para pagos en efectivo, indique un número móvil para una posible devolución.");
            if (!normalizarTelefono(destino).equals(r.telefonoDevolucion())) {
                r = new Reserva(r.id(), r.auto(), r.cliente(), r.fechaInicio(), r.fechaFin(), r.dias(), r.tarifaDiaria(),
                        r.total(), r.estado(), normalizarTelefono(destino), r.observacion(), r.creadoEn());
                reservas.save(r);
            }
        }
        Pago pago = pagos.save(Pago.nuevo(reservaId, monto.setScale(2, RoundingMode.HALF_UP), metodo,
                TipoPago.SALDO, EstadoPago.PENDIENTE,
                metodo == MetodoPago.YAPE ? normalizarTelefono(telefonoYape) : r.telefonoDevolucion(),
                codigoDelPago(metodo, codigoOperacion), ahora(), trabajador));
        bitacora.anotar(reservaId, AccionReserva.PAGO_REGISTRADO, trabajador, pago.resumen() + " registrado en mostrador");
        // El efectivo lo recibe el trabajador en la mano: se confirma en el acto (y se emite su boleta).
        if (metodo == MetodoPago.EFECTIVO) return aplicarPagoConfirmado(r, pago, trabajador);
        return pago;
    }

    // =================================================================================
    // Entrega y recepción del vehículo
    // =================================================================================

    /**
     * Entrega del vehículo: la reserva pasa a EN_CURSO.
     * Reglas: reserva CONFIRMADA, dentro del periodo [inicio, fin), saldo totalmente pagado,
     * el auto no puede estar en taller ni inactivo y no puede estar entregado en otro alquiler.
     */
    @Override public void entregarVehiculo(Long reservaId, String trabajador, String observacion) {
        Reserva r = obtenerReservaParaActualizar(reservaId);
        if (r.estado() != EstadoReserva.CONFIRMADA) throw new IllegalArgumentException("La reserva debe estar confirmada antes de entregar el vehículo.");
        LocalDate hoy = hoy();
        if (hoy.isBefore(r.fechaInicio())) throw new IllegalArgumentException("Todavía no llega la fecha de inicio del alquiler.");
        if (!hoy.isBefore(r.fechaFin())) throw new IllegalArgumentException("El periodo de alquiler ya terminó. No se puede entregar el auto.");
        if (totalPagado(reservaId).compareTo(r.total()) < 0)
            throw new IllegalArgumentException("No se puede entregar el vehículo: primero debe cancelarse el saldo total.");

        Auto auto = autos.findByIdForUpdate(r.auto().id()).orElseThrow(() -> new IllegalArgumentException("Vehículo no encontrado."));
        if (auto.estado() == EstadoAuto.MANTENIMIENTO)
            throw new IllegalArgumentException("El vehículo está en mantenimiento. Finalice el mantenimiento antes de entregarlo.");
        if (auto.estado() == EstadoAuto.INACTIVO)
            throw new IllegalArgumentException("El vehículo está inactivo y no puede entregarse.");
        if (reservas.existsOtroAlquilerEnCurso(r.auto().id(), reservaId))
            throw new IllegalArgumentException("El auto sigue entregado en otro alquiler. Registre primero su recepción.");
        cambiarEstado(r, EstadoReserva.EN_CURSO, AccionReserva.ENTREGA, trabajador,
                "Entregado a " + r.cliente().nombres() + " " + r.cliente().apellidos() + ". Estado del auto: "
                        + textoOpcional(observacion, "sin observaciones"));
    }

    /**
     * Recepción del vehículo: el alquiler EN_CURSO pasa a FINALIZADA.
     * Si el auto vuelve después de la fecha pactada, cada día de retraso se cobra con la misma tarifa:
     * el total aumenta y la diferencia queda como saldo pendiente que se registra en caja.
     */
    @Override public void recibirVehiculo(Long reservaId, String trabajador, String observacion,
                                          BigDecimal cargoDanios, String conceptoCargo) {
        Reserva r = obtenerReservaParaActualizar(reservaId);
        if (r.estado() != EstadoReserva.EN_CURSO) throw new IllegalArgumentException("El vehículo no figura como alquiler en curso.");
        boolean hayCargo = cargoDanios != null && cargoDanios.signum() != 0;
        if (hayCargo) {
            if (cargoDanios.signum() < 0) throw new IllegalArgumentException("El cargo adicional no puede ser negativo.");
            ReglasAlquiler.validarMonto(cargoDanios);
            ValidacionDatos.textoExplicativo(conceptoCargo, "Indique el concepto del cargo adicional (por ejemplo: rayón en puerta, falta de combustible).");
        }
        LocalDate hoy = hoy();
        StringBuilder notas = new StringBuilder();
        int retraso = ReglasAlquiler.diasDeRetraso(r.fechaFin(), hoy);
        if (retraso > 0) {
            BigDecimal cargo = r.tarifaDiaria().multiply(BigDecimal.valueOf(retraso)).setScale(2, RoundingMode.HALF_UP);
            String nota = "Devuelto con " + retraso + " día(s) de retraso: S/ " + cargo + ".";
            r = r.conNuevoPeriodo(hoy, r.dias() + retraso, r.total().add(cargo), nota);
            notas.append(nota).append(' ');
            bitacora.anotar(r.id(), AccionReserva.CARGO_ADICIONAL, trabajador,
                    "Retraso de " + retraso + " día(s) x S/ " + r.tarifaDiaria() + " = S/ " + cargo + ". Nuevo total: S/ " + r.total());
        }
        if (hayCargo) {
            BigDecimal cargo = cargoDanios.setScale(2, RoundingMode.HALF_UP);
            String nota = "Cargo por " + conceptoCargo.trim() + ": S/ " + cargo + ".";
            r = r.conNuevoPeriodo(r.fechaFin(), r.dias(), r.total().add(cargo), (notas + nota).trim());
            bitacora.anotar(r.id(), AccionReserva.CARGO_ADICIONAL, trabajador,
                    conceptoCargo.trim() + ": S/ " + cargo + ". Nuevo total: S/ " + r.total());
        }
        BigDecimal saldo = r.total().subtract(totalPagado(r.id()));
        cambiarEstado(r, EstadoReserva.FINALIZADA, AccionReserva.RECEPCION, trabajador,
                "Estado del auto al recibirlo: " + textoOpcional(observacion, "sin observaciones")
                        + (saldo.signum() > 0 ? ". Queda saldo por cobrar: S/ " + saldo.setScale(2, RoundingMode.HALF_UP) : ". Sin deuda."));
    }

    private static String textoOpcional(String texto, String porDefecto) {
        return texto == null || texto.isBlank() ? porDefecto : texto.trim();
    }

    // =================================================================================
    // Cierre de reservas
    // =================================================================================

    /**
     * Cierra como VENCIDA una reserva cuya fecha de inicio pasó sin anticipo confirmado.
     * Los pagos que seguían pendientes se marcan como rechazados y el auto queda libre.
     */
    @Override public void vencerReservaSinPago(Long reservaId, String trabajador) {
        Reserva r = obtenerReservaParaActualizar(reservaId);
        if (r.estado() != EstadoReserva.PENDIENTE_PAGO && r.estado() != EstadoReserva.PENDIENTE_VALIDACION)
            throw new IllegalArgumentException("Solo se pueden cerrar reservas sin anticipo confirmado.");
        if (!r.fechaInicio().isBefore(hoy()))
            throw new IllegalArgumentException("El inicio del alquiler aún no ha vencido.");
        if (totalPagado(reservaId).signum() > 0)
            throw new IllegalArgumentException("La reserva tiene dinero confirmado. No puede cerrarse sin gestionar el reembolso.");
        cerrarComoVencida(r, trabajador, "Cerrada por el personal: llegó la fecha de inicio sin anticipo confirmado.");
    }

    /** Rechaza los pagos pendientes de la reserva y la marca como VENCIDA (el auto vuelve a estar libre). */
    private void cerrarComoVencida(Reserva r, String usuario, String motivo) {
        for (Pago p : pagos.findByReservaId(r.id())) {
            if (p.estado() == EstadoPago.PENDIENTE) rechazar(p, usuario, "Reserva vencida sin pago");
        }
        cambiarEstado(r, EstadoReserva.VENCIDA, AccionReserva.VENCIDA, usuario, motivo + " El vehículo quedó libre.");
    }

    /**
     * Cancela una reserva.
     * Cliente: solo sus reservas y hasta 24 horas antes del inicio.
     * Personal: puede anularla en cualquier momento antes de la entrega (por ejemplo, si el auto se averió).
     * Pagos pendientes: se rechazan. Pagos confirmados: generan una devolución por el mismo medio
     * (Yape o efectivo: al número registrado; tarjeta o PayPal: reembolso automático en la pasarela).
     */
    @Override public void cancelarReserva(Long reservaId, String username, String motivo) {
        Reserva r = obtenerReservaParaActualizar(reservaId);
        Usuario solicitante = usuarios.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        boolean esCliente = solicitante.rol() == Rol.CLIENTE;
        if (esCliente && !r.cliente().id().equals(solicitante.id()))
            throw new IllegalArgumentException("No puede cancelar una reserva que pertenece a otro cliente.");
        if (!r.estado().esActiva())
            throw new IllegalArgumentException("Esta reserva ya no se puede cancelar.");
        if (esCliente && !ReglasAlquiler.sePuedeCancelar(r.fechaInicio(), ahora()))
            throw new IllegalArgumentException("La cancelación debe realizarse como mínimo 24 horas antes del inicio del alquiler.");
        if (!esCliente)
            ValidacionDatos.textoExplicativo(motivo, "Indique el motivo de la anulación (queda registrado en el historial de la reserva).");
        String razon = textoOpcional(motivo, "Cancelada por el cliente");

        BigDecimal aDevolver = BigDecimal.ZERO;
        for (Pago p : pagos.findByReservaId(reservaId)) {
            if (p.estado() == EstadoPago.PENDIENTE) {
                rechazar(p, username, "Reserva cancelada");
            } else if (p.estado() == EstadoPago.CONFIRMADO) {
                registrarDevolucion(r, p, username);
                aDevolver = aDevolver.add(p.monto());
            }
        }
        cambiarEstado(r, EstadoReserva.CANCELADA, esCliente ? AccionReserva.CANCELADA : AccionReserva.ANULADA, username,
                razon + (aDevolver.signum() > 0 ? ". Se devolverá S/ " + aDevolver.setScale(2, RoundingMode.HALF_UP) : ". No había pagos confirmados."));
    }

    /** Crea la devolución de un pago confirmado y publica el evento (reembolso en pasarela y nota de crédito). */
    private void registrarDevolucion(Reserva r, Pago p, String usuario) {
        String destino = destinoDevolucion(r, p);
        Devolucion d = devoluciones.save(new Devolucion(null, r.id(), p.id(), p.monto(), p.metodo(), destino,
                EstadoDevolucion.PENDIENTE, ahora(), null, null, null));
        bitacora.anotar(r.id(), AccionReserva.DEVOLUCION_REGISTRADA, usuario,
                "Devolver S/ " + p.monto() + " por " + p.metodo().getEtiqueta() + " a " + destino
                        + (p.metodo().esEnLinea() ? " (reembolso automático en la pasarela)" : " (lo envía la sucursal)"));
        eventos.publishEvent(new DevolucionRegistradaEvento(d.id()));
    }

    /** A dónde vuelve el dinero: la misma tarjeta/PayPal, el mismo Yape o el número registrado para efectivo. */
    /**
     * Destino de un reembolso en línea, en pocas palabras y sin cortar texto: el dinero vuelve al mismo
     * medio con que se pagó. "Tarjeta **** 1111" si se pagó con tarjeta (también dentro de PayPal)
     * o "Cuenta PayPal". El correo del pagador queda en el detalle del pago.
     */
    static String destinoEnLinea(Pago p) {
        java.util.regex.Matcher ultimos = java.util.regex.Pattern.compile("\\*{4} ?(\\d{4})")
                .matcher(p.detalle() == null ? "" : p.detalle());
        if (ultimos.find()) return "Tarjeta **** " + ultimos.group(1);
        return p.metodo() == MetodoPago.PAYPAL ? "Cuenta PayPal" : "Tarjeta del pago";
    }

    private String destinoDevolucion(Reserva r, Pago p) {
        if (p.metodo().esEnLinea()) return destinoEnLinea(p);
        String telefono = p.telefonoOrigen() != null && !p.telefonoOrigen().isBlank() ? p.telefonoOrigen() : r.telefonoDevolucion();
        if (telefono == null || telefono.isBlank())
            throw new IllegalArgumentException("No existe un número de destino para devolver el pago en efectivo.");
        return telefono;
    }

    // =================================================================================
    // Devoluciones
    // =================================================================================

    @Override @Transactional(readOnly = true) public List<Pago> listarPagos() { return pagos.findAll(); }
    @Override @Transactional(readOnly = true) public List<Pago> listarPagosDeCliente(String username) {
        Usuario u = usuarios.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        if (u.rol() != Rol.CLIENTE) return pagos.findAll();
        return pagos.findByClienteId(u.id());
    }
    @Override @Transactional(readOnly = true) public List<Devolucion> listarDevoluciones() { return devoluciones.findAll(); }

    @Override @Transactional(readOnly = true) public List<Devolucion> listarDevolucionesDeCliente(String username) {
        Usuario u = usuarios.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        if (u.rol() != Rol.CLIENTE) return devoluciones.findAll();
        return devoluciones.findByClienteId(u.id());
    }

    /** El trabajador marca la devolución como COMPLETADA cuando ya envió el dinero al cliente. */
    @Override public void completarDevolucion(Long devolucionId, String trabajador, String codigoOperacion) {
        Devolucion d = devoluciones.findById(devolucionId).orElseThrow(() -> new IllegalArgumentException("Devolución no encontrada."));
        if (d.estado() != EstadoDevolucion.PENDIENTE) throw new IllegalArgumentException("La devolución ya fue completada.");
        // Sin evidencia no hay devolución: el código del Yape enviado (o del reembolso) prueba que el dinero salió.
        if (codigoOperacion == null || !codigoOperacion.trim().matches("[A-Za-z0-9-]{4,40}"))
            throw new IllegalArgumentException("Ingrese el código de operación del Yape o transferencia con que devolvió el dinero.");
        String codigo = codigoOperacion.trim().toUpperCase(Locale.ROOT);
        devoluciones.save(d.completada(ahora(), trabajador, codigo));
        bitacora.anotar(d.reservaId(), AccionReserva.DEVOLUCION_COMPLETADA, trabajador,
                "Devueltos S/ " + d.monto() + " a " + d.telefonoDestino() + " (op. " + codigo + ")");
    }

    /** Bitácora completa de una reserva (para la pantalla de detalle). */
    @Override @Transactional(readOnly = true) public List<EventoReserva> historialDe(Long reservaId) {
        return bitacora.de(reservaId);
    }

    /** Suma de los pagos CONFIRMADOS de una reserva (los pendientes y rechazados no cuentan). */
    private BigDecimal totalPagado(Long reservaId) {
        return pagos.totalConfirmado(reservaId).setScale(2, RoundingMode.HALF_UP);
    }

    // =================================================================================
    // Consultas para las pantallas (reglas que antes estaban en los controladores)
    // =================================================================================

    /** Saldo de cada reserva = total - pagos confirmados. */
    @Override @Transactional(readOnly = true)
    public Map<Long, BigDecimal> calcularSaldos(List<Reserva> listaReservas, List<Pago> listaPagos) {
        Map<Long, BigDecimal> saldos = new HashMap<>();
        for (Reserva r : listaReservas) saldos.put(r.id(), r.total());
        for (Pago p : listaPagos) {
            if (p.estado() == EstadoPago.CONFIRMADO && saldos.containsKey(p.reservaId()))
                saldos.put(p.reservaId(), saldos.get(p.reservaId()).subtract(p.monto()));
        }
        return saldos;
    }

    /** Reservas activas (pendientes o confirmadas) que aún están a más de 24 horas de su inicio. */
    @Override @Transactional(readOnly = true)
    public Set<Long> reservasCancelables(List<Reserva> listaReservas) {
        LocalDateTime ahora = ahora();
        Set<Long> ids = new HashSet<>();
        for (Reserva r : listaReservas) {
            if (r.estado().esActiva() && ReglasAlquiler.sePuedeCancelar(r.fechaInicio(), ahora)) ids.add(r.id());
        }
        return ids;
    }

    /** El personal puede anular cualquier reserva activa que todavía no se entregó. */
    @Override @Transactional(readOnly = true)
    public Set<Long> reservasAnulablesPorPersonal(List<Reserva> listaReservas) {
        Set<Long> ids = new HashSet<>();
        for (Reserva r : listaReservas) if (r.estado().esActiva()) ids.add(r.id());
        return ids;
    }

    /** Reservas pendientes sin ningún pago pendiente ni confirmado: el cliente puede volver a pagar. */
    @Override @Transactional(readOnly = true)
    public Set<Long> reservasReintentables(List<Reserva> listaReservas, List<Pago> listaPagos) {
        Set<Long> conPagoVigente = new HashSet<>();
        for (Pago p : listaPagos) {
            if (p.estado() == EstadoPago.PENDIENTE || p.estado() == EstadoPago.CONFIRMADO) conPagoVigente.add(p.reservaId());
        }
        Set<Long> ids = new HashSet<>();
        for (Reserva r : listaReservas) {
            boolean pendiente = r.estado() == EstadoReserva.PENDIENTE_PAGO || r.estado() == EstadoReserva.PENDIENTE_VALIDACION;
            if (pendiente && !conPagoVigente.contains(r.id())) ids.add(r.id());
        }
        return ids;
    }

    /** Pago en línea todavía pendiente de una reserva del cliente (para mostrarle el botón "Pagar"). */
    @Override @Transactional(readOnly = true)
    public Optional<Pago> buscarPagoEnLineaPendiente(String username, Long reservaId) {
        Usuario u = usuarios.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        Reserva r = obtenerReserva(reservaId);
        if (!r.cliente().id().equals(u.id())) return Optional.empty();
        return pagos.findByReservaId(reservaId).stream()
                .filter(p -> p.estado() == EstadoPago.PENDIENTE && p.metodo().esEnLinea()).findFirst();
    }

    // =================================================================================
    // Tareas automáticas (las ejecuta TareasProgramadas cada pocos minutos)
    // =================================================================================

    /** Pagos con tarjeta o PayPal que el cliente dejó a medias: después de 30 minutos se rechazan. */
    @Override public int liberarPagosEnLineaAbandonados() {
        int liberados = 0;
        for (Pago p : pagos.findPendientesRegistradosAntesDe(ahora().minusMinutes(minutosPagoEnLinea))) {
            if (!p.metodo().esEnLinea()) continue;
            rechazar(p, EventoReserva.SISTEMA, "El pago en línea no se completó en " + minutosPagoEnLinea + " minutos");
            liberados++;
        }
        if (liberados > 0) log.info("Pagos en línea abandonados liberados: {}", liberados);
        return liberados;
    }

    /**
     * Reservas web que no pagaron el anticipo: vencen 24 horas después de creadas o cuando llega su fecha de inicio.
     * No se tocan las que esperan la validación de un Yape (el cliente ya pagó; decide el personal).
     */
    @Override public int vencerReservasSinPago() {
        LocalDateTime limite = ahora().minusHours(horasParaPagar);
        int vencidas = 0;
        for (Reserva r : reservas.findByEstadoIn(List.of(EstadoReserva.PENDIENTE_PAGO))) {
            boolean plazoAgotado = r.creadoEn() != null && r.creadoEn().isBefore(limite);
            boolean inicioPasado = r.fechaInicio().isBefore(hoy());
            if (!plazoAgotado && !inicioPasado) continue;
            List<Pago> pagosReserva = pagos.findByReservaId(r.id());
            boolean tieneDinero = pagosReserva.stream().anyMatch(p -> p.estado() == EstadoPago.CONFIRMADO);
            boolean esperaYape = pagosReserva.stream().anyMatch(p -> p.estado() == EstadoPago.PENDIENTE && p.metodo() == MetodoPago.YAPE);
            if (tieneDinero || esperaYape) continue;
            cerrarComoVencida(obtenerReservaParaActualizar(r.id()), EventoReserva.SISTEMA,
                    inicioPasado ? "Llegó la fecha de inicio sin anticipo pagado." : "Pasaron " + horasParaPagar + " horas sin pagar el anticipo.");
            vencidas++;
        }
        if (vencidas > 0) log.info("Reservas vencidas por falta de pago: {}", vencidas);
        return vencidas;
    }

    /**
     * Cliente que no se presentó: la reserva estaba CONFIRMADA y terminó su periodo sin que recogiera el auto.
     * Política: el anticipo se retiene como penalidad; lo pagado como saldo se devuelve.
     */
    @Override public int marcarNoPresentados() {
        int marcadas = 0;
        for (Reserva r : reservas.findByEstadoIn(List.of(EstadoReserva.CONFIRMADA))) {
            if (hoy().isBefore(r.fechaFin())) continue;
            Reserva bloqueada = obtenerReservaParaActualizar(r.id());
            BigDecimal retenido = BigDecimal.ZERO;
            for (Pago p : pagos.findByReservaId(r.id())) {
                if (p.estado() == EstadoPago.PENDIENTE) rechazar(p, EventoReserva.SISTEMA, "Cliente no se presentó");
                else if (p.estado() == EstadoPago.CONFIRMADO && p.tipo() == TipoPago.SALDO) registrarDevolucion(bloqueada, p, EventoReserva.SISTEMA);
                else if (p.estado() == EstadoPago.CONFIRMADO) retenido = retenido.add(p.monto());
            }
            cambiarEstado(bloqueada, EstadoReserva.NO_PRESENTADO, AccionReserva.NO_PRESENTADO, EventoReserva.SISTEMA,
                    "Terminó el periodo sin recoger el auto. Anticipo retenido como penalidad: S/ "
                            + retenido.setScale(2, RoundingMode.HALF_UP) + "; lo pagado como saldo se devuelve.");
            marcadas++;
        }
        if (marcadas > 0) log.info("Reservas marcadas como no presentadas: {}", marcadas);
        return marcadas;
    }
}
