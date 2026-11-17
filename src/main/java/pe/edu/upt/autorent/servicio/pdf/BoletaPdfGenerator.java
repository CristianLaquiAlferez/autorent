package pe.edu.upt.autorent.servicio.pdf;

import java.util.Locale;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.edu.upt.autorent.modelo.Comprobante;
import pe.edu.upt.autorent.modelo.MontoEnLetras;
import pe.edu.upt.autorent.modelo.TipoComprobante;

/**
 * Representación impresa (PDF) de una boleta o nota de crédito electrónica.
 * Contiene lo que pide SUNAT: datos del emisor y del cliente, serie y número, detalle, operación gravada,
 * IGV, total, importe en letras, código hash y código QR.
 * Si el comprobante es de prueba (no se envió a SUNAT) se imprime una advertencia visible.
 */
@Component
public class BoletaPdfGenerator {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String NAVY = "0.08 0.17 0.29", TEXTO = "0.16 0.22 0.30", GRIS = "0.40 0.46 0.52";
    private static final String BORDE = "0.70 0.75 0.80", FONDO = "0.94 0.96 0.98", ROJO = "0.75 0.12 0.12", NEGRO = "0 0 0";

    private final String ruc;
    private final String razonSocial;
    private final String direccion;

    public BoletaPdfGenerator(@Value("${autorent.empresa.ruc:20123456786}") String ruc,
                              @Value("${autorent.empresa.razon-social:CAPLINA RENT A CAR S.A.C.}") String razonSocial,
                              @Value("${autorent.empresa.direccion:Av. Bolognesi 1250, Tacna - Tacna - Tacna}") String direccion) {
        this.ruc = ruc; this.razonSocial = razonSocial; this.direccion = direccion;
    }

    public byte[] generar(Comprobante c, Comprobante modificado, boolean esPrueba) {
        LienzoPdf p = new LienzoPdf();
        double m = 40;
        // Emisor
        p.texto(m, 60, razonSocial, 15, true, NAVY);
        p.texto(m, 78, "Alquiler de vehículos - Sistema AutoRent", 9.5, false, GRIS);
        p.texto(m, 92, direccion, 9.5, false, GRIS);
        // Recuadro del comprobante (arriba a la derecha), como en toda boleta peruana
        double bx = 345, bw = 210;
        p.marco(bx, 40, bw, 72, NAVY, 1.2);
        p.textoCentrado(bx + bw / 2, 60, "R.U.C. " + ruc, 11, true, NAVY);
        p.rectangulo(bx + 1, 67, bw - 2, 20, FONDO);
        p.textoCentrado(bx + bw / 2, 81, c.tipo().getNombre(), 9.5, true, NAVY);
        p.textoCentrado(bx + bw / 2, 103, c.serieNumero(), 12, true, NAVY);

        // Datos del cliente
        double y = 140;
        p.linea(m, y - 12, 555, y - 12, BORDE, 0.6);
        fila(p, m, y, "Cliente:", c.clienteNombre());
        fila(p, m, y + 16, "DNI:", c.clienteDocumento());
        fila(p, m, y + 32, "Dirección:", c.clienteDireccion() == null || c.clienteDireccion().isBlank() ? "-" : c.clienteDireccion());
        fila(p, 345, y, "Fecha de emisión:", c.fechaEmision().format(FECHA));
        fila(p, 345, y + 16, "Moneda:", "SOLES (PEN)");
        fila(p, 345, y + 32, "Reserva N.°:", String.valueOf(c.reservaId()));
        if (c.tipo() == TipoComprobante.NOTA_CREDITO && modificado != null) {
            fila(p, m, y + 48, "Documento que modifica:", "Boleta " + modificado.serieNumero() + "  -  Motivo: Anulación de la operación");
        }

        // Detalle
        y = 215;
        p.rectangulo(m, y, 515, 20, NAVY);
        p.texto(m + 8, y + 14, "CANT.", 8.5, true, "1 1 1");
        p.texto(m + 50, y + 14, "UNIDAD", 8.5, true, "1 1 1");
        p.texto(m + 100, y + 14, "DESCRIPCIÓN", 8.5, true, "1 1 1");
        p.textoDerecha(m + 435, y + 14, "P. UNITARIO", 8.5, true, "1 1 1");
        p.textoDerecha(m + 507, y + 14, "IMPORTE", 8.5, true, "1 1 1");
        List<String> lineas = partir(c.descripcion(), 300, 9);
        double alto = Math.max(26, 12 * lineas.size() + 12);
        p.marco(m, y + 20, 515, alto, BORDE, 0.6);
        p.texto(m + 14, y + 36, "1", 9, false, TEXTO);
        p.texto(m + 50, y + 36, "ZZ", 9, false, TEXTO);
        for (int i = 0; i < lineas.size(); i++) p.texto(m + 100, y + 36 + 12 * i, lineas.get(i), 9, false, TEXTO);
        p.textoDerecha(m + 435, y + 36, soles(c.total()), 9, false, TEXTO);
        p.textoDerecha(m + 507, y + 36, soles(c.total()), 9, false, TEXTO);

        // Totales
        y = y + 20 + alto + 20;
        total(p, y, "Op. gravada", c.gravada(), false);
        total(p, y + 16, "IGV (18 %)", c.igv(), false);
        total(p, y + 34, "IMPORTE TOTAL", c.total(), true);
        p.texto(m, y + 4, "SON: " + MontoEnLetras.soles(c.total()), 9, true, TEXTO);

        // QR y hash
        double qy = y + 60;
        dibujarQr(p, c.cadenaQr(), m, qy, 96);
        p.texto(m + 112, qy + 14, "Representación impresa de la " + c.tipo().getNombre().toLowerCase() + ".", 8.5, false, GRIS);
        p.texto(m + 112, qy + 28, "Código hash: " + (c.hash() == null ? "-" : c.hash()), 8.5, false, GRIS);
        p.texto(m + 112, qy + 42, "Emitido mediante: " + (esPrueba ? "modo de prueba AutoRent" : c.proveedor()), 8.5, false, GRIS);
        if (c.mensaje() != null) p.texto(m + 112, qy + 56, recortar(c.mensaje(), 95), 8.5, false, GRIS);

        if (esPrueba) {
            double ay = qy + 120;
            p.rectangulo(m, ay, 515, 34, "0.99 0.93 0.93");
            p.marco(m, ay, 515, 34, ROJO, 0.8);
            p.textoCentrado(297.5, ay + 15, "DOCUMENTO DE PRUEBA - SIN VALOR TRIBUTARIO", 11, true, ROJO);
            p.textoCentrado(297.5, ay + 28, "Generado para fines académicos. No fue enviado a SUNAT.", 8.5, false, ROJO);
        }
        p.textoCentrado(297.5, 815, "AutoRent - " + razonSocial + " - Tacna, Perú", 8, false, GRIS);
        return p.pdf();
    }

