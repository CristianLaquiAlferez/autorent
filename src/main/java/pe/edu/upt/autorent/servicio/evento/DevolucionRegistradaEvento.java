package pe.edu.upt.autorent.servicio.evento;

/**
 * Evento de negocio: se registró una devolución de dinero.
 * Después de guardar se ejecuta el reembolso en la pasarela (si el pago fue en línea)
 * y se emite la nota de crédito que anula la boleta del pago.
 */
public record DevolucionRegistradaEvento(Long devolucionId) { }
