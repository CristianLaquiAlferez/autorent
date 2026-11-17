package pe.edu.upt.autorent.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * Modelo de negocio de un pago de anticipo o saldo.
 * codigoOperacion: código de Yape o identificador de la transacción en la pasarela.
 * detalle: datos visibles del medio en línea (por ejemplo "VISA **** 1111"); nunca el número completo de la tarjeta.
 *
 * Trazabilidad: cada pago guarda QUIÉN lo registró y CUÁNDO (registradoPor / registradoEn)
 * y, por separado, QUIÉN lo confirmó o rechazó y CUÁNDO (procesadoPor / procesadoEn).
 * observacion: motivo del rechazo o comentario del personal.
 */
public record Pago(Long id, Long reservaId, BigDecimal monto, MetodoPago metodo,
                   TipoPago tipo, EstadoPago estado, String telefonoOrigen,
                   String codigoOperacion, LocalDateTime registradoEn,
                   LocalDateTime procesadoEn, String registradoPor, String detalle,
                   String procesadoPor, String observacion) {

    /** Pago nuevo, todavía sin procesar. */
    public static Pago nuevo(Long reservaId, BigDecimal monto, MetodoPago metodo, TipoPago tipo, EstadoPago estado,
                             String telefonoOrigen, String codigoOperacion, LocalDateTime registradoEn, String registradoPor) {
        return new Pago(null, reservaId, monto, metodo, tipo, estado, telefonoOrigen, codigoOperacion,
                registradoEn, null, registradoPor, null, null, null);
    }

    /**
     * Copia del pago confirmado o rechazado (los records son inmutables).
     * No toca registradoPor: así se sabe quién registró el pago y quién lo procesó.
     */
    public Pago procesado(EstadoPago nuevo, LocalDateTime fecha, String usuario, String motivo) {
        return new Pago(id, reservaId, monto, metodo, tipo, nuevo, telefonoOrigen, codigoOperacion,
                registradoEn, fecha, registradoPor, detalle, usuario, motivo);
    }

    /** Copia del pago con los datos que devolvió la pasarela (método real, código y detalle). */
    public Pago conDatosPasarela(MetodoPago metodoReal, String codigo, String detallePasarela) {
        return new Pago(id, reservaId, monto, metodoReal, tipo, estado, telefonoOrigen, codigo,
                registradoEn, procesadoEn, registradoPor, detallePasarela, procesadoPor, observacion);
    }

    /** Texto corto para la bitácora, por ejemplo "Anticipo S/ 120.00 - Yape (op. ABC123)". */
    public String resumen() {
        String base = (tipo == TipoPago.ANTICIPO ? "Anticipo" : "Saldo") + " S/ " + monto + " - " + metodo.getEtiqueta();
        if (detalle != null && !detalle.isBlank()) base += " " + detalle;
        if (codigoOperacion != null && !codigoOperacion.isBlank()) base += " (op. " + codigoOperacion + ")";
        return base;
    }
}
