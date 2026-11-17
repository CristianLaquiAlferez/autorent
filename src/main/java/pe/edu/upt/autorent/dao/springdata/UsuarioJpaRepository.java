package pe.edu.upt.autorent.dao.springdata;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.upt.autorent.modelo.Rol;
import pe.edu.upt.autorent.modelo.entidad.UsuarioEntity;
import java.util.List;
import java.util.Optional;
/** Repositorio Spring Data JPA: consultas concretas de la tabla de usuario. */

public interface UsuarioJpaRepository extends JpaRepository<UsuarioEntity, Long> {
    Optional<UsuarioEntity> findByUsernameIgnoreCase(String username);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UsuarioEntity u where u.id = :id")
    Optional<UsuarioEntity> bloquearPorId(@Param("id") Long id);
    List<UsuarioEntity> findAllByOrderByNombresAsc();
    List<UsuarioEntity> findByRolOrderByNombresAsc(Rol rol);
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByDocumento(String documento);
    boolean existsByCorreoIgnoreCase(String correo);
    boolean existsByDocumentoAndIdNot(String documento, Long id);
    boolean existsByCorreoIgnoreCaseAndIdNot(String correo, Long id);
}
