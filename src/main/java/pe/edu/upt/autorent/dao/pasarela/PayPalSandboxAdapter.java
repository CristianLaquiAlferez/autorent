package pe.edu.upt.autorent.dao.pasarela;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import pe.edu.upt.autorent.modelo.MetodoPago;
import pe.edu.upt.autorent.modelo.OrdenEnLinea;
import pe.edu.upt.autorent.modelo.ResultadoCobro;
import pe.edu.upt.autorent.modelo.ResultadoReembolso;
import pe.edu.upt.autorent.repositorio.PayPalPort;

/**
 * Adaptador: PayPal en modo Sandbox (entorno de pruebas oficial de PayPal), con su API REST "Orders v2".
 *
 * Las credenciales (Client ID y Secret) se crean gratis en developer.paypal.com y se configuran con
 * variables de entorno; nunca se escriben en el código. Si no hay credenciales, configurado() devuelve
 * false y la web solo ofrece la pasarela de tarjeta de prueba.
 *
 * PayPal no cobra en soles a comercios peruanos, por eso el monto se convierte a dólares con el tipo
 * de cambio configurado. Al cobrar se comprueba que PayPal haya cobrado exactamente ese monto.
 */
@Component
public class PayPalSandboxAdapter implements PayPalPort {
    private static final Logger log = LoggerFactory.getLogger(PayPalSandboxAdapter.class);

    private final String clientId;
    private final String secreto;
    private final String moneda;
    private final BigDecimal tipoCambio;
    private final RestClient http;
    private String tokenAcceso;
    private Instant tokenVence = Instant.EPOCH;

    public PayPalSandboxAdapter(@Value("${autorent.paypal.client-id:}") String clientId,
                                @Value("${autorent.paypal.client-secret:}") String secreto,
                                @Value("${autorent.paypal.url:https://api-m.sandbox.paypal.com}") String url,
                                @Value("${autorent.paypal.moneda:USD}") String moneda,
                                @Value("${autorent.paypal.tipo-cambio:3.75}") BigDecimal tipoCambio) {
        this.clientId = clientId == null ? "" : clientId.trim();
        this.secreto = secreto == null ? "" : secreto.trim();
        this.moneda = moneda;
        this.tipoCambio = tipoCambio;
        SimpleClientHttpRequestFactory tiempos = new SimpleClientHttpRequestFactory();
        tiempos.setConnectTimeout(10_000);   // si PayPal no responde, no se queda colgada la página
        tiempos.setReadTimeout(30_000);
        this.http = RestClient.builder().baseUrl(url).requestFactory(tiempos).build();
    }

    @Override public boolean configurado() { return !clientId.isBlank() && !secreto.isBlank(); }
    @Override public String clientId() { return clientId; }
    @Override public String moneda() { return moneda; }

    @Override public BigDecimal convertir(BigDecimal montoSoles) {
        return montoSoles.divide(tipoCambio, 2, RoundingMode.HALF_UP);
    }

    /** POST /v2/checkout/orders: crea la orden con intención de cobro inmediato (CAPTURE). */
    @Override public OrdenEnLinea crearOrden(String referencia, BigDecimal montoSoles, String descripcion) {
        exigirConfiguracion();
        BigDecimal monto = convertir(montoSoles);
        Map<String, Object> cuerpo = Map.of(
                "intent", "CAPTURE",
                "purchase_units", List.of(Map.of(
                        "reference_id", referencia,
                        "description", recortar(descripcion, 120),
                        "amount", Map.of("currency_code", moneda, "value", monto.toPlainString()))));
        try {
            Map<?, ?> respuesta = http.post().uri("/v2/checkout/orders")
                    .header("Authorization", "Bearer " + token())
                    .contentType(MediaType.APPLICATION_JSON).body(cuerpo)
                    .retrieve().body(Map.class);
            return new OrdenEnLinea(String.valueOf(respuesta.get("id")), monto, moneda);
        } catch (RestClientException ex) {
            log.warn("PayPal no pudo crear la orden: {}", ex.getMessage());
            throw new IllegalArgumentException("No se pudo conectar con PayPal. Intente nuevamente o pague con tarjeta.");
        }
    }

