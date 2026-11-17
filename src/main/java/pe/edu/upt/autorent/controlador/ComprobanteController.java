package pe.edu.upt.autorent.controlador;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.upt.autorent.servicio.contrato.ComprobanteUseCase;

/**
 * Controlador web de comprobantes electrónicos.
 * - /comprobantes/{id}/pdf: cualquier usuario con sesión; el cliente solo ve los suyos (lo valida el servicio).
 * - /operaciones/comprobantes: listado y reintento de envío para ADMIN y TRABAJADOR.
 */
@Controller
public class ComprobanteController {
    private final ComprobanteUseCase comprobantes;

    public ComprobanteController(ComprobanteUseCase comprobantes) { this.comprobantes = comprobantes; }

    /** Descarga el PDF (o redirige al PDF oficial de Nubefact). */
    @GetMapping("/comprobantes/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id, Authentication auth) {
        var archivo = comprobantes.obtenerPdf(id, auth.getName());
        if (archivo.enlaceExterno() != null) {
            return ResponseEntity.status(302).header(HttpHeaders.LOCATION, archivo.enlaceExterno()).build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(archivo.nombre()).build().toString())
                .body(archivo.contenido());
    }

    /** Listado para el personal: estado de envío de cada boleta y nota de crédito. */
    @GetMapping("/operaciones/comprobantes")
    public String listar(Model model) {
        model.addAttribute("comprobantes", comprobantes.listarTodos());
        return "operaciones/comprobantes";
    }

    /** Reenvía un comprobante que quedó con error (por ejemplo, sin internet en ese momento). */
    @PostMapping("/operaciones/comprobantes/{id}/reintentar")
    public String reintentar(@PathVariable Long id, RedirectAttributes flash) {
        try {
            var c = comprobantes.reintentar(id);
            flash.addFlashAttribute(c.emitido() ? "ok" : "error", "Comprobante " + c.serieNumero() + ": " + c.mensaje());
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/operaciones/comprobantes";
    }
}
