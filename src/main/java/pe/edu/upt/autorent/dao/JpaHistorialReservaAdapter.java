package pe.edu.upt.autorent.dao;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upt.autorent.dao.springdata.HistorialReservaJpaRepository;
import pe.edu.upt.autorent.dao.springdata.ReservaJpaRepository;
import pe.edu.upt.autorent.modelo.EventoReserva;
import pe.edu.upt.autorent.modelo.entidad.HistorialReservaEntity;
import pe.edu.upt.autorent.repositorio.HistorialReservaRepositoryPort;
import java.util.List;

/** Adaptador DAO: implementa la bitácora de reservas con JPA. Solo inserta, nunca actualiza. */
@Repository
@Transactional
public class JpaHistorialReservaAdapter implements HistorialReservaRepositoryPort {
    private final HistorialReservaJpaRepository repository;
    private final ReservaJpaRepository reservas;

    public JpaHistorialReservaAdapter(HistorialReservaJpaRepository repository, ReservaJpaRepository reservas) {
        this.repository = repository; this.reservas = reservas;
    }

    @Override public EventoReserva save(EventoReserva ev) {
        HistorialReservaEntity e = new HistorialReservaEntity();
        e.setReserva(reservas.getReferenceById(ev.reservaId()));
        e.setFecha(ev.fecha()); e.setAccion(ev.accion());
        e.setEstadoAnterior(ev.estadoAnterior()); e.setEstadoNuevo(ev.estadoNuevo());
        e.setUsuario(ev.usuario());
        e.setDetalle(ev.detalle() != null && ev.detalle().length() > 300 ? ev.detalle().substring(0, 300) : ev.detalle());
        return JpaMapper.toDomain(repository.save(e));
    }

    @Override @Transactional(readOnly = true) public List<EventoReserva> findByReservaId(Long reservaId) {
        return repository.findByReserva_IdOrderByFechaAscIdAsc(reservaId).stream().map(JpaMapper::toDomain).toList();
    }
}
