package pe.edu.upt.autorent.dao.facturacion;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import pe.edu.upt.autorent.modelo.Comprobante;
import pe.edu.upt.autorent.modelo.EstadoComprobante;
import pe.edu.upt.autorent.modelo.ResultadoEmision;
import pe.edu.upt.autorent.modelo.TipoComprobante;
import pe.edu.upt.autorent.repositorio.FacturacionElectronicaPort;

/**
 * Adaptador: Nubefact, operador de facturación electrónica que envía los comprobantes a SUNAT.
 *
 * Se usa la API "JSON" de Nubefact: una RUTA y un TOKEN propios de cada cuenta (se obtienen gratis en
 * su modo DEMO). Cada solicitud es un POST a la RUTA con el comprobante en formato JSON y la cabecera
 * Authorization: Token token="...". Nubefact responde con el enlace del PDF, el código hash, la cadena
 * del código QR y si SUNAT aceptó el comprobante.
 *
 * Los montos ya incluyen IGV: cada línea se envía con valor_unitario (sin IGV) y precio_unitario (con IGV).
 */
public class NubefactAdapter implements FacturacionElectronicaPort {
    private static final Logger log = LoggerFactory.getLogger(NubefactAdapter.class);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final String ruta;
    private final String token;
    private final String serieBoleta;
    private final String serieNotaCredito;
    private static final String DOCUMENTO_YA_EXISTE = "23";
    private final RestClient http;

    public NubefactAdapter(String ruta, String token, String serieBoleta, String serieNotaCredito) {
        this.ruta = ruta.trim(); this.token = token.trim();
        this.serieBoleta = serieBoleta; this.serieNotaCredito = serieNotaCredito;
        SimpleClientHttpRequestFactory tiempos = new SimpleClientHttpRequestFactory();
        tiempos.setConnectTimeout(10_000);
        tiempos.setReadTimeout(40_000);
        this.http = RestClient.builder().requestFactory(tiempos).build();
    }

    @Override public String proveedor() { return "NUBEFACT"; }
    @Override public boolean envioReal() { return true; }
    @Override public String serie(TipoComprobante tipo) { return tipo == TipoComprobante.BOLETA ? serieBoleta : serieNotaCredito; }

    @Override public ResultadoEmision emitir(Comprobante c, Comprobante modificado) {
        Map<String, Object> json = armarJson(c, modificado);
        try {
            Map<?, ?> r = http.post().uri(ruta)
                    .header("Authorization", "Token token=\"" + token + "\"")
                    .contentType(MediaType.APPLICATION_JSON).body(json)
                    .retrieve().body(Map.class);
            if (r == null) return new ResultadoEmision(EstadoComprobante.ERROR, null, null, null, "Nubefact no respondió.");
            if (r.get("errors") != null) {
                // Código 23 = "el documento ya existe": pasa cuando Nubefact sí registró la boleta pero la respuesta
                // se perdió (corte de internet). En vez de quedar en ERROR para siempre, se consulta la ya emitida.
                if (DOCUMENTO_YA_EXISTE.equals(texto(r.get("codigo")))) return consultar(c);
                return new ResultadoEmision(EstadoComprobante.ERROR, null, null, null, "Nubefact: " + r.get("errors"));
            }
            return interpretar(r);
        } catch (RestClientResponseException ex) {
            String respuesta = ex.getResponseBodyAsString();
            if (respuesta.contains("\"codigo\":" + DOCUMENTO_YA_EXISTE) || respuesta.contains("\"codigo\":\"" + DOCUMENTO_YA_EXISTE + "\""))
                return consultar(c);
            log.warn("Nubefact rechazó {}: {}", c.serieNumero(), respuesta);
            return new ResultadoEmision(EstadoComprobante.ERROR, null, null, null,
                    "Nubefact respondió " + ex.getStatusCode().value() + ": " + respuesta);
        } catch (RestClientException ex) {
            log.warn("No se pudo conectar con Nubefact: {}", ex.getMessage());
            return new ResultadoEmision(EstadoComprobante.ERROR, null, null, null, "Sin conexión con Nubefact. Reintente más tarde.");
        }
    }

