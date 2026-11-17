package pe.edu.upt.autorent.servicio;

import org.springframework.stereotype.Component;
import pe.edu.upt.autorent.modelo.AccionReserva;
import pe.edu.upt.autorent.modelo.EstadoReserva;
import pe.edu.upt.autorent.modelo.EventoReserva;
import pe.edu.upt.autorent.repositorio.HistorialReservaRepositoryPort;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Anota en la bitácora de la reserva cada paso del proceso: creación, pagos, confirmaciones,
 * rechazos, boletas, entrega, recepción, cargos, cancelaciones y devoluciones de dinero.
 *
 * Se llama dentro de la misma transacción que la acción: si la acción falla y se deshace,
 * su línea de bitácora también se deshace (nunca queda anotado algo que no pasó).
 */
@Component
public class BitacoraReservas {
    private final HistorialReservaRepositoryPort historial;
    private final Clock reloj;

    public BitacoraReservas(HistorialReservaRepositoryPort historial, Clock reloj) {
        this.historial = historial; this.reloj = reloj;
    }

    /** Anota una acción que cambia el estado de la reserva. */
    public void anotar(Long reservaId, AccionReserva accion, EstadoReserva antes, EstadoReserva despues,
                       String usuario, String detalle) {
        historial.save(new EventoReserva(null, reservaId, LocalDateTime.now(reloj), accion, antes, despues,
                usuario == null || usuario.isBlank() ? EventoReserva.SISTEMA : usuario, detalle));
    }

    /** Anota una acción que no cambia el estado (por ejemplo, un pago registrado o una boleta emitida). */
    public void anotar(Long reservaId, AccionReserva accion, String usuario, String detalle) {
        anotar(reservaId, accion, null, null, usuario, detalle);
    }

    /** Bitácora completa de la reserva, de la acción más antigua a la más reciente. */
    public List<EventoReserva> de(Long reservaId) { return historial.findByReservaId(reservaId); }
}
