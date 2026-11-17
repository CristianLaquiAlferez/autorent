package pe.edu.upt.autorent.modelo;

/**
 * Medios de pago aceptados por AutoRent.
 * EFECTIVO y YAPE los verifica el personal; TARJETA y PAYPAL los confirma la pasarela de pagos en línea.
 */
public enum MetodoPago {
    EFECTIVO("Efectivo"), YAPE("Yape"), TARJETA("Tarjeta Visa/Mastercard"), PAYPAL("PayPal");

    private final String etiqueta;

    MetodoPago(String etiqueta) { this.etiqueta = etiqueta; }

    /** Texto para mostrar en pantallas, PDF y boletas. */
    public String getEtiqueta() { return etiqueta; }

    /** Pagos procesados por una pasarela (no los revisa una persona). */
    public boolean esEnLinea() { return this == TARJETA || this == PAYPAL; }
}
