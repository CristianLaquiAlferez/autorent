package pe.edu.upt.autorent.dao.facturacion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import pe.edu.upt.autorent.modelo.Comprobante;
import pe.edu.upt.autorent.modelo.EstadoComprobante;
import pe.edu.upt.autorent.modelo.ResultadoEmision;
import pe.edu.upt.autorent.modelo.TipoComprobante;
import pe.edu.upt.autorent.repositorio.FacturacionElectronicaPort;

/**
 * Adaptador de respaldo: emisión de comprobantes en modo de PRUEBA (sin conexión con SUNAT).
 *
 * Arma el comprobante con la misma estructura de SUNAT (serie, correlativo, IGV, código hash y la
 * cadena del código QR) para poder mostrarlo e imprimirlo, pero NO tiene validez tributaria:
 * el PDF lo indica con una marca de agua. Se usa cuando Nubefact no está configurado.
 */
public class FacturacionSimuladaAdapter implements FacturacionElectronicaPort {
    private final String ruc;
    private final String serieBoleta;
    private final String serieNotaCredito;

    public FacturacionSimuladaAdapter(String ruc, String serieBoleta, String serieNotaCredito) {
        this.ruc = ruc; this.serieBoleta = serieBoleta; this.serieNotaCredito = serieNotaCredito;
    }

    @Override public String proveedor() { return "PRUEBA"; }
    @Override public boolean envioReal() { return false; }
    @Override public String serie(TipoComprobante tipo) { return tipo == TipoComprobante.BOLETA ? serieBoleta : serieNotaCredito; }

    @Override public ResultadoEmision emitir(Comprobante c, Comprobante modificado) {
        String hash = resumen(c);
        // Cadena del QR según el formato de SUNAT: RUC|TIPO|SERIE|NÚMERO|IGV|TOTAL|FECHA|TIPO DOC|N.° DOC|HASH|
        String qr = String.join("|", ruc, c.tipo().getCodigoSunat(), c.serie(), String.valueOf(c.numero()),
                c.igv().toPlainString(), c.total().toPlainString(), c.fechaEmision().toString(), "1",
                c.clienteDocumento(), hash) + "|";
        return new ResultadoEmision(EstadoComprobante.EMITIDO, null, hash, qr,
                "Comprobante de prueba generado por AutoRent. No fue enviado a SUNAT.");
    }

    /** Código hash: resumen SHA-256 de los datos del comprobante (en la emisión real lo calcula la firma digital). */
    private static String resumen(Comprobante c) {
        try {
            String datos = c.serieNumero() + c.fechaEmision() + c.clienteDocumento() + c.total().toPlainString();
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(datos.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest).substring(0, 28);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
