package pe.edu.upt.autorent.dao;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upt.autorent.modelo.Mantenimiento;
import pe.edu.upt.autorent.repositorio.MantenimientoRepositoryPort;
import pe.edu.upt.autorent.modelo.entidad.MantenimientoEntity;
import pe.edu.upt.autorent.dao.springdata.AutoJpaRepository;
import pe.edu.upt.autorent.dao.springdata.MantenimientoJpaRepository;
import java.util.List;
import java.util.Optional;
/** Adaptador DAO: implementa el acceso a mantenimientos mediante JPA. */

@Repository
@Transactional
public class JpaMantenimientoAdapter implements MantenimientoRepositoryPort {
    private final MantenimientoJpaRepository repository;
    private final AutoJpaRepository autos;
    public JpaMantenimientoAdapter(MantenimientoJpaRepository repository, AutoJpaRepository autos) { this.repository = repository; this.autos = autos; }
    @Override public Mantenimiento save(Mantenimiento m) {
        MantenimientoEntity e = m.id() == null ? new MantenimientoEntity() : repository.findById(m.id()).orElseGet(MantenimientoEntity::new);
        e.setId(m.id()); e.setAuto(autos.findById(m.autoId()).orElseThrow(() -> new IllegalArgumentException("Auto no encontrado.")));
        e.setDescripcion(m.descripcion()); e.setFechaInicio(m.fechaInicio()); e.setFechaFin(m.fechaFin());
        e.setEstado(m.estado()); e.setRegistradoEn(m.registradoEn());
        return JpaMapper.toDomain(repository.save(e));
    }
    @Override @Transactional(readOnly = true) public Optional<Mantenimiento> findById(Long id) { return repository.findById(id).map(JpaMapper::toDomain); }
    @Override @Transactional(readOnly = true) public List<Mantenimiento> findAll() { return repository.findAllByOrderByRegistradoEnDesc().stream().map(JpaMapper::toDomain).toList(); }
    @Override @Transactional(readOnly = true) public List<Mantenimiento> findByAutoId(Long id) { return repository.findByAuto_IdOrderByRegistradoEnDesc(id).stream().map(JpaMapper::toDomain).toList(); }
}
