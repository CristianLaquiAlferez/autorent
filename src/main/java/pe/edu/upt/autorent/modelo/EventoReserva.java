package pe.edu.upt.autorent.modelo;

import java.time.LocalDateTime;

/**
 * Una línea de la bitácora (historial) de una reserva: qué pasó, cuándo, quién lo hizo
 * y cómo cambió el estado. Responde a "¿qué proceso hubo detrás de esta reserva y de sus pagos?".
 *
 * Nunca se modifica ni se borra: solo se agregan líneas nuevas (es un registro de auditoría).
 *
 * @param estadoAnterior estado antes de la acción (null si la acción no cambia el estado)
 * @param estadoNuevo    estado después de la acción (null si la acción no cambia el estado)
 * @param usuario        quién la hizo: el usuario del cliente o trabajador, o SISTEMA para las tareas automáticas
 */
public record EventoReserva(Long id, Long reservaId, LocalDateTime fecha, AccionReserva accion,
                            EstadoReserva estadoAnterior, EstadoReserva estadoNuevo, String usuario, String detalle) {

    /** Usuario con el que se firman las acciones automáticas (vencimientos, no presentados, pasarela, SUNAT). */
    public static final String SISTEMA = "SISTEMA";

    /** true si esta acción cambió el estado de la reserva. */
    public boolean cambioEstado() {
        return estadoNuevo != null && estadoNuevo != estadoAnterior;
    }
}
