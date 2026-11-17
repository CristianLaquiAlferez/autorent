package pe.edu.upt.autorent.repositorio;

import pe.edu.upt.autorent.modelo.EstadoReserva;
import pe.edu.upt.autorent.modelo.Reserva;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
/** Puerto de salida: contrato para acceder a reservas. */

public interface ReservaRepositoryPort {
    /** Guarda un registro nuevo o actualizado. */
    Reserva save(Reserva reserva);
    /** Busca un registro por su identificador. */
    Optional<Reserva> findById(Long id);
    /** Busca y bloquea el registro durante una transaccion. */
    Optional<Reserva> findByIdForUpdate(Long id);
    /** Lista todos los registros del modulo. */
    List<Reserva> findAll();
    /** Contrato de acceso a datos para find by cliente id. */
    List<Reserva> findByClienteId(Long clienteId);
    /** Contrato de acceso a datos para find by auto id. */
    List<Reserva> findByAutoId(Long autoId);
    /** Contrato de acceso a datos para exists cruce. */
    boolean existsCruce(Long autoId, LocalDate inicio, LocalDate fin, List<EstadoReserva> estados);
    /** Comprueba reservas que impiden utilizar el vehículo. */
    boolean existsReservaActiva(Long autoId, List<EstadoReserva> estados);
    /** Reservas que están en alguno de los estados indicados (para las tareas automáticas). */
    List<Reserva> findByEstadoIn(List<EstadoReserva> estados);
    /** Indica si el auto ya está entregado en otro alquiler. */
    boolean existsOtroAlquilerEnCurso(Long autoId, Long reservaId);
}
