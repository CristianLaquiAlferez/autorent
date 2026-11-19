package pe.edu.upt.autorent.conexion.seguridad;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CsrfException;
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
                .requestMatchers(HttpMethod.GET, "/autos/*/imagen", "/autos/*/ilustracion.svg").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/operaciones/**", "/reportes", "/reportes/**").hasAnyRole("ADMIN", "TRABAJADOR")
                .requestMatchers("/reservas/**", "/mis-reservas", "/mis-reservas/**", "/pagos/**", "/mi-cuenta").hasRole("CLIENTE")
                .requestMatchers("/inicio").authenticated()
                .anyRequest().authenticated())
            .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/inicio", true).permitAll())
            .logout(logout -> logout.logoutSuccessUrl("/login?logout").permitAll())
            .exceptionHandling(ex -> ex.accessDeniedHandler(manejadorAccesoDenegado()));
        return http.build();
    }

    /**
     * Acceso denegado:
     * - Si falla el código CSRF de alguien sin sesión (la página de ingreso quedó abierta mucho tiempo o el
     *   servidor se reinició), no es un ataque: se le devuelve al ingreso con el aviso "la página expiró".
     * - En cualquier otro caso (ruta de otro rol, o un envío sin código CSRF con sesión activa) se muestra
     *   la página "Acceso denegado" con estado 403.
     */
    private AccessDeniedHandler manejadorAccesoDenegado() {
        AccessDeniedHandlerImpl paginaDenegado = new AccessDeniedHandlerImpl();
        paginaDenegado.setErrorPage("/acceso-denegado");
        return (request, response, ex) -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            boolean sinSesion = auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated();
            if (ex instanceof CsrfException && sinSesion) {
                // Vuelve al mismo formulario público (registro) o al ingreso, sin guardar nada de lo enviado.
                String ruta = request.getRequestURI().substring(request.getContextPath().length());
                String destino = "/registro".equals(ruta) ? "/registro?expirada" : "/login?expirada";
                response.sendRedirect(request.getContextPath() + destino);
                return;
            }
            paginaDenegado.handle(request, response, ex);
        };
    }
}
