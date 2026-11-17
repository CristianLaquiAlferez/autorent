package pe.edu.upt.autorent.controlador;

import pe.edu.upt.autorent.servicio.pdf.ReportePdfGenerator;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import pe.edu.upt.autorent.servicio.contrato.ReporteUseCase;
/** Controlador web de reportes (ADMIN y TRABAJADOR): indicadores en pantalla y descarga en PDF. */

@Controller
@RequestMapping("/reportes")
public class ReporteController {
    private final ReporteUseCase reportes;
    public ReporteController(ReporteUseCase reportes) { this.reportes = reportes; }
    /**
     * Descarga el reporte en PDF. La cabecera Content-Disposition "attachment" hace que el
     * navegador descargue el archivo en vez de abrirlo; el nombre lleva la fecha y hora.
     */
    @GetMapping("/descargar-pdf")
    public ResponseEntity<byte[]> descargarPdf() {
        byte[] contenido = ReportePdfGenerator.generar(reportes.generarResumen());
        String nombre = "AutoRent_Reporte_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(contenido);
    }

    /** Pantalla de reportes con los indicadores y el historial. */
    @GetMapping public String index(Model model) {
        model.addAttribute("resumen", reportes.generarResumen());
        return "reportes/index";
    }
}