    /** POST /v2/checkout/orders/{id}/capture: cobra la orden aprobada y devuelve el código de la captura. */
    @Override public ResultadoCobro capturar(String ordenId) {
        exigirConfiguracion();
        try {
            Map<?, ?> r = http.post().uri("/v2/checkout/orders/{id}/capture", ordenId)
                    .header("Authorization", "Bearer " + token())
                    .header("Prefer", "return=representation")
                    .contentType(MediaType.APPLICATION_JSON).body(Map.of())
                    .retrieve().body(Map.class);
            Map<?, ?> unidad = (Map<?, ?>) ((List<?>) r.get("purchase_units")).get(0);
            Map<?, ?> captura = (Map<?, ?>) ((List<?>) ((Map<?, ?>) unidad.get("payments")).get("captures")).get(0);
            Map<?, ?> monto = (Map<?, ?>) captura.get("amount");
            if (!"COMPLETED".equals(captura.get("status")))
                return ResultadoCobro.rechazado("PayPal no completó el cobro (estado " + captura.get("status") + ").");
            return new ResultadoCobro(true, String.valueOf(captura.get("id")), MetodoPago.PAYPAL, detalle(r),
                    "Pago aprobado por PayPal.", new BigDecimal(String.valueOf(monto.get("value"))),
                    String.valueOf(monto.get("currency_code")));
        } catch (RestClientResponseException ex) {
            log.warn("PayPal rechazó la captura {}: {}", ordenId, ex.getResponseBodyAsString());
            String cuerpo = ex.getResponseBodyAsString();
            if (cuerpo.contains("INSTRUMENT_DECLINED")) return ResultadoCobro.rechazado("PayPal rechazó el medio de pago. Elija otro.");
            if (cuerpo.contains("ORDER_ALREADY_CAPTURED")) return ResultadoCobro.rechazado("Esta orden de PayPal ya fue cobrada.");
            return ResultadoCobro.rechazado("PayPal no pudo procesar el pago.");
        } catch (RestClientException | ClassCastException | NullPointerException | IndexOutOfBoundsException ex) {
            log.warn("Respuesta inesperada de PayPal al capturar {}: {}", ordenId, ex.getMessage());
            return ResultadoCobro.rechazado("No se pudo confirmar el pago con PayPal.");
        }
    }

    /** POST /v2/payments/captures/{id}/refund: devuelve el dinero al comprador. */
    @Override public ResultadoReembolso reembolsar(String capturaId, BigDecimal montoSoles) {
        if (!configurado()) return new ResultadoReembolso(false, null, "PayPal no está configurado.");
        try {
            Map<?, ?> r = http.post().uri("/v2/payments/captures/{id}/refund", capturaId)
                    .header("Authorization", "Bearer " + token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("amount", Map.of("currency_code", moneda, "value", convertir(montoSoles).toPlainString())))
                    .retrieve().body(Map.class);
            String estado = String.valueOf(r.get("status"));
            boolean ok = "COMPLETED".equals(estado) || "PENDING".equals(estado);
            return new ResultadoReembolso(ok, String.valueOf(r.get("id")), "Reembolso PayPal: " + estado);
        } catch (RestClientException ex) {
            log.warn("PayPal no pudo reembolsar {}: {}", capturaId, ex.getMessage());
            return new ResultadoReembolso(false, null, "PayPal no pudo procesar el reembolso.");
        }
    }

    /** Token OAuth 2.0 (client credentials). Se reutiliza hasta un minuto antes de que venza. */
    private synchronized String token() {
        if (tokenAcceso != null && Instant.now().isBefore(tokenVence)) return tokenAcceso;
        String basico = Base64.getEncoder().encodeToString((clientId + ":" + secreto).getBytes(StandardCharsets.UTF_8));
        Map<?, ?> r = http.post().uri("/v1/oauth2/token")
                .header("Authorization", "Basic " + basico)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body("grant_type=client_credentials")
                .retrieve().body(Map.class);
        tokenAcceso = String.valueOf(r.get("access_token"));
        long segundos = Long.parseLong(String.valueOf(r.get("expires_in")));
        tokenVence = Instant.now().plusSeconds(Math.max(0, segundos - 60));
        return tokenAcceso;
    }

    /** Texto para mostrar: "VISA **** 1234" si pagó con tarjeta dentro de PayPal, o "PayPal correo" si usó su cuenta. */
    private static String detalle(Map<?, ?> respuesta) {
        Object fuente = respuesta.get("payment_source");
        if (fuente instanceof Map<?, ?> f && f.get("card") instanceof Map<?, ?> tarjeta)
            return recortar(tarjeta.get("brand") + " **** " + tarjeta.get("last_digits") + " (PayPal)", 120);
        Object comprador = respuesta.get("payer");
        if (comprador instanceof Map<?, ?> c && c.get("email_address") != null)
            return recortar("PayPal " + c.get("email_address"), 120);
        return "PayPal";
    }

    private void exigirConfiguracion() {
        if (!configurado()) throw new IllegalArgumentException("El pago con PayPal no está configurado.");
    }

    private static String recortar(String texto, int maximo) {
        if (texto == null) return "";
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }
}
