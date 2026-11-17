package pe.edu.upt.autorent.modelo;

import java.math.BigDecimal;

/**
 * Respuesta de la pasarela al intentar cobrar.
 * codigoTransaccion: identificador del cobro en la pasarela (sirve para reembolsar).
 * detalle: lo que se muestra al usuario, por ejemplo "VISA **** 1111" o "PayPal: comprador@correo".
 * montoCobrado y moneda: lo que realmente cobró la pasarela (PayPal cobra en dólares).
 */
public record ResultadoCobro(boolean aprobado, String codigoTransaccion, MetodoPago metodo, String detalle,
                             String mensaje, BigDecimal montoCobrado, String moneda) {

    public static ResultadoCobro rechazado(String mensaje) {
        return new ResultadoCobro(false, null, null, null, mensaje, null, null);
    }
}
