package pe.edu.upt.autorent.conexion.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
/** Configura el inicio de sesión (Spring Security) y qué rol puede entrar a cada ruta. */

@Configuration
public class SecurityConfig {
    /** BCrypt: cifra las contraseñas con un algoritmo de una sola vía (no se pueden descifrar). */
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    /**
     * Reglas de acceso, de la más específica a la más general:
     * - Público: catálogo, login, registro, estilos y fotos de autos.
     * - ADMIN: /admin/** (vehículos, usuarios, mantenimiento).
     * - ADMIN y TRABAJADOR: /operaciones/** y /reportes.
     * - CLIENTE: /reservas/**, /mis-reservas y la pantalla de pago en línea /pagos/**.
     * - Cualquier usuario con sesión: /comprobantes/{id}/pdf (el servicio valida que sea suyo).
     * Si alguien entra a una ruta sin permiso, se le muestra /acceso-denegado.
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/registro", "/css/**", "/js/**", "/autos", "/error", "/acceso-denegado").permitAll()
                .requestMatchers(HttpMethod.GET, "/autos/*/imagen").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/operaciones/**", "/reportes", "/reportes/**").hasAnyRole("ADMIN", "TRABAJADOR")
                .requestMatchers("/reservas/**", "/mis-reservas", "/mis-reservas/**", "/pagos/**").hasRole("CLIENTE")
                .requestMatchers("/inicio").authenticated()
                .anyRequest().authenticated())
            .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/inicio", true).permitAll())
            .logout(logout -> logout.logoutSuccessUrl("/login?logout").permitAll())
            .exceptionHandling(ex -> ex.accessDeniedPage("/acceso-denegado"));
        return http.build();
    }
}
