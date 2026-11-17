package pe.edu.upt.autorent.dao.springdata;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.upt.autorent.modelo.TipoComprobante;
import pe.edu.upt.autorent.modelo.entidad.ComprobanteEntity;

/** Repositorio Spring Data JPA de la tabla comprobantes. */
public interface ComprobanteJpaRepository extends JpaRepository<ComprobanteEntity, Long> {
    Optional<ComprobanteEntity> findFirstByPagoIdAndTipo(Long pagoId, TipoComprobante tipo);
    List<ComprobanteEntity> findAllByOrderByIdDesc();
    List<ComprobanteEntity> findByClienteIdOrderByIdDesc(Long clienteId);
    List<ComprobanteEntity> findByReservaIdOrderByIdAsc(Long reservaId);
    /** Último correlativo de la serie; COALESCE devuelve 0 si la serie está vacía. */
    @Query("select coalesce(max(c.numero), 0) from ComprobanteEntity c where c.tipo = :tipo and c.serie = :serie")
    int ultimoNumero(@Param("tipo") TipoComprobante tipo, @Param("serie") String serie);
}
