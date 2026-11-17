package pe.edu.upt.autorent.dao;

import java.time.LocalDateTime;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upt.autorent.modelo.Pago;
import pe.edu.upt.autorent.modelo.EstadoPago;
import java.math.BigDecimal;
import pe.edu.upt.autorent.repositorio.PagoRepositoryPort;
import pe.edu.upt.autorent.modelo.entidad.PagoEntity;
import pe.edu.upt.autorent.dao.springdata.PagoJpaRepository;
import pe.edu.upt.autorent.dao.springdata.ReservaJpaRepository;
import java.util.List;
import java.util.Optional;
/** Adaptador DAO: implementa la persistencia de pagos mediante JPA. */

@Repository
@Transactional
public class JpaPagoAdapter implements PagoRepositoryPort {
    private final PagoJpaRepository repository;
    private final ReservaJpaRepository reservas;
    public JpaPagoAdapter(PagoJpaRepository repository, ReservaJpaRepository reservas) { this.repository = repository; this.reservas = reservas; }
    @Override public Pago save(Pago pago) {
        PagoEntity e = pago.id() == null ? new PagoEntity() : repository.findById(pago.id()).orElseGet(PagoEntity::new);
        e.setId(pago.id()); e.setReserva(reservas.getReferenceById(pago.reservaId()));
        e.setMonto(pago.monto()); e.setMetodo(pago.metodo()); e.setTipo(pago.tipo()); e.setEstado(pago.estado());
        e.setTelefonoOrigen(pago.telefonoOrigen()); e.setCodigoOperacion(pago.codigoOperacion());
        e.setRegistradoEn(pago.registradoEn()); e.setProcesadoEn(pago.procesadoEn()); e.setRegistradoPor(pago.registradoPor());
        e.setProcesadoPor(pago.procesadoPor()); e.setObservacion(recortar(pago.observacion(), 200));
        e.setDetalle(pago.detalle());
        return JpaMapper.toDomain(repository.save(e));
    }
    @Override @Transactional(readOnly = true) public Optional<Pago> findById(Long id) { return repository.findById(id).map(JpaMapper::toDomain); }
    @Override @Transactional(readOnly = true) public List<Pago> findByReservaId(Long id) { return repository.findByReserva_IdOrderByRegistradoEnDesc(id).stream().map(JpaMapper::toDomain).toList(); }
    @Override @Transactional(readOnly = true) public List<Pago> findByClienteId(Long id) { return repository.findByReserva_Cliente_IdOrderByRegistradoEnDesc(id).stream().map(JpaMapper::toDomain).toList(); }
    @Override @Transactional(readOnly = true) public BigDecimal totalConfirmado(Long id) {
        BigDecimal total = repository.sumarPorReservaYEstado(id, EstadoPago.CONFIRMADO);
        return total == null ? BigDecimal.ZERO : total;
    }
    @Override @Transactional(readOnly = true) public boolean existePendiente(Long id) { return repository.existsByReserva_IdAndEstado(id, EstadoPago.PENDIENTE); }
    @Override @Transactional(readOnly = true) public List<Pago> findAll() { return repository.findAllByOrderByRegistradoEnDesc().stream().map(JpaMapper::toDomain).toList(); }
    @Override @Transactional(readOnly = true) public List<Pago> findPendientesRegistradosAntesDe(LocalDateTime limite) {
        return repository.findByEstadoAndRegistradoEnBefore(EstadoPago.PENDIENTE, limite).stream().map(JpaMapper::toDomain).toList();
    }
    @Override @Transactional(readOnly = true) public boolean existsCodigoOperacion(String codigo) { return codigo != null && !codigo.isBlank() && repository.existsByCodigoOperacionIgnoreCase(codigo); }

    private static String recortar(String texto, int max) {
        return texto == null || texto.length() <= max ? texto : texto.substring(0, max);
    }
}
