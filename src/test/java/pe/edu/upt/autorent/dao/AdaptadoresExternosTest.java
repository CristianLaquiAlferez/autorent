package pe.edu.upt.autorent.dao;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.edu.upt.autorent.dao.facturacion.NubefactAdapter;
import pe.edu.upt.autorent.dao.pasarela.PayPalSandboxAdapter;
import pe.edu.upt.autorent.modelo.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueba los adaptadores de PayPal y Nubefact contra un servidor HTTP local que responde como los reales
 * (mismas rutas, cabeceras y JSON). Así se comprueba qué se envía, cómo se interpreta la respuesta
 * y qué pasa ante rechazos, errores y caídas del servicio, sin depender de internet ni de cuentas.
 */
class AdaptadoresExternosTest {

    private HttpServer servidor;
    private String base;
    /** Respuestas programadas por ruta: (petición) -> [código HTTP, cuerpo]. */
    private final Map<String, Function<Peticion, Object[]>> respuestas = new ConcurrentHashMap<>();
    /** Peticiones recibidas, para revisar lo que envió el adaptador. */
    private final List<Peticion> recibidas = new ArrayList<>();

    record Peticion(String metodo, String ruta, String autorizacion, String prefer, String cuerpo) { }

    @BeforeEach void iniciar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", this::atender);
        servidor.start();
        base = "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    @AfterEach void detener() { servidor.stop(0); }

    private void atender(HttpExchange ex) throws IOException {
        String cuerpo = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Peticion p = new Peticion(ex.getRequestMethod(), ex.getRequestURI().getPath(),
                ex.getRequestHeaders().getFirst("Authorization"), ex.getRequestHeaders().getFirst("Prefer"), cuerpo);
        synchronized (recibidas) { recibidas.add(p); }
        Function<Peticion, Object[]> f = respuestas.entrySet().stream()
                .filter(e -> p.ruta().matches(e.getKey())).map(Map.Entry::getValue).findFirst()
                .orElse(x -> new Object[]{404, "{\"message\":\"no encontrado\"}"});
        Object[] r = f.apply(p);
        if ((int) r[0] == -1) {               // simula un servicio que no responde a tiempo
            try { Thread.sleep(3_000); } catch (InterruptedException ignored) { }
            r = new Object[]{200, "{}"};
        }
        byte[] salida = String.valueOf(r[1]).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders((int) r[0], salida.length);
        ex.getResponseBody().write(salida);
        ex.close();
    }

    private List<Peticion> peticionesA(String ruta) {
        synchronized (recibidas) { return recibidas.stream().filter(p -> p.ruta().matches(ruta)).toList(); }
    }

    // ------------------------------------------------------------------ PayPal

    private PayPalSandboxAdapter payPal() {
        respuestas.put("/v1/oauth2/token", p -> new Object[]{200, "{\"access_token\":\"TOKEN-1\",\"expires_in\":32400}"});
        return new PayPalSandboxAdapter("cliente-demo", "secreto-demo", base, "USD", new BigDecimal("3.75"));
    }

    @Test
    void payPalCreaLaOrdenEnDolaresConElTokenYReutilizaElToken() {
        PayPalSandboxAdapter pp = payPal();
        respuestas.put("/v2/checkout/orders", p -> new Object[]{201, "{\"id\":\"ORDEN-1\",\"status\":\"CREATED\"}"});

        OrdenEnLinea o1 = pp.crearOrden("RES-1-PAGO-1", new BigDecimal("150.00"), "Anticipo reserva 1");
        OrdenEnLinea o2 = pp.crearOrden("RES-1-PAGO-2", new BigDecimal("100.00"), "Saldo reserva 1");

        assertEquals("ORDEN-1", o1.id());
        assertEquals(0, new BigDecimal("40.00").compareTo(o1.monto()), "S/ 150 / 3.75 = USD 40.00");
        assertEquals(0, new BigDecimal("26.67").compareTo(o2.monto()), "S/ 100 / 3.75 = USD 26.67 (redondeo)");
        // El token se pide una sola vez y se envía como Bearer; el client_id/secret van en Basic.
        assertEquals(1, peticionesA("/v1/oauth2/token").size());
        assertTrue(peticionesA("/v1/oauth2/token").get(0).autorizacion().startsWith("Basic "));
        Peticion orden = peticionesA("/v2/checkout/orders").get(0);
        assertEquals("Bearer TOKEN-1", orden.autorizacion());
        assertTrue(orden.cuerpo().contains("\"intent\":\"CAPTURE\""));
        assertTrue(orden.cuerpo().contains("\"currency_code\":\"USD\""));
        assertTrue(orden.cuerpo().contains("\"value\":\"40.00\""));
    }

