package pe.edu.upt.autorent.servicio.contrato;

import java.util.List;
import pe.edu.upt.autorent.modelo.Comprobante;

/** Puerto de entrada: emisión y consulta de boletas y notas de crédito electrónicas. */
public interface ComprobanteUseCase {
    /** Emite la boleta de un pago confirmado (si ya existe, la devuelve sin duplicarla). */
    Comprobante emitirBoletaDePago(Long pagoId);
    /** Emite la nota de crédito que anula la boleta del pago devuelto. */
    Comprobante emitirNotaCreditoDeDevolucion(Long devolucionId);
    /** Vuelve a enviar un comprobante que quedó con error de envío (mismo número, sin saltos). */
    Comprobante reintentar(Long comprobanteId);
    /** Todos los comprobantes (personal). */
    List<Comprobante> listarTodos();
    /** Comprobantes del cliente que inició sesión. */
    List<Comprobante> listarDeCliente(String username);
    /** PDF del comprobante; el cliente solo puede descargar los suyos. */
    ArchivoComprobante obtenerPdf(Long comprobanteId, String username);

    /** PDF generado por AutoRent, o el enlace al PDF oficial de Nubefact. */
    record ArchivoComprobante(String nombre, byte[] contenido, String enlaceExterno) { }
}