    /** Consulta en Nubefact un comprobante ya enviado (operación consultar_comprobante). */
    private ResultadoEmision consultar(Comprobante c) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("operacion", "consultar_comprobante");
        json.put("tipo_de_comprobante", c.tipo() == TipoComprobante.BOLETA ? 2 : 3);
        json.put("serie", c.serie());
        json.put("numero", c.numero());
        try {
            Map<?, ?> r = http.post().uri(ruta)
                    .header("Authorization", "Token token=\"" + token + "\"")
                    .contentType(MediaType.APPLICATION_JSON).body(json)
                    .retrieve().body(Map.class);
            if (r == null || r.get("errors") != null)
                return new ResultadoEmision(EstadoComprobante.ERROR, null, null, null,
                        "El comprobante ya existe en Nubefact pero no se pudo consultar: " + (r == null ? "sin respuesta" : r.get("errors")));
            return interpretar(r);
        } catch (RestClientException ex) {
            return new ResultadoEmision(EstadoComprobante.ERROR, null, null, null, "Sin conexión con Nubefact. Reintente más tarde.");
        }
    }

    /** Convierte la respuesta de Nubefact (emisión o consulta) en el resultado de la emisión. */
    private static ResultadoEmision interpretar(Map<?, ?> r) {
        boolean aceptada = Boolean.TRUE.equals(r.get("aceptada_por_sunat"));
        String descripcionSunat = r.get("sunat_description") == null ? "Enviado a SUNAT; respuesta pendiente."
                : String.valueOf(r.get("sunat_description"));
        return new ResultadoEmision(aceptada ? EstadoComprobante.ACEPTADO : EstadoComprobante.EMITIDO,
                texto(r.get("enlace_del_pdf")), texto(r.get("codigo_hash")), texto(r.get("cadena_para_codigo_qr")),
                descripcionSunat);
    }

    Map<String, Object> armarJson(Comprobante c, Comprobante modificado) {
        Map<String, Object> j = new LinkedHashMap<>();
        j.put("operacion", "generar_comprobante");
        j.put("tipo_de_comprobante", c.tipo() == TipoComprobante.BOLETA ? 2 : 3);
        j.put("serie", c.serie());
        j.put("numero", c.numero());
        j.put("sunat_transaction", 1);                    // venta interna
        j.put("cliente_tipo_de_documento", 1);            // DNI
        j.put("cliente_numero_de_documento", c.clienteDocumento());
        j.put("cliente_denominacion", c.clienteNombre());
        j.put("cliente_direccion", c.clienteDireccion() == null ? "" : c.clienteDireccion());
        j.put("cliente_email", "");
        j.put("fecha_de_emision", c.fechaEmision().format(FECHA));
        j.put("moneda", 1);                               // soles
        j.put("porcentaje_de_igv", new BigDecimal("18.00"));
        j.put("total_gravada", c.gravada());
        j.put("total_igv", c.igv());
        j.put("total", c.total());
        j.put("enviar_automaticamente_a_la_sunat", true);
        j.put("enviar_automaticamente_al_cliente", false);
        j.put("observaciones", "Reserva N.° " + c.reservaId());
        if (c.tipo() == TipoComprobante.NOTA_CREDITO && modificado != null) {
            j.put("documento_que_se_modifica_tipo", 2);   // boleta
            j.put("documento_que_se_modifica_serie", modificado.serie());
            j.put("documento_que_se_modifica_numero", modificado.numero());
            j.put("tipo_de_nota_de_credito", 1);          // anulación de la operación
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("unidad_de_medida", "ZZ");               // servicio
        item.put("codigo", "ALQ-" + c.reservaId());
        item.put("descripcion", c.descripcion());
        item.put("cantidad", 1);
        item.put("valor_unitario", c.gravada());
        item.put("precio_unitario", c.total());
        item.put("subtotal", c.gravada());
        item.put("tipo_de_igv", 1);                       // gravado - operación onerosa
        item.put("igv", c.igv());
        item.put("total", c.total());
        item.put("anticipo_regularizacion", false);
        j.put("items", List.of(item));
        return j;
    }

    private static String texto(Object valor) { return valor == null ? null : String.valueOf(valor); }
}
