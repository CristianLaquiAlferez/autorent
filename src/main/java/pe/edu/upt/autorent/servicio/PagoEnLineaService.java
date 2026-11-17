package pe.edu.upt.autorent.servicio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upt.autorent.modelo.*;
import pe.edu.upt.autorent.repositorio.*;
import pe.edu.upt.autorent.servicio.contrato.PagoEnLineaUseCase;

/**
 * Servicio de pagos en línea: tarjeta (pasarela de prueba) y PayPal Sandbox.
 *
 * Reglas importantes:
 * - Solo el dueño de la reserva puede pagarla, y solo pagos PENDIENTES de tipo en línea.
 * - Antes de cobrar se verifica que el pago todavía se pueda registrar (reserva activa, sin exceder el total).
 * - Con PayPal se comprueba que el monto cobrado sea exactamente el esperado; si no, se devuelve el dinero.
 * - El número completo de la tarjeta nunca se guarda: solo marca y últimos 4 dígitos.
 * Al confirmar se usa la misma regla que el personal (ReservaService.aplicarPagoConfirmado),
 * que además dispara la emisión de la boleta electrónica.
 */
@Service
@Transactional
public class PagoEnLineaService implements PagoEnLineaUseCase {
    private static final Logger log = LoggerFactory.getLogger(PagoEnLineaService.class);

    private final PagoRepositoryPort pagos;
    private final ReservaRepositoryPort reservas;
    private final UsuarioRepositoryPort usuarios;
    private final DevolucionRepositoryPort devoluciones;
    private final PasarelaTarjetaPort tarjetas;
    private final PayPalPort payPal;
    private final ReservaService reglasDeReserva;
    private final BitacoraReservas bitacora;
    private final Clock reloj;

    public PagoEnLineaService(PagoRepositoryPort pagos, ReservaRepositoryPort reservas, UsuarioRepositoryPort usuarios,
                              DevolucionRepositoryPort devoluciones, PasarelaTarjetaPort tarjetas, PayPalPort payPal,
                              ReservaService reglasDeReserva, BitacoraReservas bitacora, Clock reloj) {
        this.pagos = pagos; this.reservas = reservas; this.usuarios = usuarios; this.devoluciones = devoluciones;
        this.tarjetas = tarjetas; this.payPal = payPal; this.reglasDeReserva = reglasDeReserva; this.bitacora = bitacora; this.reloj = reloj;
    }

    @Override @Transactional(readOnly = true) public boolean payPalDisponible() { return payPal.configurado(); }

    @Override @Transactional(readOnly = true)
    public DatosCheckout prepararCheckout(String username, Long pagoId) {
        Pago pago = pagoPendienteDelCliente(username, pagoId);
        Reserva r = reservas.findById(pago.reservaId()).orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada."));
        boolean conPayPal = payPal.configurado();
        return new DatosCheckout(pago, r, conPayPal, conPayPal ? payPal.clientId() : null, payPal.moneda(),
                conPayPal ? payPal.convertir(pago.monto()) : null, tarjetas.nombre());
    }

    /** Crea un pago de saldo PENDIENTE por todo lo que falta. El cliente elige tarjeta o PayPal en la pantalla de pago. */
    @Override public Pago iniciarPagoSaldo(String username, Long reservaId) {
        Usuario cliente = cliente(username);
        Reserva r = reservas.findByIdForUpdate(reservaId).orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada."));
        if (!r.cliente().id().equals(cliente.id())) throw new IllegalArgumentException("Esta reserva no pertenece a su cuenta.");
        if (r.estado() != EstadoReserva.CONFIRMADA && r.estado() != EstadoReserva.FINALIZADA)
            throw new IllegalArgumentException("Solo se puede pagar el saldo de una reserva confirmada.");
        if (pagos.existePendiente(reservaId)) {
            return pagos.findByReservaId(reservaId).stream()
                    .filter(p -> p.estado() == EstadoPago.PENDIENTE && p.metodo().esEnLinea()).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Ya hay un pago pendiente de validación para esta reserva."));
        }
        BigDecimal restante = r.total().subtract(pagos.totalConfirmado(reservaId)).setScale(2, RoundingMode.HALF_UP);
        if (restante.signum() <= 0) throw new IllegalArgumentException("La reserva no tiene saldo pendiente.");
        Pago pago = pagos.save(Pago.nuevo(reservaId, restante, MetodoPago.TARJETA, TipoPago.SALDO, EstadoPago.PENDIENTE,
                null, null, LocalDateTime.now(reloj), username));
        bitacora.anotar(reservaId, AccionReserva.PAGO_REGISTRADO, username, "Pago del saldo en línea iniciado: S/ " + restante);
        return pago;
    }

