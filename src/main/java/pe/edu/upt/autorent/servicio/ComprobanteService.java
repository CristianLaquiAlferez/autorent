package pe.edu.upt.autorent.servicio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upt.autorent.modelo.*;
import pe.edu.upt.autorent.repositorio.*;
import pe.edu.upt.autorent.servicio.contrato.ComprobanteUseCase;
import pe.edu.upt.autorent.servicio.pdf.BoletaPdfGenerator;

/**
 * Servicio de comprobantes electrónicos.
 *
 * Cada pago confirmado recibe su boleta (anticipo, saldo o cargo por retraso son cobros distintos).
 * Si el dinero se devuelve, se emite una nota de crédito que anula esa boleta.
 * El envío lo hace el puerto FacturacionElectronicaPort (Nubefact o el modo de prueba).
 *
 * Primero se guarda el comprobante con su número (PENDIENTE) y luego se envía. Si el envío falla queda en
 * ERROR con el mismo número y se puede reintentar: así nunca hay saltos en el correlativo.
 * Las emisiones corren en una transacción nueva (el pago ya se guardó antes, ver EventosDeNegocioListener) y
 * dentro de un bloque synchronized que incluye el COMMIT: así dos pagos simultáneos nunca reciben el mismo número.
 */
@Service
public class ComprobanteService implements ComprobanteUseCase {
    private static final Logger log = LoggerFactory.getLogger(ComprobanteService.class);
    private static final BigDecimal FACTOR_IGV = new BigDecimal("1.18");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ComprobanteRepositoryPort comprobantes;
    private final PagoRepositoryPort pagos;
    private final ReservaRepositoryPort reservas;
    private final DevolucionRepositoryPort devoluciones;
    private final UsuarioRepositoryPort usuarios;
    private final FacturacionElectronicaPort facturacion;
    private final BoletaPdfGenerator pdf;
    private final BitacoraReservas bitacora;
    private final Clock reloj;
    private final TransactionTemplate transaccionNueva;

