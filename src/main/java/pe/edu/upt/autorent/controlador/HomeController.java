package pe.edu.upt.autorent.controlador;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import pe.edu.upt.autorent.servicio.contrato.AutoUseCase;
/** Controlador web de la pantalla de inicio, el login y el acceso denegado. */

@Controller
@RequestMapping
public class HomeController {
    private final AutoUseCase autos;
    public HomeController(AutoUseCase autos) { this.autos = autos; }

    /** Sin sesión se muestra el catálogo; con sesión, el inicio. */
    @GetMapping("/") public String raiz(Authentication authentication) {
        return authentication == null ? "redirect:/autos" : "redirect:/inicio";
    }

    /** Inicio con accesos rápidos según el rol y los vehículos disponibles. */
    @GetMapping("/inicio") public String inicio(Model model) {
        model.addAttribute("autos", autos.listarDisponibles());
        return "inicio";
    }

    // Spring Security procesa el POST del login; aquí solo se muestran las páginas.
    @GetMapping("/login") public String login() { return "auth/login"; }
    @GetMapping("/acceso-denegado") public String accesoDenegado() { return "auth/acceso-denegado"; }
}