    @Test
    void payPalCapturaAprobadaDevuelveMontoCodigoYDetalleSinDatosSensibles() {
        PayPalSandboxAdapter pp = payPal();
        respuestas.put("/v2/checkout/orders/[^/]+/capture", p -> new Object[]{201, """
                {"id":"ORDEN-9","status":"COMPLETED",
                 "payer":{"email_address":"comprador@personal.example.com"},
                 "purchase_units":[{"payments":{"captures":[{"id":"CAP-77","status":"COMPLETED",
                   "amount":{"currency_code":"USD","value":"40.00"}}]}}]}"""});
        ResultadoCobro c = pp.capturar("ORDEN-9");
        assertTrue(c.aprobado());
        assertEquals("CAP-77", c.codigoTransaccion());
        assertEquals(MetodoPago.PAYPAL, c.metodo());
        assertEquals(0, new BigDecimal("40.00").compareTo(c.montoCobrado()));
        assertEquals("USD", c.moneda());
        assertEquals("PayPal comprador@personal.example.com", c.detalle());
        assertEquals("return=representation", peticionesA(".*/capture").get(0).prefer());
    }

    @Test
    void payPalCapturaConTarjetaGuardaSoloMarcaYUltimosDigitos() {
        PayPalSandboxAdapter pp = payPal();
        respuestas.put("/v2/checkout/orders/[^/]+/capture", p -> new Object[]{201, """
                {"id":"O","status":"COMPLETED","payment_source":{"card":{"brand":"VISA","last_digits":"1111"}},
                 "purchase_units":[{"payments":{"captures":[{"id":"CAP-1","status":"COMPLETED",
                   "amount":{"currency_code":"USD","value":"10.00"}}]}}]}"""});
        assertEquals("VISA **** 1111 (PayPal)", pp.capturar("O").detalle());
    }

    @Test
    void payPalRechazosYRespuestasRarasNoSeConviertenEnPagosAprobados() {
        PayPalSandboxAdapter pp = payPal();
        respuestas.put("/v2/checkout/orders/DECLINADA/capture", p -> new Object[]{422,
                "{\"name\":\"UNPROCESSABLE_ENTITY\",\"details\":[{\"issue\":\"INSTRUMENT_DECLINED\"}]}"});
        respuestas.put("/v2/checkout/orders/COBRADA/capture", p -> new Object[]{422,
                "{\"details\":[{\"issue\":\"ORDER_ALREADY_CAPTURED\"}]}"});
        respuestas.put("/v2/checkout/orders/PENDIENTE/capture", p -> new Object[]{201, """
                {"purchase_units":[{"payments":{"captures":[{"id":"C","status":"PENDING",
                  "amount":{"currency_code":"USD","value":"1.00"}}]}}]}"""});
        respuestas.put("/v2/checkout/orders/ROTA/capture", p -> new Object[]{201, "{\"status\":\"COMPLETED\"}"});
        respuestas.put("/v2/checkout/orders/CAIDA/capture", p -> new Object[]{500, "{}"});

        assertTrue(pp.capturar("DECLINADA").mensaje().contains("rechazó el medio de pago"));
        assertTrue(pp.capturar("COBRADA").mensaje().contains("ya fue cobrada"));
        assertTrue(pp.capturar("PENDIENTE").mensaje().contains("PENDING"));
        for (String id : List.of("DECLINADA", "COBRADA", "PENDIENTE", "ROTA", "CAIDA"))
            assertFalse(pp.capturar(id).aprobado(), "La orden " + id + " no debe darse por pagada");
    }

