package pe.edu.upt.autorent.dao.springdata;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.upt.autorent.modelo.entidad.DevolucionEntity;
import java.util.List;
/** Repositorio Spring Data JPA: consultas concretas de la tabla de devolución. */

public interface DevolucionJpaRepository extends JpaRepository<DevolucionEntity, Long> {
    List<DevolucionEntity> findAllByOrderByRegistradoEnDesc();
    List<DevolucionEntity> findByReserva_IdOrderByRegistradoEnDesc(Long reservaId);
    List<DevolucionEntity> findByReserva_Cliente_IdOrderByRegistradoEnDesc(Long clienteId);
}
