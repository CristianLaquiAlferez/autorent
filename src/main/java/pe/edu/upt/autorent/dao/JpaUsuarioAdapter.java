package pe.edu.upt.autorent.dao;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upt.autorent.modelo.Rol;
import pe.edu.upt.autorent.modelo.Usuario;
import pe.edu.upt.autorent.repositorio.UsuarioRepositoryPort;
import pe.edu.upt.autorent.modelo.entidad.UsuarioEntity;
import pe.edu.upt.autorent.dao.springdata.UsuarioJpaRepository;
import java.util.List;
import java.util.Optional;
/** Adaptador DAO: implementa el acceso a usuarios mediante JPA. */

@Repository
@Transactional
public class JpaUsuarioAdapter implements UsuarioRepositoryPort {
    private final UsuarioJpaRepository repository;
    public JpaUsuarioAdapter(UsuarioJpaRepository repository) { this.repository = repository; }

    @Override public Usuario save(Usuario usuario) {
        UsuarioEntity e = usuario.id() == null ? new UsuarioEntity() : repository.findById(usuario.id()).orElseGet(UsuarioEntity::new);
        e.setId(usuario.id()); e.setUsername(usuario.username()); e.setPasswordHash(usuario.passwordHash());
        e.setNombres(usuario.nombres()); e.setApellidos(usuario.apellidos()); e.setDocumento(usuario.documento());
        e.setCorreo(usuario.correo()); e.setTelefono(usuario.telefono()); e.setDireccion(usuario.direccion());
        e.setRol(usuario.rol()); e.setActivo(usuario.activo());
        return JpaMapper.toDomain(repository.save(e));
    }
    @Override @Transactional(readOnly = true) public Optional<Usuario> findById(Long id) { return repository.findById(id).map(JpaMapper::toDomain); }
    @Override @Transactional(readOnly = true) public Optional<Usuario> findByUsername(String username) { return repository.findByUsernameIgnoreCase(username).map(JpaMapper::toDomain); }
    @Override @Transactional(readOnly = true) public List<Usuario> findAll() { return repository.findAllByOrderByNombresAsc().stream().map(JpaMapper::toDomain).toList(); }
    @Override @Transactional(readOnly = true) public List<Usuario> findByRol(Rol rol) { return repository.findByRolOrderByNombresAsc(rol).stream().map(JpaMapper::toDomain).toList(); }
    @Override @Transactional(readOnly = true) public boolean existsByUsername(String username) { return repository.existsByUsernameIgnoreCase(username); }
    @Override @Transactional(readOnly = true) public boolean existsByDocumento(String documento) { return repository.existsByDocumento(documento); }
    @Override @Transactional(readOnly = true) public boolean existsByCorreo(String correo) { return repository.existsByCorreoIgnoreCase(correo); }
    @Override @Transactional(readOnly = true) public boolean existsByDocumentoAndIdNot(String documento, Long id) { return repository.existsByDocumentoAndIdNot(documento, id); }
    @Override @Transactional(readOnly = true) public boolean existsByCorreoAndIdNot(String correo, Long id) { return repository.existsByCorreoIgnoreCaseAndIdNot(correo, id); }
}
