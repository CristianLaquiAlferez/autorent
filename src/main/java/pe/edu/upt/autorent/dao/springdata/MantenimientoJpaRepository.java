package pe.edu.upt.autorent.dao.springdata;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.upt.autorent.modelo.entidad.MantenimientoEntity;
import java.util.List;
/** Repositorio Spring Data JPA: consultas concretas de la tabla de mantenimiento. */

public interface MantenimientoJpaRepository extends JpaRepository<MantenimientoEntity, Long> {
    List<MantenimientoEntity> findAllByOrderByRegistradoEnDesc();
    List<MantenimientoEntity> findByAuto_IdOrderByRegistradoEnDesc(Long autoId);
}
