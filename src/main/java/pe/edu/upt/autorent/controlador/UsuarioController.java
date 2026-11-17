package pe.edu.upt.autorent.controlador;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.upt.autorent.servicio.contrato.UsuarioUseCase;
import pe.edu.upt.autorent.controlador.formulario.RegistroForm;
/** Controlador web de administración de usuarios (solo ADMIN): registrar trabajadores y activar o desactivar cuentas. */

@Controller
@RequestMapping("/admin/usuarios")
public class UsuarioController {
    private final UsuarioUseCase usuarios;
    public UsuarioController(UsuarioUseCase usuarios) { this.usuarios = usuarios; }

    /** Lista de usuarios y formulario para un nuevo trabajador. */
    @GetMapping public String listar(Model model) {
        model.addAttribute("usuarios", usuarios.listarUsuarios());
        model.addAttribute("form", new RegistroForm());
        return "admin/usuarios";
    }

    /** Registra un trabajador. */
    @PostMapping("/trabajador") public String crearTrabajador(@ModelAttribute("form") RegistroForm form, RedirectAttributes flash) {
        try {
            usuarios.crearTrabajador(form.getUsername(), form.getPassword(), form.getNombres(), form.getApellidos(),
                    form.getDocumento(), form.getCorreo(), form.getTelefono(), form.getDireccion());
            flash.addFlashAttribute("ok", "Trabajador registrado correctamente.");
        } catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/admin/usuarios";
    }

    /** Activa o desactiva una cuenta. */
    @PostMapping("/{id}/estado") public String cambiarEstado(@PathVariable Long id,
            @RequestParam boolean activo, RedirectAttributes flash) {
        try { usuarios.cambiarEstado(id, activo); flash.addFlashAttribute("ok", "Estado de usuario actualizado."); }
        catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/admin/usuarios";
    }
}
