package pe.edu.upt.autorent.dao;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upt.autorent.modelo.Devolucion;
import pe.edu.upt.autorent.repositorio.DevolucionRepositoryPort;
import pe.edu.upt.autorent.modelo.entidad.DevolucionEntity;
import pe.edu.upt.autorent.dao.springdata.DevolucionJpaRepository;
import pe.edu.upt.autorent.dao.springdata.PagoJpaRepository;
import pe.edu.upt.autorent.dao.springdata.ReservaJpaRepository;
import java.util.List;
import java.util.Optional;
/** Adaptador DAO: implementa el acceso a devoluciones mediante JPA. */

@Repository
@Transactional
public class JpaDevolucionAdapter implements DevolucionRepositoryPort {
    private final DevolucionJpaRepository repository;
    private final ReservaJpaRepository reservas;
    private final PagoJpaRepository pagos;
    public JpaDevolucionAdapter(DevolucionJpaRepository repository, ReservaJpaRepository reservas, PagoJpaRepository pagos) {
        this.repository = repository; this.reservas = reservas; this.pagos = pagos;
    }
    @Override public Devolucion save(Devolucion d) {
        DevolucionEntity e = d.id() == null ? new DevolucionEntity() : repository.findById(d.id()).orElseGet(DevolucionEntity::new);
        e.setId(d.id()); e.setReserva(reservas.getReferenceById(d.reservaId()));
        e.setPago(d.pagoId() == null ? null : pagos.getReferenceById(d.pagoId()));
        e.setMonto(d.monto()); e.setMetodo(d.metodo()); e.setTelefonoDestino(d.telefonoDestino()); e.setEstado(d.estado());
        e.setRegistradoEn(d.registradoEn()); e.setCompletadoEn(d.completadoEn()); e.setCompletadoPor(d.completadoPor());
        e.setCodigoOperacion(d.codigoOperacion());
        return JpaMapper.toDomain(repository.save(e));
    }
    @Override @Transactional(readOnly = true) public Optional<Devolucion> findById(Long id) { return repository.findById(id).map(JpaMapper::toDomain); }
    @Override @Transactional(readOnly = true) public List<Devolucion> findAll() { return repository.findAllByOrderByRegistradoEnDesc().stream().map(JpaMapper::toDomain).toList(); }
    @Override @Transactional(readOnly = true) public List<Devolucion> findByReservaId(Long id) { return repository.findByReserva_IdOrderByRegistradoEnDesc(id).stream().map(JpaMapper::toDomain).toList(); }
    @Override @Transactional(readOnly = true) public List<Devolucion> findByClienteId(Long id) { return repository.findByReserva_Cliente_IdOrderByRegistradoEnDesc(id).stream().map(JpaMapper::toDomain).toList(); }
}
