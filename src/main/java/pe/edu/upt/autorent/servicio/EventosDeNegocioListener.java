package pe.edu.upt.autorent.servicio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pe.edu.upt.autorent.servicio.contrato.ComprobanteUseCase;
import pe.edu.upt.autorent.servicio.contrato.PagoEnLineaUseCase;
import pe.edu.upt.autorent.servicio.evento.DevolucionRegistradaEvento;
import pe.edu.upt.autorent.servicio.evento.PagoConfirmadoEvento;

/**
 * Reacciona a los eventos de negocio DESPUÉS de que la transacción se guardó (AFTER_COMMIT).
 *
 * ¿Por qué después? Porque emitir la boleta o reembolsar en PayPal depende de internet. Si fallaran,
 * no deben deshacer el pago ya registrado: el comprobante queda en ERROR y se puede reintentar.
 */
@Component
public class EventosDeNegocioListener {
    private static final Logger log = LoggerFactory.getLogger(EventosDeNegocioListener.class);

    private final ComprobanteUseCase comprobantes;
    private final PagoEnLineaUseCase pagosEnLinea;

    public EventosDeNegocioListener(ComprobanteUseCase comprobantes, PagoEnLineaUseCase pagosEnLinea) {
        this.comprobantes = comprobantes; this.pagosEnLinea = pagosEnLinea;
    }

    /** Pago confirmado -> boleta electrónica. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alConfirmarPago(PagoConfirmadoEvento evento) {
        try {
            comprobantes.emitirBoletaDePago(evento.pagoId());
        } catch (RuntimeException ex) {
            log.warn("No se pudo emitir la boleta del pago {}: {}", evento.pagoId(), ex.getMessage());
        }
    }

    /** Devolución registrada -> reembolso en la pasarela (si fue en línea) y nota de crédito. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alRegistrarDevolucion(DevolucionRegistradaEvento evento) {
        try {
            pagosEnLinea.procesarReembolsoEnLinea(evento.devolucionId());
        } catch (RuntimeException ex) {
            log.warn("No se pudo reembolsar automáticamente la devolución {}: {}", evento.devolucionId(), ex.getMessage());
        }
        try {
            comprobantes.emitirNotaCreditoDeDevolucion(evento.devolucionId());
        } catch (RuntimeException ex) {
            log.warn("No se emitió nota de crédito para la devolución {}: {}", evento.devolucionId(), ex.getMessage());
        }
    }
}
