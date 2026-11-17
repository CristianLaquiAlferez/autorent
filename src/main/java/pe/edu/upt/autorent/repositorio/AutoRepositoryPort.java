package pe.edu.upt.autorent.repositorio;

import pe.edu.upt.autorent.modelo.Auto;
import pe.edu.upt.autorent.modelo.EstadoAuto;
import java.util.List;
import java.util.Optional;
/** Puerto de salida: contrato de persistencia de vehículos, sin depender de JPA. */

public interface AutoRepositoryPort {
    /** Guarda un registro nuevo o actualizado. */
    Auto save(Auto auto);
    /** Busca un registro por su identificador. */
    Optional<Auto> findById(Long id);
    /** Busca y bloquea el registro durante una transaccion. */
    Optional<Auto> findByIdForUpdate(Long id);
    /** Lista todos los registros del modulo. */
    List<Auto> findAll();
    /** Filtra los registros según su estado. */
    List<Auto> findByEstado(EstadoAuto estado);
    /** Indica si ya existen registros. */
    boolean existsAny();
    /** Comprueba si la placa ya esta registrada. */
    boolean existsByPlaca(String placa);
    /** Comprueba placas duplicadas al editar un vehículo. */
    boolean existsByPlacaAndIdNot(String placa, Long id);
}
