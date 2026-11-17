package pe.edu.upt.autorent.dao.springdata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import pe.edu.upt.autorent.modelo.EstadoAuto;
import pe.edu.upt.autorent.modelo.entidad.AutoEntity;
import java.util.List;
/** Repositorio Spring Data JPA: consultas concretas de la tabla de auto. */

public interface AutoJpaRepository extends JpaRepository<AutoEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AutoEntity a where a.id = :id")
    Optional<AutoEntity> bloquearPorId(@Param("id") Long id);
    List<AutoEntity> findAllByOrderByMarcaAscModeloAsc();
    List<AutoEntity> findByEstadoOrderByMarcaAscModeloAsc(EstadoAuto estado);
    boolean existsByPlacaIgnoreCase(String placa);
    boolean existsByPlacaIgnoreCaseAndIdNot(String placa, Long id);
}
