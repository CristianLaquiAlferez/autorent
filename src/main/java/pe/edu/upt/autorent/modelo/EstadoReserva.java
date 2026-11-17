package pe.edu.upt.autorent.modelo;

import java.util.List;
/**
 * Estados del proceso de reserva y alquiler.
 * Flujo normal: PENDIENTE_PAGO / PENDIENTE_VALIDACION -> CONFIRMADA -> EN_CURSO -> FINALIZADA.
 * Otros finales: CANCELADA (por el cliente o el personal), VENCIDA (no pagó el anticipo a tiempo)
 * y NO_PRESENTADO (tenía la reserva confirmada pero nunca recogió el vehículo).
 */

public enum EstadoReserva {
    PENDIENTE_PAGO, PENDIENTE_VALIDACION, CONFIRMADA, EN_CURSO, FINALIZADA, CANCELADA, VENCIDA, NO_PRESENTADO;

    /** Estados que ocupan el vehículo: con ellos no se acepta otra reserva en las mismas fechas. */
    public static final List<EstadoReserva> BLOQUEAN_DISPONIBILIDAD = List.of(
            PENDIENTE_PAGO, PENDIENTE_VALIDACION, CONFIRMADA, EN_CURSO);

    /** Reserva todavía abierta: aún puede pagarse, cancelarse o entregarse. */
    public boolean esActiva() {
        return this == PENDIENTE_PAGO || this == PENDIENTE_VALIDACION || this == CONFIRMADA;
    }

    /** Reserva cerrada: ya no admite más operaciones. */
    public boolean esFinal() {
        return this == FINALIZADA || this == CANCELADA || this == VENCIDA || this == NO_PRESENTADO;
    }
}
