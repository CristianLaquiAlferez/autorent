package pe.edu.upt.autorent.dao.springdata;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.upt.autorent.modelo.entidad.HistorialReservaEntity;
import java.util.List;

/** Repositorio Spring Data JPA de la bitácora de reservas. */
public interface HistorialReservaJpaRepository extends JpaRepository<HistorialReservaEntity, Long> {
    List<HistorialReservaEntity> findByReserva_IdOrderByFechaAscIdAsc(Long reservaId);
}
