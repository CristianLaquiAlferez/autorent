package pe.edu.upt.autorent.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upt.autorent.modelo.Auto;
import pe.edu.upt.autorent.modelo.EstadoAuto;
import pe.edu.upt.autorent.repositorio.AutoRepositoryPort;
import pe.edu.upt.autorent.modelo.entidad.AutoEntity;
import pe.edu.upt.autorent.dao.springdata.AutoJpaRepository;
import java.util.List;
import java.util.Optional;
/** Adaptador DAO: traduce el puerto de vehículos a consultas Spring Data JPA. */

@Repository
@Transactional
public class JpaAutoAdapter implements AutoRepositoryPort {
    private final AutoJpaRepository repository;
    @PersistenceContext private EntityManager em;
    public JpaAutoAdapter(AutoJpaRepository repository) { this.repository = repository; }

    @Override public Auto save(Auto auto) {
        AutoEntity e = auto.id() == null ? new AutoEntity() : repository.findById(auto.id()).orElseGet(AutoEntity::new);
        e.setId(auto.id()); e.setPlaca(auto.placa()); e.setMarca(auto.marca()); e.setModelo(auto.modelo());
        e.setAnio(auto.anio()); e.setCategoria(auto.categoria()); e.setColor(auto.color());
        e.setTarifaDiaria(auto.tarifaDiaria()); e.setDescripcion(auto.descripcion()); e.setEstado(auto.estado());
        e.setImagenUrl(auto.imagenUrl());
        return JpaMapper.toDomain(repository.save(e));
    }
    @Override @Transactional(readOnly = true) public Optional<Auto> findById(Long id) { return repository.findById(id).map(JpaMapper::toDomain); }
    /**
     * Toma el candado (SELECT ... FOR UPDATE) leyendo SIEMPRE de la base de datos.
     * Antes se guarda lo pendiente (flush) y se vacía la memoria de JPA (clear): así nada de lo que se lea
     * después del candado sale de una copia vieja en memoria. Es seguro porque los servicios trabajan con
     * records inmutables, no con entidades JPA.
     */
    @Override public Optional<Auto> findByIdForUpdate(Long id) {
        em.flush(); em.clear();
        return repository.bloquearPorId(id).map(JpaMapper::toDomain);
    }
    @Override @Transactional(readOnly = true) public List<Auto> findAll() { return repository.findAllByOrderByMarcaAscModeloAsc().stream().map(JpaMapper::toDomain).toList(); }
    @Override @Transactional(readOnly = true) public List<Auto> findByEstado(EstadoAuto estado) { return repository.findByEstadoOrderByMarcaAscModeloAsc(estado).stream().map(JpaMapper::toDomain).toList(); }
    @Override @Transactional(readOnly = true) public boolean existsAny() { return repository.count() > 0; }
    @Override @Transactional(readOnly = true) public boolean existsByPlaca(String placa) { return repository.existsByPlacaIgnoreCase(placa); }
    @Override @Transactional(readOnly = true) public boolean existsByPlacaAndIdNot(String placa, Long id) { return repository.existsByPlacaIgnoreCaseAndIdNot(placa, id); }
}
