package pe.edu.upt.autorent.modelo;

/**
 * Medios de pago aceptados por AutoRent.
 * EFECTIVO y YAPE (con código de operación) los verifica el personal.
 * TARJETA, PAYPAL y YAPE_EN_LINEA (Yape con código de aprobación) los confirma la pasarela de pagos en línea.
 */
public enum MetodoPago {
    EFECTIVO("Efectivo"), YAPE("Yape"), TARJETA("Tarjeta Visa/Mastercard"), PAYPAL("PayPal"),
    YAPE_EN_LINEA("Yape con código de aprobación");

    private final String etiqueta;

    MetodoPago(String etiqueta) { this.etiqueta = etiqueta; }

    /** Texto para mostrar en pantallas, PDF y boletas. */
    public String getEtiqueta() { return etiqueta; }

    /** Pagos procesados por una pasarela (no los revisa una persona). */
    public boolean esEnLinea() { return this == TARJETA || this == PAYPAL || this == YAPE_EN_LINEA; }
}
