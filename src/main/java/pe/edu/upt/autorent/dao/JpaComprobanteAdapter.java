package pe.edu.upt.autorent.dao;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upt.autorent.dao.springdata.ComprobanteJpaRepository;
import pe.edu.upt.autorent.modelo.Comprobante;
import pe.edu.upt.autorent.modelo.TipoComprobante;
import pe.edu.upt.autorent.modelo.entidad.ComprobanteEntity;
import pe.edu.upt.autorent.repositorio.ComprobanteRepositoryPort;

/** Adaptador DAO: implementa ComprobanteRepositoryPort con JPA. */
@Repository
@Transactional
public class JpaComprobanteAdapter implements ComprobanteRepositoryPort {
    private final ComprobanteJpaRepository repository;

    public JpaComprobanteAdapter(ComprobanteJpaRepository repository) { this.repository = repository; }

    @Override public Comprobante save(Comprobante c) {
        ComprobanteEntity e = c.id() == null ? new ComprobanteEntity() : repository.findById(c.id()).orElseGet(ComprobanteEntity::new);
        e.setId(c.id()); e.setTipo(c.tipo()); e.setSerie(c.serie()); e.setNumero(c.numero()); e.setFechaEmision(c.fechaEmision());
        e.setPagoId(c.pagoId()); e.setReservaId(c.reservaId()); e.setClienteId(c.clienteId());
        e.setClienteDocumento(c.clienteDocumento()); e.setClienteNombre(c.clienteNombre()); e.setClienteDireccion(c.clienteDireccion());
        e.setDescripcion(c.descripcion()); e.setGravada(c.gravada()); e.setIgv(c.igv()); e.setTotal(c.total());
        e.setEstado(c.estado()); e.setProveedor(c.proveedor()); e.setEnlacePdf(c.enlacePdf()); e.setHash(c.hash());
        e.setCadenaQr(c.cadenaQr()); e.setMensaje(recortar(c.mensaje(), 500)); e.setReferenciaId(c.referenciaId());
        e.setCreadoEn(c.creadoEn());
        return JpaMapper.toDomain(repository.save(e));
    }
    @Override @Transactional(readOnly = true) public Optional<Comprobante> findById(Long id) { return repository.findById(id).map(JpaMapper::toDomain); }
    @Override @Transactional(readOnly = true) public Optional<Comprobante> findByPagoIdAndTipo(Long pagoId, TipoComprobante tipo) {
        return repository.findFirstByPagoIdAndTipo(pagoId, tipo).map(JpaMapper::toDomain);
    }
    @Override @Transactional(readOnly = true) public List<Comprobante> findAll() { return repository.findAllByOrderByIdDesc().stream().map(JpaMapper::toDomain).toList(); }
    @Override @Transactional(readOnly = true) public List<Comprobante> findByClienteId(Long clienteId) {
        return repository.findByClienteIdOrderByIdDesc(clienteId).stream().map(JpaMapper::toDomain).toList();
    }
    @Override @Transactional(readOnly = true) public List<Comprobante> findByReservaId(Long reservaId) {
        return repository.findByReservaIdOrderByIdAsc(reservaId).stream().map(JpaMapper::toDomain).toList();
    }
    @Override @Transactional(readOnly = true) public int ultimoNumero(TipoComprobante tipo, String serie) { return repository.ultimoNumero(tipo, serie); }

    private static String recortar(String texto, int maximo) {
        return texto == null || texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }
}
