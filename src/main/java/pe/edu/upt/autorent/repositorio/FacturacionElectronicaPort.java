package pe.edu.upt.autorent.repositorio;

import pe.edu.upt.autorent.modelo.Comprobante;
import pe.edu.upt.autorent.modelo.ResultadoEmision;
import pe.edu.upt.autorent.modelo.TipoComprobante;

/**
 * Puerto de salida: sistema de facturación electrónica que envía comprobantes a SUNAT.
 * Adaptadores: Nubefact (operador de servicios electrónicos, en su modo demo) o la emisión
 * de prueba interna cuando Nubefact no está configurado.
 */
public interface FacturacionElectronicaPort {
    /** Nombre del proveedor (se guarda en cada comprobante). */
    String proveedor();
    /** true si el comprobante se envía de verdad a un sistema externo; false en modo de prueba interno. */
    boolean envioReal();
    /** Serie que corresponde al tipo de comprobante (por ejemplo B001 para boletas). */
    String serie(TipoComprobante tipo);
    /** Envía el comprobante. En una nota de crédito, "modificado" es la boleta que se anula. */
    ResultadoEmision emitir(Comprobante comprobante, Comprobante modificado);
}