    public ComprobanteService(ComprobanteRepositoryPort comprobantes, PagoRepositoryPort pagos, ReservaRepositoryPort reservas,
                              DevolucionRepositoryPort devoluciones, UsuarioRepositoryPort usuarios,
                              FacturacionElectronicaPort facturacion, BoletaPdfGenerator pdf,
                              BitacoraReservas bitacora, Clock reloj,
                              PlatformTransactionManager transacciones) {
        this.comprobantes = comprobantes; this.pagos = pagos; this.reservas = reservas; this.devoluciones = devoluciones;
        this.usuarios = usuarios; this.facturacion = facturacion; this.pdf = pdf; this.bitacora = bitacora; this.reloj = reloj;
        this.transaccionNueva = new TransactionTemplate(transacciones);
        this.transaccionNueva.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transaccionNueva.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /** IGV incluido en el precio: base = total / 1.18 y el IGV es la diferencia (así los centavos siempre cuadran). */
    static BigDecimal[] separarIgv(BigDecimal total) {
        BigDecimal gravada = total.divide(FACTOR_IGV, 2, RoundingMode.HALF_UP);
        return new BigDecimal[]{gravada, total.subtract(gravada)};
    }

    /** Ejecuta en una transacción nueva y confirma (commit) antes de soltar el candado. */
    private synchronized <T> T enTransaccionExclusiva(Supplier<T> trabajo) {
        return transaccionNueva.execute(estado -> trabajo.get());
    }

    @Override public Comprobante emitirBoletaDePago(Long pagoId) {
        return enTransaccionExclusiva(() -> boletaDePago(pagoId));
    }

    @Override public Comprobante emitirNotaCreditoDeDevolucion(Long devolucionId) {
        return enTransaccionExclusiva(() -> notaCreditoDeDevolucion(devolucionId));
    }

    @Override public Comprobante reintentar(Long comprobanteId) {
        return enTransaccionExclusiva(() -> reenviar(comprobanteId));
    }

    private Comprobante boletaDePago(Long pagoId) {
        Optional<Comprobante> existente = comprobantes.findByPagoIdAndTipo(pagoId, TipoComprobante.BOLETA);
        if (existente.isPresent()) return existente.get();
        Pago pago = pagos.findById(pagoId).orElseThrow(() -> new IllegalArgumentException("Pago no encontrado."));
        if (pago.estado() != EstadoPago.CONFIRMADO) throw new IllegalArgumentException("Solo se emite boleta de un pago confirmado.");
        Reserva r = reservas.findById(pago.reservaId()).orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada."));
        String concepto = pago.tipo() == TipoPago.ANTICIPO ? "Anticipo" : "Saldo";
        String descripcion = "Alquiler de vehículo " + r.auto().marca() + " " + r.auto().modelo() + " (" + r.auto().placa()
                + "), del " + r.fechaInicio().format(FECHA) + " al " + r.fechaFin().format(FECHA) + " (" + r.dias()
                + " días). " + concepto + " de la reserva N.° " + r.id() + ". Pago: " + pago.metodo().getEtiqueta() + ".";
        // Si el saldo incluye cargos por retraso o daños, la boleta lo dice (el cliente sabe qué está pagando).
        if (pago.tipo() == TipoPago.SALDO && r.observacion() != null && !r.observacion().isBlank())
            descripcion += " Incluye: " + r.observacion();
        Comprobante nuevo = armar(TipoComprobante.BOLETA, pago, r, descripcion, null);
        return enviar(comprobantes.save(nuevo), null);
    }

    private Comprobante notaCreditoDeDevolucion(Long devolucionId) {
        Devolucion d = devoluciones.findById(devolucionId).orElseThrow(() -> new IllegalArgumentException("Devolución no encontrada."));
        Optional<Comprobante> existente = comprobantes.findByPagoIdAndTipo(d.pagoId(), TipoComprobante.NOTA_CREDITO);
        if (existente.isPresent()) return existente.get();
        Comprobante boleta = comprobantes.findByPagoIdAndTipo(d.pagoId(), TipoComprobante.BOLETA)
                .orElseThrow(() -> new IllegalArgumentException("El pago devuelto no tiene boleta que anular."));
        if (!boleta.emitido())
            throw new IllegalArgumentException("La boleta " + boleta.serieNumero() + " no llegó a emitirse; no requiere nota de crédito.");
        Pago pago = pagos.findById(d.pagoId()).orElseThrow(() -> new IllegalArgumentException("Pago no encontrado."));
        Reserva r = reservas.findById(d.reservaId()).orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada."));
        String descripcion = "Anulación de la boleta " + boleta.serieNumero() + ": devolución del pago de la reserva N.° "
                + r.id() + " (" + r.estado().name().replace('_', ' ').toLowerCase() + ").";
        Comprobante nota = armar(TipoComprobante.NOTA_CREDITO, pago, r, descripcion, boleta.id());
        return enviar(comprobantes.save(nota), boleta);
    }

    private Comprobante reenviar(Long comprobanteId) {
        Comprobante c = comprobantes.findById(comprobanteId).orElseThrow(() -> new IllegalArgumentException("Comprobante no encontrado."));
        if (c.emitido()) throw new IllegalArgumentException("El comprobante " + c.serieNumero() + " ya fue emitido.");
        Comprobante modificado = c.referenciaId() == null ? null : comprobantes.findById(c.referenciaId()).orElse(null);
        return enviar(c, modificado);
    }

    /** Arma el comprobante con el siguiente número de la serie y los montos con IGV separado. */
    private Comprobante armar(TipoComprobante tipo, Pago pago, Reserva r, String descripcion, Long referenciaId) {
        Usuario cliente = usuarios.findById(r.cliente().id()).orElse(r.cliente());
        String serie = facturacion.serie(tipo);
        int numero = comprobantes.ultimoNumero(tipo, serie) + 1;
        BigDecimal[] montos = separarIgv(pago.monto());
        return new Comprobante(null, tipo, serie, numero, LocalDate.now(reloj), pago.id(), r.id(), cliente.id(),
                cliente.documento(), cliente.nombreCompleto().trim().toUpperCase(), cliente.direccion(),
                descripcion.length() > 250 ? descripcion.substring(0, 250) : descripcion,
                montos[0], montos[1], pago.monto(), EstadoComprobante.PENDIENTE, facturacion.proveedor(),
                null, null, null, null, referenciaId, LocalDateTime.now(reloj));
    }

    /** Envía al sistema de facturación y guarda la respuesta. Nunca lanza error por fallas de conexión. */
    private Comprobante enviar(Comprobante c, Comprobante modificado) {
        ResultadoEmision r;
        try {
            r = facturacion.emitir(c, modificado);
        } catch (RuntimeException ex) {
            log.warn("Falló el envío de {}: {}", c.serieNumero(), ex.getMessage());
            r = new ResultadoEmision(EstadoComprobante.ERROR, null, null, null, "Error al enviar: " + ex.getMessage());
        }
        Comprobante guardado = comprobantes.save(c.conResultado(r.estado(), facturacion.proveedor(), r.enlacePdf(), r.hash(), r.cadenaQr(), r.mensaje()));
        String nombre = guardado.tipo().getNombre() + " " + guardado.serieNumero() + " por S/ " + guardado.total();
        if (guardado.emitido()) {
            bitacora.anotar(guardado.reservaId(), AccionReserva.COMPROBANTE_EMITIDO, EventoReserva.SISTEMA,
                    nombre + " (" + guardado.proveedor() + ", " + guardado.estado().name().toLowerCase() + ")");
        } else {
            bitacora.anotar(guardado.reservaId(), AccionReserva.COMPROBANTE_CON_ERROR, EventoReserva.SISTEMA,
                    nombre + ": " + (guardado.mensaje() == null ? "sin respuesta" : guardado.mensaje()) + ". Se puede reintentar.");
        }
        return guardado;
    }

    @Override @Transactional(readOnly = true) public List<Comprobante> listarTodos() { return comprobantes.findAll(); }

    @Override @Transactional(readOnly = true) public List<Comprobante> listarDeCliente(String username) {
        Usuario u = usuarios.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        return u.rol() == Rol.CLIENTE ? comprobantes.findByClienteId(u.id()) : comprobantes.findAll();
    }

    @Override @Transactional(readOnly = true) public ArchivoComprobante obtenerPdf(Long comprobanteId, String username) {
        Usuario u = usuarios.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        Comprobante c = comprobantes.findById(comprobanteId).orElseThrow(() -> new IllegalArgumentException("Comprobante no encontrado."));
        if (u.rol() == Rol.CLIENTE && !c.clienteId().equals(u.id()))
            throw new IllegalArgumentException("Ese comprobante no pertenece a su cuenta.");
        if (!c.emitido()) throw new IllegalArgumentException("El comprobante " + c.serieNumero() + " todavía no fue emitido.");
        if (c.enlacePdf() != null && !c.enlacePdf().isBlank()) return new ArchivoComprobante(null, null, c.enlacePdf());
        Comprobante modificado = c.referenciaId() == null ? null : comprobantes.findById(c.referenciaId()).orElse(null);
        boolean esPrueba = "PRUEBA".equals(c.proveedor());
        return new ArchivoComprobante(c.serieNumero() + ".pdf", pdf.generar(c, modificado, esPrueba), null);
    }
}