    @Override public ResultadoCobro pagarConTarjeta(String username, Long pagoId, DatosTarjeta tarjeta) {
        Pago pago = pagoPendienteDelCliente(username, pagoId);
        Reserva r = reservas.findByIdForUpdate(pago.reservaId()).orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada."));
        pago = pagos.findById(pagoId).orElseThrow();            // relectura después del bloqueo (evita doble cobro)
        if (pago.estado() != EstadoPago.PENDIENTE) throw new IllegalArgumentException("Este pago ya fue procesado.");
        reglasDeReserva.verificarPagoAplicable(r, pago);
        ResultadoCobro cobro = tarjetas.cobrar(tarjeta, pago.monto(), descripcion(r, pago));
        if (!cobro.aprobado()) {
            // El intento fallido también queda en el historial (solo marca y últimos 4 dígitos, nunca la tarjeta completa).
            bitacora.anotar(r.id(), AccionReserva.PAGO_EN_LINEA_FALLIDO, username,
                    "Tarjeta **** " + tarjeta.ultimos4() + ": " + cobro.mensaje());
            return cobro;
        }
        Pago conDatos = pago.conDatosPasarela(MetodoPago.TARJETA, cobro.codigoTransaccion(), cobro.detalle());
        reglasDeReserva.aplicarPagoConfirmado(r, conDatos, username);
        return cobro;
    }

    @Override public OrdenEnLinea crearOrdenPayPal(String username, Long pagoId) {
        if (!payPal.configurado()) throw new IllegalArgumentException("El pago con PayPal no está disponible.");
        Pago pago = pagoPendienteDelCliente(username, pagoId);
        Reserva r = reservas.findByIdForUpdate(pago.reservaId()).orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada."));
        reglasDeReserva.verificarPagoAplicable(r, pago);
        OrdenEnLinea orden = payPal.crearOrden("AUTORENT-PAGO-" + pago.id(), pago.monto(), descripcion(r, pago));
        // Se anota la orden en el pago para comprobar luego que el cliente aprobó ESTA orden y no otra.
        pagos.save(pago.conDatosPasarela(MetodoPago.PAYPAL, orden.id(), "PayPal (orden pendiente)"));
        return orden;
    }

    @Override public ResultadoCobro capturarPayPal(String username, Long pagoId, String ordenId) {
        Pago pago = pagoPendienteDelCliente(username, pagoId);
        Reserva r = reservas.findByIdForUpdate(pago.reservaId()).orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada."));
        pago = pagos.findById(pagoId).orElseThrow();
        if (pago.estado() != EstadoPago.PENDIENTE) throw new IllegalArgumentException("Este pago ya fue procesado.");
        if (ordenId == null || !ordenId.equals(pago.codigoOperacion()))
            throw new IllegalArgumentException("La orden de PayPal no corresponde a este pago.");
        reglasDeReserva.verificarPagoAplicable(r, pago);
        ResultadoCobro cobro = payPal.capturar(ordenId);
        if (!cobro.aprobado()) {
            bitacora.anotar(r.id(), AccionReserva.PAGO_EN_LINEA_FALLIDO, username, "PayPal: " + cobro.mensaje());
            return cobro;
        }
        BigDecimal esperado = payPal.convertir(pago.monto());
        if (cobro.montoCobrado() == null || cobro.montoCobrado().compareTo(esperado) != 0 || !payPal.moneda().equals(cobro.moneda())) {
            log.warn("PayPal cobró {} {} y se esperaba {} {}. Se devuelve.", cobro.montoCobrado(), cobro.moneda(), esperado, payPal.moneda());
            payPal.reembolsar(cobro.codigoTransaccion(), pago.monto());
            bitacora.anotar(r.id(), AccionReserva.PAGO_EN_LINEA_FALLIDO, username,
                    "PayPal cobró un monto distinto al esperado; se devolvió automáticamente (op. " + cobro.codigoTransaccion() + ").");
            return ResultadoCobro.rechazado("El monto cobrado por PayPal no coincide. Se devolvió el dinero.");
        }
        Pago conDatos = pago.conDatosPasarela(MetodoPago.PAYPAL, cobro.codigoTransaccion(), cobro.detalle());
        reglasDeReserva.aplicarPagoConfirmado(r, conDatos, username);
        return cobro;
    }

