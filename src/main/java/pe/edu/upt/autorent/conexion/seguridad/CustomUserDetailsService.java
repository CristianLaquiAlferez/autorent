package pe.edu.upt.autorent.conexion.seguridad;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import pe.edu.upt.autorent.modelo.Usuario;
import pe.edu.upt.autorent.repositorio.UsuarioRepositoryPort;
/** Carga las credenciales y roles de usuario para Spring Security. */

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final UsuarioRepositoryPort usuarios;
    public CustomUserDetailsService(UsuarioRepositoryPort usuarios) { this.usuarios = usuarios; }

    @Override public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Usuario u = usuarios.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
        return User.withUsername(u.username()).password(u.passwordHash()).roles(u.rol().name())
                .disabled(!u.activo()).build();
    }
}
