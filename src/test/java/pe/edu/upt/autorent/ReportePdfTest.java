package pe.edu.upt.autorent;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import pe.edu.upt.autorent.modelo.ResumenReporte;
import pe.edu.upt.autorent.servicio.pdf.ReportePdfGenerator;
import static org.junit.jupiter.api.Assertions.*;

/** Comprueba que el reporte se entrega en formato PDF. */
class ReportePdfTest {
    @Test
    void generaDocumentoPdfConResumenVacio() {
        ResumenReporte datos = new ResumenReporte(0, 0, 0, 0, 0, 0,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of());
        byte[] pdf = ReportePdfGenerator.generar(datos);
        assertTrue(pdf.length > 500);
        assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.US_ASCII));
    }
}
