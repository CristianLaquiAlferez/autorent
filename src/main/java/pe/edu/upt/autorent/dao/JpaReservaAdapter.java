package pe.edu.upt.autorent.dao;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upt.autorent.modelo.EstadoReserva;
import pe.edu.upt.autorent.modelo.Reserva;
import pe.edu.upt.autorent.repositorio.ReservaRepositoryPort;
import pe.edu.upt.autorent.modelo.entidad.ReservaEntity;
import pe.edu.upt.autorent.dao.springdata.AutoJpaRepository;
import pe.edu.upt.autorent.dao.springdata.ReservaJpaRepository;
import pe.edu.upt.autorent.dao.springdata.UsuarioJpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
/**
 * Adaptador DAO (lado de salida del hexágono): implementa ReservaRepositoryPort usando JPA.
 * Convierte el objeto del dominio (Reserva) en entidad JPA (ReservaEntity) para guardarlo en MySQL,
 * y al leer hace lo contrario con JpaMapper. Así el servicio nunca ve una entidad JPA.
 */

@Repository
@Transactional
public class JpaReservaAdapter implements ReservaRepositoryPort {
    private final ReservaJpaRepository repository;
    private final AutoJpaRepository autos;
    private final UsuarioJpaRepository usuarios;
    public JpaReservaAdapter(ReservaJpaRepository repository, AutoJpaRepository autos, UsuarioJpaRepository usuarios) {
        this.repository = repository; this.autos = autos; this.usuarios = usuarios;
    }
    /** Inserta (id null) o actualiza (id existente) la reserva y devuelve la versión guardada. */
    @Override public Reserva save(Reserva reserva) {
        ReservaEntity e = reserva.id() == null ? new ReservaEntity() : repository.findById(reserva.id()).orElseGet(ReservaEntity::new);
        e.setId(reserva.id());
        e.setAuto(autos.findById(reserva.auto().id()).orElseThrow(() -> new IllegalArgumentException("Auto no encontrado.")));
        e.setCliente(usuarios.findById(reserva.cliente().id()).orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado.")));
        e.setFechaInicio(reserva.fechaInicio()); e.setFechaFin(reserva.fechaFin()); e.setDias(reserva.dias());
        e.setTarifaDiaria(reserva.tarifaDiaria()); e.setTotal(reserva.total()); e.setEstado(reserva.estado());
        e.setTelefonoDevolucion(reserva.telefonoDevolucion()); e.setObservacion(reserva.observacion());
        return JpaMapper.toDomain(repository.save(e));
    }
    @Override @Transactional(readOnly = true) public Optional<Reserva> findById(Long id) { return repository.findById(id).map(JpaMapper::toDomain); }
    /** Lectura con bloqueo pesimista para modificar la reserva sin conflictos. */
    @Override public Optional<Reserva> findByIdForUpdate(Long id) { return repository.bloquearPorId(id).map(JpaMapper::toDomain); }
    @Override @Transactional(readOnly = true) public List<Reserva> findAll() { return repository.findAllByOrderByCreadoEnDesc().stream().map(JpaMapper::toDomain).toList(); }
    @Override @Transactional(readOnly = true) public List<Reserva> findByClienteId(Long id) { return repository.findByCliente_IdOrderByCreadoEnDesc(id).stream().map(JpaMapper::toDomain).toList(); }
    @Override @Transactional(readOnly = true) public List<Reserva> findByAutoId(Long id) { return repository.findByAuto_IdOrderByCreadoEnDesc(id).stream().map(JpaMapper::toDomain).toList(); }
    @Override @Transactional(readOnly = true) public boolean existsCruce(Long autoId, LocalDate inicio, LocalDate fin, List<EstadoReserva> estados) {
        return repository.existeCruce(autoId, inicio, fin, estados);
    }
    @Override @Transactional(readOnly = true) public boolean existsReservaActiva(Long autoId, List<EstadoReserva> estados) {
        return repository.existsByAuto_IdAndEstadoIn(autoId, estados);
    }
    @Override @Transactional(readOnly = true) public List<Reserva> findByEstadoIn(List<EstadoReserva> estados) {
        return repository.findByEstadoIn(estados).stream().map(JpaMapper::toDomain).toList();
    }
    @Override @Transactional(readOnly = true) public boolean existsOtroAlquilerEnCurso(Long autoId, Long reservaId) {
        return repository.existsByAuto_IdAndEstadoAndIdNot(autoId, EstadoReserva.EN_CURSO, reservaId);
    }
}
