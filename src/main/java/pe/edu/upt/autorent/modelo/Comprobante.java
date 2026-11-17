package pe.edu.upt.autorent.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Comprobante electrónico (boleta o nota de crédito) de un pago.
 * Montos: total incluye IGV; gravada = total / 1.18; igv = total - gravada.
 * referenciaId: en una nota de crédito, la boleta que se anula.
 */
public record Comprobante(Long id, TipoComprobante tipo, String serie, int numero, LocalDate fechaEmision,
                          Long pagoId, Long reservaId, Long clienteId, String clienteDocumento, String clienteNombre,
                          String clienteDireccion, String descripcion, BigDecimal gravada, BigDecimal igv, BigDecimal total,
                          EstadoComprobante estado, String proveedor, String enlacePdf, String hash, String cadenaQr,
                          String mensaje, Long referenciaId, LocalDateTime creadoEn) {

    /** Serie y número como se imprimen: B001-00000012. */
    public String serieNumero() { return String.format("%s-%08d", serie, numero); }

    /** Copia con el resultado del envío al sistema de facturación. */
    public Comprobante conResultado(EstadoComprobante nuevoEstado, String proveedorUsado, String enlace, String codigoHash,
                                    String qr, String detalle) {
        return new Comprobante(id, tipo, serie, numero, fechaEmision, pagoId, reservaId, clienteId, clienteDocumento,
                clienteNombre, clienteDireccion, descripcion, gravada, igv, total, nuevoEstado, proveedorUsado, enlace,
                codigoHash, qr, detalle, referenciaId, creadoEn);
    }

    /** true si el comprobante ya existe para SUNAT o para el modo de prueba (no hace falta reenviarlo). */
    public boolean emitido() {
        return estado == EstadoComprobante.EMITIDO || estado == EstadoComprobante.ACEPTADO;
    }
}
