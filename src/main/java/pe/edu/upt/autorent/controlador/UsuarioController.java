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
        if (!model.containsAttribute("form")) model.addAttribute("form", new RegistroForm());
        return "admin/usuarios";
    }

    /** Registra un trabajador. */
    @PostMapping("/trabajador") public String crearTrabajador(@ModelAttribute("form") RegistroForm form, RedirectAttributes flash) {
        try {
            usuarios.crearTrabajador(form.getUsername(), form.getPassword(), form.getNombres(), form.getApellidos(),
                    form.getDocumento(), form.getCorreo(), form.getTelefono(), form.getDireccion());
            flash.addFlashAttribute("ok", "Trabajador registrado correctamente.");
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            form.setPassword(null);                 // la contraseña nunca se devuelve al formulario
            flash.addFlashAttribute("form", form);  // lo demás se conserva para no tener que escribirlo de nuevo
        }
        return "redirect:/admin/usuarios";
    }

    /** Formulario para corregir los datos de cualquier cuenta. */
    @GetMapping("/{id}/editar") public String editar(@PathVariable Long id, Model model, RedirectAttributes flash) {
        try {
            var u = usuarios.obtener(id);
            if (!model.containsAttribute("form")) model.addAttribute("form", RegistroForm.de(u));
            model.addAttribute("usuario", u);
            model.addAttribute("titulo", "Editar datos de " + u.nombres() + " " + u.apellidos());
            model.addAttribute("accion", "/admin/usuarios/" + id + "/editar");
            model.addAttribute("volver", "/admin/usuarios");
            model.addAttribute("soloContacto", false);
            return "usuarios/editar";
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/usuarios";
        }
    }

    /** Guarda la corrección; si algo no es válido vuelve al formulario con lo escrito. */
    @PostMapping("/{id}/editar") public String guardarEdicion(@PathVariable Long id, @ModelAttribute("form") RegistroForm form,
            org.springframework.security.core.Authentication auth, RedirectAttributes flash) {
        try {
            usuarios.actualizarDatos(id, form.getNombres(), form.getApellidos(), form.getDocumento(), form.getCorreo(),
                    form.getTelefono(), form.getDireccion(), auth.getName());
            flash.addFlashAttribute("ok", "Datos actualizados.");
            return "redirect:/admin/usuarios";
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            flash.addFlashAttribute("form", form);
            return "redirect:/admin/usuarios/" + id + "/editar";
        }
    }

    /** Activa o desactiva una cuenta. */
    @PostMapping("/{id}/estado") public String cambiarEstado(@PathVariable Long id,
            @RequestParam boolean activo, RedirectAttributes flash) {
        try { usuarios.cambiarEstado(id, activo); flash.addFlashAttribute("ok", "Estado de usuario actualizado."); }
        catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/admin/usuarios";
    }
}
