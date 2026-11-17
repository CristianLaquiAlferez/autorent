package pe.edu.upt.autorent.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * Modelo de negocio de una devolución de dinero (reembolso) al cliente.
 * codigoOperacion: evidencia de la devolución (código del Yape enviado o del reembolso en la pasarela).
 * Sin ese código no se puede marcar como completada: así queda la prueba de que el dinero salió.
 */
public record Devolucion(Long id, Long reservaId, Long pagoId, BigDecimal monto,
                         MetodoPago metodo, String telefonoDestino,
                         EstadoDevolucion estado, LocalDateTime registradoEn,
                         LocalDateTime completadoEn, String completadoPor, String codigoOperacion) {

    /** Copia de la devolución marcada como COMPLETADA con su evidencia. */
    public Devolucion completada(LocalDateTime fecha, String usuario, String codigo) {
        return new Devolucion(id, reservaId, pagoId, monto, metodo, telefonoDestino, EstadoDevolucion.COMPLETADA,
                registradoEn, fecha, usuario, codigo);
    }
}