    private static void fila(LienzoPdf p, double x, double y, String etiqueta, String valor) {
        p.texto(x, y, etiqueta, 9, true, TEXTO);
        p.texto(x + LienzoPdf.ancho(etiqueta, 9) * 1.12 + 6, y, recortar(valor, 70), 9, false, TEXTO);
    }

    private static void total(LienzoPdf p, double y, String etiqueta, BigDecimal monto, boolean destacado) {
        p.textoDerecha(470, y, etiqueta, destacado ? 10 : 9, destacado, destacado ? NAVY : TEXTO);
        p.textoDerecha(555, y, "S/ " + soles(monto), destacado ? 10 : 9, destacado, destacado ? NAVY : TEXTO);
    }

    /** Dibuja el código QR cuadradito por cuadradito (sin imágenes) a partir de la matriz de ZXing. */
    private static void dibujarQr(LienzoPdf p, String contenido, double x, double y, double lado) {
        if (contenido == null || contenido.isBlank()) return;
        try {
            BitMatrix matriz = new QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 0));
            double modulo = lado / matriz.getWidth();
            for (int fila = 0; fila < matriz.getHeight(); fila++) {
                for (int col = 0; col < matriz.getWidth(); col++) {
                    if (matriz.get(col, fila)) p.rectangulo(x + col * modulo, y + fila * modulo, modulo + 0.05, modulo + 0.05, NEGRO);
                }
            }
        } catch (WriterException ex) {
            p.texto(x, y + 10, "QR no disponible", 8, false, GRIS);
        }
    }

    private static List<String> partir(String texto, double anchoMaximo, double tamano) {
        List<String> lineas = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        for (String palabra : texto.split(" ")) {
            String prueba = actual.isEmpty() ? palabra : actual + " " + palabra;
            if (LienzoPdf.ancho(prueba, tamano) > anchoMaximo && !actual.isEmpty()) {
                lineas.add(actual.toString());
                actual = new StringBuilder(palabra);
            } else {
                actual = new StringBuilder(prueba);
            }
        }
        if (!actual.isEmpty()) lineas.add(actual.toString());
        return lineas;
    }

    private static String soles(BigDecimal monto) { return String.format(Locale.ROOT, "%,.2f", monto).replace(',', ' '); }

    private static String recortar(String texto, int maximo) {
        return texto == null || texto.length() <= maximo ? texto : texto.substring(0, maximo - 3) + "...";
    }
}
