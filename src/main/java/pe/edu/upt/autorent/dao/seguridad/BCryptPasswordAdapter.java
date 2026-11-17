package pe.edu.upt.autorent.dao.seguridad;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import pe.edu.upt.autorent.repositorio.PasswordEncoderPort;
/** Adaptador que aplica BCrypt al contrato de cifrado de contraseñas. */

@Component
public class BCryptPasswordAdapter implements PasswordEncoderPort {
    private final PasswordEncoder passwordEncoder;
    public BCryptPasswordAdapter(PasswordEncoder passwordEncoder) { this.passwordEncoder = passwordEncoder; }
    @Override public String encode(String rawPassword) { return passwordEncoder.encode(rawPassword); }
    @Override public boolean matches(String rawPassword, String encodedPassword) { return passwordEncoder.matches(rawPassword, encodedPassword); }
}