    /**
     * Reembolso automático: si el pago devuelto fue con tarjeta o PayPal, se pide a la pasarela.
     * Si la pasarela responde bien, la devolución queda COMPLETADA; si no, sigue PENDIENTE para el personal.
     */
    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void procesarReembolsoEnLinea(Long devolucionId) {
        Devolucion d = devoluciones.findById(devolucionId).orElseThrow(() -> new IllegalArgumentException("Devolución no encontrada."));
        if (d.estado() != EstadoDevolucion.PENDIENTE || !d.metodo().esEnLinea()) return;
        Pago pago = pagos.findById(d.pagoId()).orElseThrow(() -> new IllegalArgumentException("Pago no encontrado."));
        ResultadoReembolso r = d.metodo() == MetodoPago.PAYPAL
                ? payPal.reembolsar(pago.codigoOperacion(), d.monto())
                : tarjetas.reembolsar(pago.codigoOperacion(), d.monto());
        if (!r.exitoso()) {
            log.warn("Reembolso automático no realizado para la devolución {}: {}", d.id(), r.mensaje());
            return;
        }
        String referencia = r.referencia() == null ? "SIN-REF" : r.referencia();
        if (referencia.length() > 60) referencia = referencia.substring(0, 60);
        devoluciones.save(d.completada(LocalDateTime.now(reloj), EventoReserva.SISTEMA, referencia));
        bitacora.anotar(d.reservaId(), AccionReserva.DEVOLUCION_COMPLETADA, EventoReserva.SISTEMA,
                "Reembolso automático de S/ " + d.monto() + " en " + d.metodo().getEtiqueta() + " (op. " + referencia + ")");
    }

    private Usuario cliente(String username) {
        Usuario u = usuarios.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        if (u.rol() != Rol.CLIENTE || !u.activo()) throw new IllegalArgumentException("Solo un cliente activo puede pagar en línea.");
        return u;
    }

    /** El pago debe existir, ser del cliente, estar PENDIENTE y ser de tipo en línea. */
    private Pago pagoPendienteDelCliente(String username, Long pagoId) {
        Usuario u = cliente(username);
        Pago pago = pagos.findById(pagoId).orElseThrow(() -> new IllegalArgumentException("Pago no encontrado."));
        Reserva r = reservas.findById(pago.reservaId()).orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada."));
        if (!r.cliente().id().equals(u.id())) throw new IllegalArgumentException("Este pago no pertenece a su cuenta.");
        if (!pago.metodo().esEnLinea()) throw new IllegalArgumentException("Este pago no se realiza en línea.");
        if (pago.estado() != EstadoPago.PENDIENTE)
            throw new IllegalArgumentException(pago.estado() == EstadoPago.CONFIRMADO ? "Este pago ya fue realizado."
                    : "El tiempo para este pago terminó. Vuelva a intentarlo desde \"Mis reservas\".");
        return pago;
    }

    private static String descripcion(Reserva r, Pago p) {
        return (p.tipo() == TipoPago.ANTICIPO ? "Anticipo" : "Saldo") + " reserva " + r.id() + " - "
                + r.auto().marca() + " " + r.auto().modelo() + " (" + r.auto().placa() + ")";
    }
}
