package pe.edu.upt.autorent.dao.springdata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.repository.query.Param;
import pe.edu.upt.autorent.modelo.EstadoReserva;
import pe.edu.upt.autorent.modelo.entidad.ReservaEntity;
import java.time.LocalDate;
import java.util.List;
/** Repositorio Spring Data JPA de la tabla reservas. Spring genera el SQL a partir del nombre de cada método. */

public interface ReservaJpaRepository extends JpaRepository<ReservaEntity, Long> {
    /** SELECT ... FOR UPDATE: bloquea la fila hasta que termine la transacción. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReservaEntity r where r.id = :id")
    Optional<ReservaEntity> bloquearPorId(@Param("id") Long id);

    // @EntityGraph trae el auto y el cliente en la misma consulta (evita muchas consultas pequeñas).
    @EntityGraph(attributePaths = {"auto", "cliente"})
    List<ReservaEntity> findAllByOrderByCreadoEnDesc();
    @EntityGraph(attributePaths = {"auto", "cliente"})
    List<ReservaEntity> findByCliente_IdOrderByCreadoEnDesc(Long clienteId);
    @EntityGraph(attributePaths = {"auto", "cliente"})
    List<ReservaEntity> findByAuto_IdOrderByCreadoEnDesc(Long autoId);

    @EntityGraph(attributePaths = {"auto", "cliente"})
    List<ReservaEntity> findByEstadoIn(List<EstadoReserva> estados);

    boolean existsByAuto_IdAndEstadoIn(Long autoId, List<EstadoReserva> estados);
    boolean existsByAuto_IdAndEstadoAndIdNot(Long autoId, EstadoReserva estado, Long reservaId);

    /**
     * ¿Hay cruce de fechas? Dos periodos [inicio, fin) se cruzan si uno empieza antes de que el otro
     * termine y termina después de que el otro empieza. Solo cuentan las reservas en estados activos.
     */
    @Query("select case when count(r) > 0 then true else false end from ReservaEntity r " +
           "where r.auto.id = :autoId and r.estado in :estados " +
           "and r.fechaInicio < :fin and r.fechaFin > :inicio")
    boolean existeCruce(@Param("autoId") Long autoId,
                        @Param("inicio") LocalDate inicio,
                        @Param("fin") LocalDate fin,
                        @Param("estados") List<EstadoReserva> estados);
}
