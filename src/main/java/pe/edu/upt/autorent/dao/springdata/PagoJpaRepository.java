package pe.edu.upt.autorent.dao.springdata;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.upt.autorent.modelo.EstadoPago;
import java.math.BigDecimal;
import pe.edu.upt.autorent.modelo.entidad.PagoEntity;
import java.util.List;
/** Repositorio Spring Data JPA: consultas concretas de la tabla de pago. */

public interface PagoJpaRepository extends JpaRepository<PagoEntity, Long> {
    List<PagoEntity> findByReserva_IdOrderByRegistradoEnDesc(Long reservaId);
    List<PagoEntity> findByReserva_Cliente_IdOrderByRegistradoEnDesc(Long clienteId);
    boolean existsByReserva_IdAndEstado(Long reservaId, EstadoPago estado);
    @Query("select sum(p.monto) from PagoEntity p where p.reserva.id = :reservaId and p.estado = :estado")
    BigDecimal sumarPorReservaYEstado(@Param("reservaId") Long reservaId, @Param("estado") EstadoPago estado);
    List<PagoEntity> findAllByOrderByRegistradoEnDesc();
    boolean existsByCodigoOperacionIgnoreCase(String codigoOperacion);
    List<PagoEntity> findByEstadoAndRegistradoEnBefore(EstadoPago estado, LocalDateTime limite);
}
