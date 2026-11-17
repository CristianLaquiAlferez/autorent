package pe.edu.upt.autorent.repositorio;

import pe.edu.upt.autorent.modelo.Devolucion;
import java.util.List;
import java.util.Optional;
/** Puerto de salida: contrato para registrar reembolsos. */

public interface DevolucionRepositoryPort {
    /** Guarda un registro nuevo o actualizado. */
    Devolucion save(Devolucion devolucion);
    /** Busca un registro por su identificador. */
    Optional<Devolucion> findById(Long id);
    /** Lista todos los registros del modulo. */
    List<Devolucion> findAll();
    /** Contrato de acceso a datos para find by reserva id. */
    List<Devolucion> findByReservaId(Long reservaId);
    /** Contrato de acceso a datos para find by cliente id. */
    List<Devolucion> findByClienteId(Long clienteId);
}
