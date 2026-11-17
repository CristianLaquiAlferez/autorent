package pe.edu.upt.autorent.repositorio;

import pe.edu.upt.autorent.modelo.Mantenimiento;
import java.util.List;
import java.util.Optional;
/** Puerto de salida: contrato de mantenimiento. */

public interface MantenimientoRepositoryPort {
    /** Guarda un registro nuevo o actualizado. */
    Mantenimiento save(Mantenimiento mantenimiento);
    /** Busca un registro por su identificador. */
    Optional<Mantenimiento> findById(Long id);
    /** Lista todos los registros del modulo. */
    List<Mantenimiento> findAll();
    /** Contrato de acceso a datos para find by auto id. */
    List<Mantenimiento> findByAutoId(Long autoId);
}
