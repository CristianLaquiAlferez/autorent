package pe.edu.upt.autorent.controlador;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.upt.autorent.servicio.contrato.UsuarioUseCase;
import pe.edu.upt.autorent.controlador.formulario.RegistroForm;
/** Controlador web del registro público: cualquier persona puede crear su cuenta de CLIENTE. */

@Controller
@RequestMapping("/registro")
public class AuthController {
    private final UsuarioUseCase usuarios;
    public AuthController(UsuarioUseCase usuarios) { this.usuarios = usuarios; }

    /** Muestra el formulario "Crear cuenta". */
    @GetMapping public String formulario(Model model) {
        if (!model.containsAttribute("form")) model.addAttribute("form", new RegistroForm());
        return "auth/registro";
    }

    /** Registra al cliente y lo envía al login; si hay un error, vuelve al formulario con el mensaje. */
    @PostMapping public String registrar(@ModelAttribute("form") RegistroForm form, RedirectAttributes flash) {
        try {
            usuarios.registrarCliente(form.getUsername(), form.getPassword(), form.getNombres(), form.getApellidos(),
                    form.getDocumento(), form.getCorreo(), form.getTelefono(), form.getDireccion());
            flash.addFlashAttribute("ok", "Cuenta creada correctamente. Ya puede iniciar sesión.");
            return "redirect:/login";
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            form.setPassword(null);                 // la contraseña nunca se devuelve al formulario
            flash.addFlashAttribute("form", form);  // lo demás se conserva para no tener que escribirlo de nuevo
            return "redirect:/registro";
        }
    }
}