    @Test
    void payPalCredencialesInvalidasOServidorCaidoDanMensajeClaro() {
        respuestas.put("/v1/oauth2/token", p -> new Object[]{401, "{\"error\":\"invalid_client\"}"});
        PayPalSandboxAdapter pp = new PayPalSandboxAdapter("malo", "malo", base, "USD", new BigDecimal("3.75"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> pp.crearOrden("R", BigDecimal.TEN, "x"));
        assertTrue(ex.getMessage().contains("No se pudo conectar con PayPal"));
        assertFalse(pp.capturar("X").aprobado());
        assertFalse(pp.reembolsar("CAP", BigDecimal.TEN).exitoso());

        PayPalSandboxAdapter sinConfigurar = new PayPalSandboxAdapter("", "", base, "USD", new BigDecimal("3.75"));
        assertFalse(sinConfigurar.configurado());
        assertThrows(IllegalArgumentException.class, () -> sinConfigurar.crearOrden("R", BigDecimal.TEN, "x"));
    }

    @Test
    void payPalReembolsaElMismoMontoEnDolares() {
        PayPalSandboxAdapter pp = payPal();
        respuestas.put("/v2/payments/captures/[^/]+/refund", p -> new Object[]{201, "{\"id\":\"REF-5\",\"status\":\"COMPLETED\"}"});
        ResultadoReembolso r = pp.reembolsar("CAP-77", new BigDecimal("150.00"));
        assertTrue(r.exitoso());
        assertEquals("REF-5", r.referencia());
        assertTrue(peticionesA(".*/refund").get(0).cuerpo().contains("\"value\":\"40.00\""));
        assertEquals("/v2/payments/captures/CAP-77/refund", peticionesA(".*/refund").get(0).ruta());
    }

    // ------------------------------------------------------------------ Nubefact

    private static Comprobante boleta() {
        return new Comprobante(null, TipoComprobante.BOLETA, "BBB1", 15, LocalDate.of(2026, 11, 16), 7L, 3L, 2L,
                "45678912", "ANA TORRES", "Av. Bolognesi 100, Tacna", "Alquiler Kia Rio",
                new BigDecimal("101.69"), new BigDecimal("18.31"), new BigDecimal("120.00"),
                EstadoComprobante.PENDIENTE, null, null, null, null, null, null, LocalDateTime.now());
    }

    @Test
    void nubefactEnviaElTokenYGuardaPdfHashYQr() {
        respuestas.put("/api/v1/.*", p -> new Object[]{200, """
                {"tipo_de_comprobante":2,"serie":"BBB1","numero":15,
                 "enlace_del_pdf":"https://www.nubefact.com/cpe/abc.pdf","aceptada_por_sunat":true,
                 "sunat_description":"La Boleta numero BBB1-15, ha sido aceptada",
                 "codigo_hash":"HASH123","cadena_para_codigo_qr":"20123456786|03|BBB1|15|18.31|120.00|16/11/2026|1|45678912|HASH123|"}"""});
        NubefactAdapter n = new NubefactAdapter(base + "/api/v1/abc-123", "TOKEN-NUBE", "BBB1", "BBB1");
        ResultadoEmision r = n.emitir(boleta(), null);

        assertEquals(EstadoComprobante.ACEPTADO, r.estado());
        assertEquals("https://www.nubefact.com/cpe/abc.pdf", r.enlacePdf());
        assertEquals("HASH123", r.hash());
        assertTrue(r.cadenaQr().startsWith("20123456786|03|BBB1|15|"));
        Peticion p = peticionesA("/api/v1/.*").get(0);
        assertEquals("Token token=\"TOKEN-NUBE\"", p.autorizacion());
        assertTrue(p.cuerpo().contains("\"tipo_de_comprobante\":2"));
        assertTrue(p.cuerpo().contains("\"cliente_numero_de_documento\":\"45678912\""));
        assertTrue(p.cuerpo().contains("\"total\":120.00"));
    }

    @Test
    void nubefactEmitidaPeroSinRespuestaDeSunatQuedaComoEmitida() {
        respuestas.put("/api/v1/.*", p -> new Object[]{200,
                "{\"enlace_del_pdf\":\"https://x/pdf\",\"aceptada_por_sunat\":false,\"codigo_hash\":\"H\"}"});
        ResultadoEmision r = new NubefactAdapter(base + "/api/v1/x", "T", "BBB1", "BBB1").emitir(boleta(), null);
        assertEquals(EstadoComprobante.EMITIDO, r.estado());
    }

    @Test
    void nubefactErroresYCaidasQuedanEnErrorParaReintentar() {
        respuestas.put("/api/v1/errores", p -> new Object[]{200, "{\"errors\":\"El campo cliente_numero_de_documento es inválido\",\"codigo\":21}"});
        respuestas.put("/api/v1/token-malo", p -> new Object[]{401, "{\"errors\":\"Token incorrecto\"}"});
        respuestas.put("/api/v1/servidor-caido", p -> new Object[]{500, "error"});

        ResultadoEmision e1 = new NubefactAdapter(base + "/api/v1/errores", "T", "BBB1", "BBB1").emitir(boleta(), null);
        ResultadoEmision e2 = new NubefactAdapter(base + "/api/v1/token-malo", "T", "BBB1", "BBB1").emitir(boleta(), null);
        ResultadoEmision e3 = new NubefactAdapter(base + "/api/v1/servidor-caido", "T", "BBB1", "BBB1").emitir(boleta(), null);
        ResultadoEmision e4 = new NubefactAdapter("http://127.0.0.1:1/api/v1/sin-servidor", "T", "BBB1", "BBB1").emitir(boleta(), null);

        assertEquals(EstadoComprobante.ERROR, e1.estado());
        assertTrue(e1.mensaje().contains("inválido"));
        assertEquals(EstadoComprobante.ERROR, e2.estado());
        assertTrue(e2.mensaje().contains("401"));
        assertEquals(EstadoComprobante.ERROR, e3.estado());
        assertEquals(EstadoComprobante.ERROR, e4.estado());
        assertTrue(e4.mensaje().contains("Reintente"));
    }

    @Test
    void nubefactNotaDeCreditoReferenciaLaBoletaModificada() {
        respuestas.put("/api/v1/.*", p -> new Object[]{200, "{\"aceptada_por_sunat\":true}"});
        Comprobante b = boleta();
        Comprobante nota = new Comprobante(null, TipoComprobante.NOTA_CREDITO, "BBB1", 16, b.fechaEmision(), 7L, 3L, 2L,
                b.clienteDocumento(), b.clienteNombre(), b.clienteDireccion(), "Anulación",
                b.gravada(), b.igv(), b.total(), EstadoComprobante.PENDIENTE, null, null, null, null, null, 1L,
                LocalDateTime.now());
        new NubefactAdapter(base + "/api/v1/x", "T", "BBB1", "BBB1").emitir(nota, b);
        String json = peticionesA("/api/v1/.*").get(0).cuerpo();
        assertTrue(json.contains("\"tipo_de_comprobante\":3"));
        assertTrue(json.contains("\"documento_que_se_modifica_tipo\":2"));
        assertTrue(json.contains("\"documento_que_se_modifica_numero\":15"));
        assertTrue(json.contains("\"tipo_de_nota_de_credito\":1"));
    }

    @Test
    void nubefactRecuperaUnaBoletaQueYaHabiaRegistradoAntesDeUnCorte() {
        // Primer envío: Nubefact dice "ya existe" (código 23). El adaptador consulta y recupera sus datos.
        respuestas.put("/api/v1/.*", p -> p.cuerpo().contains("consultar_comprobante")
                ? new Object[]{200, "{\"aceptada_por_sunat\":true,\"enlace_del_pdf\":\"https://x/15.pdf\",\"codigo_hash\":\"H15\"}"}
                : new Object[]{200, "{\"errors\":\"Este documento ya existe en NubeFacT\",\"codigo\":23}"});
        ResultadoEmision r = new NubefactAdapter(base + "/api/v1/x", "T", "BBB1", "BBB1").emitir(boleta(), null);
        assertEquals(EstadoComprobante.ACEPTADO, r.estado());
        assertEquals("H15", r.hash());
        Peticion consulta = peticionesA("/api/v1/.*").get(1);
        assertTrue(consulta.cuerpo().contains("\"serie\":\"BBB1\"") && consulta.cuerpo().contains("\"numero\":15"));
    }
}
