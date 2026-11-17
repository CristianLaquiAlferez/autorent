package pe.edu.upt.autorent.repositorio;

import pe.edu.upt.autorent.modelo.Rol;
import pe.edu.upt.autorent.modelo.Usuario;
import java.util.List;
import java.util.Optional;
/** Puerto de salida: contrato para consultar y guardar usuarios. */

public interface UsuarioRepositoryPort {
    /** Guarda un registro nuevo o actualizado. */
    Usuario save(Usuario usuario);
    /** Busca un registro por su identificador. */
    Optional<Usuario> findById(Long id);
    /** Busca un usuario por su nombre de acceso. */
    Optional<Usuario> findByUsername(String username);
    /** Lista todos los registros del modulo. */
    List<Usuario> findAll();
    /** Contrato de acceso a datos para find by rol. */
    List<Usuario> findByRol(Rol rol);
    /** Contrato de acceso a datos para exists by username. */
    boolean existsByUsername(String username);
    /** Contrato de acceso a datos para exists by documento. */
    boolean existsByDocumento(String documento);
    /** Contrato de acceso a datos para exists by correo. */
    boolean existsByCorreo(String correo);
    /** Contrato de acceso a datos para exists by documento and id not. */
    boolean existsByDocumentoAndIdNot(String documento, Long id);
    /** Contrato de acceso a datos para exists by correo and id not. */
    boolean existsByCorreoAndIdNot(String correo, Long id);
}
