package pe.edu.upt.autorent.servicio.contrato;

import pe.edu.upt.autorent.modelo.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Puerto de entrada de consultas de trazabilidad: el expediente completo de una reserva
 * y el historial de pagos (cuadre de caja) para el personal.
 */
public interface ConsultaReservaUseCase {

    /**
     * Expediente de una reserva: todo lo que pasó con ella y con su dinero.
     * El cliente solo puede ver sus propias reservas; el personal, cualquiera.
     */
    DetalleReserva detalle(Long reservaId, String username);

    /** Historial de pagos con filtros (cualquiera puede ser null) y los totales para cuadrar caja. */
    ReporteCaja caja(LocalDate desde, LocalDate hasta, MetodoPago metodo, EstadoPago estado);

    /**
     * @param pagado    suma de pagos confirmados
     * @param devuelto  suma de devoluciones (pendientes y completadas)
     * @param saldo     lo que falta pagar (total - pagado); si es negativo no se muestra
     */
    record DetalleReserva(Reserva reserva, List<Pago> pagos, List<Comprobante> comprobantes,
                          List<Devolucion> devoluciones, List<EventoReserva> historial,
                          BigDecimal pagado, BigDecimal devuelto, BigDecimal saldo) {
        public boolean tieneDeuda() { return saldo.signum() > 0 && reserva.estado() == EstadoReserva.FINALIZADA; }
    }

    /**
     * @param totalPorMetodo       dinero CONFIRMADO por método (lo que debe haber en caja, Yape, pasarela...)
     * @param devolucionesPeriodo  devoluciones registradas en el mismo periodo
     */
    record ReporteCaja(List<Pago> pagos, Map<Long, Reserva> reservas, Map<MetodoPago, BigDecimal> totalPorMetodo,
                       BigDecimal totalConfirmado, BigDecimal totalPendiente, BigDecimal totalRechazado,
                       List<Devolucion> devolucionesPeriodo, BigDecimal totalDevuelto, BigDecimal neto) { }
}
