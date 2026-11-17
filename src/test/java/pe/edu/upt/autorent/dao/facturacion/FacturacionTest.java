package pe.edu.upt.autorent.dao.facturacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pe.edu.upt.autorent.modelo.*;
import static org.junit.jupiter.api.Assertions.*;

/** Pruebas del formato de los comprobantes: JSON para Nubefact, código QR de SUNAT y monto en letras. */
class FacturacionTest {
    private Comprobante comprobante(TipoComprobante tipo, String serie, int numero) {
        return new Comprobante(null, tipo, serie, numero, LocalDate.of(2026, 11, 16), 7L, 5L, 3L, "70000003",
                "MARÍA CLIENTE DEMO", "Tacna", "Alquiler de vehículo", new BigDecimal("127.12"), new BigDecimal("22.88"),
                new BigDecimal("150.00"), EstadoComprobante.PENDIENTE, "PRUEBA", null, null, null, null, null, LocalDateTime.now());
    }

    @Test void armaElJsonDeBoletaConLosCamposDeNubefact() {
        NubefactAdapter nubefact = new NubefactAdapter("https://api.nubefact.com/api/v1/x", "t", "BBB1", "BBB1");
        Map<String, Object> j = nubefact.armarJson(comprobante(TipoComprobante.BOLETA, "BBB1", 12), null);
        assertEquals("generar_comprobante", j.get("operacion"));
        assertEquals(2, j.get("tipo_de_comprobante"));
        assertEquals(1, j.get("cliente_tipo_de_documento"));
        assertEquals("16-11-2026", j.get("fecha_de_emision"));
        assertEquals(new BigDecimal("127.12"), j.get("total_gravada"));
        Map<?, ?> item = (Map<?, ?>) ((List<?>) j.get("items")).get(0);
        assertEquals("ZZ", item.get("unidad_de_medida"));
        assertEquals(new BigDecimal("150.00"), item.get("precio_unitario"));
        assertFalse(j.containsKey("documento_que_se_modifica_tipo"));
    }

    @Test void laNotaDeCreditoReferenciaLaBoleta() {
        NubefactAdapter nubefact = new NubefactAdapter("https://api.nubefact.com/api/v1/x", "t", "BBB1", "BBB1");
        Comprobante boleta = comprobante(TipoComprobante.BOLETA, "BBB1", 12);
        Map<String, Object> j = nubefact.armarJson(comprobante(TipoComprobante.NOTA_CREDITO, "BBB1", 13), boleta);
        assertEquals(3, j.get("tipo_de_comprobante"));
        assertEquals(2, j.get("documento_que_se_modifica_tipo"));
        assertEquals(12, j.get("documento_que_se_modifica_numero"));
        assertEquals(1, j.get("tipo_de_nota_de_credito"));
    }

    @Test void elModoDePruebaGeneraHashYCadenaQrDeSunat() {
        FacturacionSimuladaAdapter prueba = new FacturacionSimuladaAdapter("20123456786", "B001", "BC01");
        ResultadoEmision r = prueba.emitir(comprobante(TipoComprobante.BOLETA, "B001", 1), null);
        assertEquals(EstadoComprobante.EMITIDO, r.estado());
        assertEquals(28, r.hash().length());
        assertTrue(r.cadenaQr().startsWith("20123456786|03|B001|1|22.88|150.00|2026-11-16|1|70000003|"));
        assertEquals("B001-00000001", comprobante(TipoComprobante.BOLETA, "B001", 1).serieNumero());
    }

    @Test void escribeElMontoEnLetras() {
        assertEquals("CIENTO CINCUENTA CON 00/100 SOLES", MontoEnLetras.soles(new BigDecimal("150")));
        assertEquals("MIL DOSCIENTOS CINCUENTA CON 50/100 SOLES", MontoEnLetras.soles(new BigDecimal("1250.50")));
        assertEquals("VEINTIÚN MIL CON 00/100 SOLES", MontoEnLetras.soles(new BigDecimal("21000")));
        assertEquals("TREINTA Y UN MIL CIENTO UNO CON 00/100 SOLES", MontoEnLetras.soles(new BigDecimal("31101")));
        assertEquals("CIEN CON 05/100 SOLES", MontoEnLetras.soles(new BigDecimal("100.05")));
        assertEquals("NOVENTA Y NUEVE CON 99/100 SOLES", MontoEnLetras.soles(new BigDecimal("99.99")));
    }
}
