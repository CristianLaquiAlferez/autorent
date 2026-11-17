package pe.edu.upt.autorent.dao.springdata;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.upt.autorent.modelo.Rol;
import pe.edu.upt.autorent.modelo.entidad.UsuarioEntity;
import java.util.List;
import java.util.Optional;
/** Repositorio Spring Data JPA: consultas concretas de la tabla de usuario. */

public interface UsuarioJpaRepository extends JpaRepository<UsuarioEntity, Long> {
    Optional<UsuarioEntity> findByUsernameIgnoreCase(String username);
    List<UsuarioEntity> findAllByOrderByNombresAsc();
    List<UsuarioEntity> findByRolOrderByNombresAsc(Rol rol);
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByDocumento(String documento);
    boolean existsByCorreoIgnoreCase(String correo);
    boolean existsByDocumentoAndIdNot(String documento, Long id);
    boolean existsByCorreoIgnoreCaseAndIdNot(String correo, Long id);
}
