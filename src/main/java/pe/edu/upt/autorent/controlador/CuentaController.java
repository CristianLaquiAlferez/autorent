package pe.edu.upt.autorent.controlador;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.upt.autorent.controlador.formulario.RegistroForm;
import pe.edu.upt.autorent.modelo.Usuario;
import pe.edu.upt.autorent.servicio.contrato.UsuarioUseCase;

/**
 * "Mis datos" del cliente: corrige su correo, teléfono y dirección.
 * Su nombre y DNI (los de sus boletas) los corrige el personal al ver su documento.
 */
@Controller
public class CuentaController {
    private final UsuarioUseCase usuarios;

    public CuentaController(UsuarioUseCase usuarios) { this.usuarios = usuarios; }

    @GetMapping("/mi-cuenta") public String verDatos(Authentication auth, Model model) {
        Usuario u = usuarios.buscarPorUsername(auth.getName()).orElseThrow();
        if (!model.containsAttribute("form")) model.addAttribute("form", RegistroForm.de(u));
        model.addAttribute("usuario", u);
        model.addAttribute("titulo", "Mis datos");
        model.addAttribute("accion", "/mi-cuenta");
        model.addAttribute("volver", "/mis-reservas");
        model.addAttribute("soloContacto", true);
        return "usuarios/editar";
    }

    @PostMapping("/mi-cuenta") public String guardar(@ModelAttribute("form") RegistroForm form, Authentication auth,
                                                    RedirectAttributes flash) {
        Usuario u = usuarios.buscarPorUsername(auth.getName()).orElseThrow();
        try {
            // El servicio ignora nombre y DNI cuando el cliente edita su propia cuenta.
            usuarios.actualizarDatos(u.id(), u.nombres(), u.apellidos(), u.documento(), form.getCorreo(),
                    form.getTelefono(), form.getDireccion(), auth.getName());
            flash.addFlashAttribute("ok", "Sus datos se actualizaron.");
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            form.setNombres(u.nombres()); form.setApellidos(u.apellidos()); form.setDocumento(u.documento());
            flash.addFlashAttribute("form", form);
        }
        return "redirect:/mi-cuenta";
    }
}
