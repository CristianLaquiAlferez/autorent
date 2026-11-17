package pe.edu.upt.autorent.servicio.contrato;

import pe.edu.upt.autorent.modelo.Rol;
import pe.edu.upt.autorent.modelo.Usuario;
import java.util.List;
import java.util.Optional;
/** Puerto de entrada: casos de uso de registro y administración de usuarios. */

public interface UsuarioUseCase {
    Usuario registrarCliente(String username, String password, String nombres, String apellidos,
                             String documento, String correo, String telefono, String direccion);
    Usuario crearTrabajador(String username, String password, String nombres, String apellidos,
                            String documento, String correo, String telefono, String direccion);
    /** Caso de uso: listar usuarios. */
    List<Usuario> listarUsuarios();
    /** Caso de uso: listar por rol. */
    List<Usuario> listarPorRol(Rol rol);
    /** Caso de uso: buscar por username. */
    Optional<Usuario> buscarPorUsername(String username);
    /** Caso de uso: obtener. */
    Usuario obtener(Long id);
    /** Caso de uso: cambiar estado. */
    void cambiarEstado(Long id, boolean activo);
}
