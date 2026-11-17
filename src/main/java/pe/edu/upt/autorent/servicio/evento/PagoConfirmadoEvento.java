package pe.edu.upt.autorent.servicio.evento;

/**
 * Evento de negocio: un pago quedó CONFIRMADO (dinero recibido).
 * Después de guardar en la base de datos se emite la boleta electrónica de ese pago.
 */
public record PagoConfirmadoEvento(Long pagoId) { }
