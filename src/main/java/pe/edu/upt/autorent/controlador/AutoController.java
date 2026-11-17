package pe.edu.upt.autorent.controlador;

import org.springframework.stereotype.Controller;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.upt.autorent.modelo.Auto;
import pe.edu.upt.autorent.modelo.EstadoAuto;
import pe.edu.upt.autorent.servicio.contrato.AutoUseCase;
import pe.edu.upt.autorent.controlador.formulario.AutoForm;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
/**
 * Controlador web de vehículos: catálogo público, administración (solo ADMIN), fotos y mantenimiento.
 * Las validaciones del vehículo están en AutoService; aquí solo se arman los datos del formulario.
 */

@Controller
public class AutoController {
    private static final Logger log = LoggerFactory.getLogger(AutoController.class);
    private final AutoUseCase autos;
    public AutoController(AutoUseCase autos) { this.autos = autos; }

    /** Catálogo: muestra solo los vehículos DISPONIBLES. */
    @GetMapping("/autos") public String catalogo(Model model) {
        model.addAttribute("autos", autos.listarDisponibles());
        return "autos/catalogo";
    }

    /** Lista de todos los vehículos para el administrador. */
    @GetMapping("/admin/autos") public String administrar(Model model) {
        model.addAttribute("autos", autos.listarTodos());
        return "autos/administrar";
    }

    /** Formulario vacío para registrar un vehículo. */
    @GetMapping("/admin/autos/nuevo") public String nuevo(Model model) {
        model.addAttribute("form", new AutoForm());
        model.addAttribute("titulo", "Registrar vehículo");
        model.addAttribute("anioActual", LocalDate.now().getYear());
        return "autos/formulario";
    }

    /** Formulario con los datos actuales del vehículo para editarlo. */
    @GetMapping("/admin/autos/{id}/editar") public String editar(@PathVariable Long id, Model model) {
        Auto a = autos.obtener(id);
        AutoForm f = new AutoForm(); f.setId(a.id()); f.setPlaca(a.placa()); f.setMarca(a.marca());
        f.setModelo(a.modelo()); f.setAnio(a.anio()); f.setCategoria(a.categoria()); f.setColor(a.color());
        f.setTarifaDiaria(a.tarifaDiaria()); f.setDescripcion(a.descripcion());
        if (a.imagenUrl() != null && a.imagenUrl().startsWith("http")) f.setImagenUrl(a.imagenUrl());
        model.addAttribute("imagenActual", a.imagenUrl());
        model.addAttribute("form", f); model.addAttribute("titulo", "Editar vehículo");
        model.addAttribute("anioActual", LocalDate.now().getYear());
        return "autos/formulario";
    }

    /** Recibe el formulario y el archivo de imagen; delega las reglas al servicio. */
    @PostMapping("/admin/autos/guardar")
    public String guardar(@ModelAttribute("form") AutoForm f,
                          @RequestParam(name = "imagenArchivo", required = false) MultipartFile imagenArchivo,
                          RedirectAttributes flash) {
        try {
            byte[] contenido = null;
            if (imagenArchivo != null && !imagenArchivo.isEmpty()) {
                if (imagenArchivo.getSize() > 5L * 1024 * 1024)
                    throw new IllegalArgumentException("La foto no debe superar 5 MB.");
                contenido = imagenArchivo.getBytes();
            } else if (imagenArchivo != null && imagenArchivo.getOriginalFilename() != null
                    && !imagenArchivo.getOriginalFilename().isBlank()) {
                throw new IllegalArgumentException("El archivo seleccionado está vacío.");
            }
            autos.guardarConImagen(new Auto(f.getId(), f.getPlaca(), f.getMarca(), f.getModelo(), f.getAnio(),
                    f.getCategoria(), f.getColor(), f.getTarifaDiaria(), f.getDescripcion(),
                    EstadoAuto.DISPONIBLE, f.getImagenUrl()), contenido,
                    imagenArchivo == null ? null : imagenArchivo.getContentType(), f.isEliminarImagen());
            flash.addFlashAttribute("ok", "Vehículo e imagen guardados correctamente.");
            return "redirect:/admin/autos";
        } catch (IllegalArgumentException | IllegalStateException | IOException ex) {
            flash.addFlashAttribute("error", ex instanceof IOException
                    ? "No se pudo leer la foto. Seleccione otro archivo." : ex.getMessage());
            return f.getId() == null ? "redirect:/admin/autos/nuevo" : "redirect:/admin/autos/" + f.getId() + "/editar";
        } catch (RuntimeException ex) { // error inesperado (por ejemplo, MySQL apagado): se registra en el log
            log.error("No se pudo guardar el vehículo", ex);
            flash.addFlashAttribute("error", "No se pudo guardar el vehículo. Revise la conexión con MySQL e intente nuevamente.");
            return f.getId() == null ? "redirect:/admin/autos/nuevo" : "redirect:/admin/autos/" + f.getId() + "/editar";
        }
    }

