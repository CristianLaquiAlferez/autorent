package pe.edu.upt.autorent.repositorio;

import pe.edu.upt.autorent.modelo.EventoReserva;
import java.util.List;

/** Puerto de salida: guarda y consulta la bitácora (historial) de cada reserva. */
public interface HistorialReservaRepositoryPort {
    /** Agrega una línea a la bitácora. Las líneas nunca se modifican. */
    EventoReserva save(EventoReserva evento);
    /** Bitácora de una reserva, en orden cronológico (la más antigua primero). */
    List<EventoReserva> findByReservaId(Long reservaId);
}
