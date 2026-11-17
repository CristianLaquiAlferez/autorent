package pe.edu.upt.autorent.repositorio;

import java.math.BigDecimal;
import pe.edu.upt.autorent.modelo.OrdenEnLinea;
import pe.edu.upt.autorent.modelo.ResultadoCobro;
import pe.edu.upt.autorent.modelo.ResultadoReembolso;

/**
 * Puerto de salida: cobros con PayPal (cuenta PayPal o tarjeta Visa/Mastercard dentro de PayPal).
 * Flujo: 1) el servidor crea una orden, 2) el cliente la aprueba en la ventana de PayPal,
 * 3) el servidor la captura (cobra) y verifica el monto.
 */
public interface PayPalPort {
    /** true si hay credenciales de PayPal configuradas; si no, la web solo ofrece la pasarela de tarjeta. */
    boolean configurado();
    /** Client ID público que necesita el botón de PayPal en el navegador. */
    String clientId();
    /** Moneda en la que cobra PayPal (USD: PayPal no cobra en soles a comercios peruanos). */
    String moneda();
    /** Convierte soles a la moneda de PayPal con el tipo de cambio configurado. */
    BigDecimal convertir(BigDecimal montoSoles);
    /** Crea la orden de pago en PayPal. */
    OrdenEnLinea crearOrden(String referencia, BigDecimal montoSoles, String descripcion);
    /** Cobra la orden que el cliente aprobó. */
    ResultadoCobro capturar(String ordenId);
    /** Devuelve el dinero de una captura anterior. */
    ResultadoReembolso reembolsar(String capturaId, BigDecimal montoSoles);
}