    /** Devuelve los bytes de una foto para que el navegador la muestre. */
    /**
     * Ilustración del vehículo (SVG) según su categoría y color. Se muestra cuando el auto no tiene foto
     * o cuando la foto no se puede cargar. Es pública, igual que el catálogo.
     */
    @GetMapping(value = "/autos/{id}/ilustracion.svg", produces = "image/svg+xml")
    @ResponseBody
    public ResponseEntity<String> ilustracion(@PathVariable Long id) {
        return ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofHours(1)))
                .contentType(MediaType.valueOf("image/svg+xml;charset=UTF-8"))
                .body(pe.edu.upt.autorent.servicio.IlustracionAuto.svg(autos.obtener(id)));
    }

    @GetMapping("/autos/{id}/imagen")
    @ResponseBody
    public ResponseEntity<byte[]> verImagen(@PathVariable Long id) {
        try {
            var imagen = autos.obtenerImagen(id);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(imagen.tipoContenido()))
                    .cacheControl(CacheControl.noStore())
                    .body(imagen.datos());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Cambia el estado del vehículo (DISPONIBLE / INACTIVO). */
    @PostMapping("/admin/autos/{id}/estado") public String cambiarEstado(@PathVariable Long id,
            @RequestParam EstadoAuto estado, RedirectAttributes flash) {
        try { autos.cambiarEstado(id, estado); flash.addFlashAttribute("ok", "Estado del vehículo actualizado."); }
        catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/admin/autos";
    }

    /** Pantalla de mantenimiento: vehículos y mantenimientos registrados. */
    @GetMapping("/admin/mantenimiento") public String mantenimiento(Model model) {
        model.addAttribute("fechaHoy", LocalDate.now()); // el mantenimiento no puede empezar en el pasado
        model.addAttribute("autos", autos.listarTodos());
        model.addAttribute("mantenimientos", autos.listarMantenimientos());
        return "admin/mantenimiento";
    }

    /** Envía un vehículo al taller entre las fechas indicadas. */
    @PostMapping("/admin/mantenimiento/iniciar") public String iniciarMantenimiento(
            @RequestParam Long autoId, @RequestParam String descripcion,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            RedirectAttributes flash) {
        try { autos.iniciarMantenimiento(autoId, descripcion, fechaInicio, fechaFin); flash.addFlashAttribute("ok", "Vehículo enviado a mantenimiento."); }
        catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/admin/mantenimiento";
    }

    /** Termina el mantenimiento y el vehículo vuelve a estar disponible. */
    @PostMapping("/admin/mantenimiento/{id}/finalizar") public String finalizarMantenimiento(@PathVariable Long id, RedirectAttributes flash) {
        try { autos.finalizarMantenimiento(id); flash.addFlashAttribute("ok", "Mantenimiento finalizado. Vehículo disponible."); }
        catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/admin/mantenimiento";
    }
}
