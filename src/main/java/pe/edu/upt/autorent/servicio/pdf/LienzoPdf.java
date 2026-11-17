package pe.edu.upt.autorent.servicio.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Lienzo PDF de una sola hoja A4 vertical (595 x 842 puntos), escrito sin librerías externas.
 * Ofrece lo justo para la boleta: texto, rectángulos, líneas y cuadrados (para dibujar el código QR).
 * Usa el mismo formato que ReportePdfGenerator: fuentes Helvetica y codificación Windows-1252 (con tildes).
 * El origen (0,0) del PDF está abajo a la izquierda; aquí "y" se mide desde ARRIBA para que sea más natural.
 */
final class LienzoPdf {
    static final double ANCHO = 595, ALTO = 842;
    private static final Charset CP1252 = Charset.forName("windows-1252");
    private final StringBuilder contenido = new StringBuilder();

    /** Escribe texto; negrita usa Helvetica-Bold. */
    LienzoPdf texto(double x, double yDesdeArriba, String texto, double tamano, boolean negrita, String colorRgb) {
        contenido.append("BT ").append(colorRgb).append(" rg /").append(negrita ? "F2 " : "F1 ").append(f(tamano))
                .append(" Tf 1 0 0 1 ").append(f(x)).append(' ').append(f(ALTO - yDesdeArriba))
                .append(" Tm (").append(escapar(texto)).append(") Tj ET\n");
        return this;
    }

    /** Texto alineado a la derecha terminando en x. */
    LienzoPdf textoDerecha(double xFinal, double yDesdeArriba, String texto, double tamano, boolean negrita, String colorRgb) {
        return texto(xFinal - ancho(texto, tamano), yDesdeArriba, texto, tamano, negrita, colorRgb);
    }

    /** Texto centrado en x. */
    LienzoPdf textoCentrado(double xCentro, double yDesdeArriba, String texto, double tamano, boolean negrita, String colorRgb) {
        return texto(xCentro - ancho(texto, tamano) / 2, yDesdeArriba, texto, tamano, negrita, colorRgb);
    }

    /** Rectángulo relleno. (x, y) es la esquina superior izquierda. */
    LienzoPdf rectangulo(double x, double yDesdeArriba, double ancho, double alto, String colorRgb) {
        contenido.append(colorRgb).append(" rg ").append(f(x)).append(' ').append(f(ALTO - yDesdeArriba - alto)).append(' ')
                .append(f(ancho)).append(' ').append(f(alto)).append(" re f\n");
        return this;
    }

    /** Borde de rectángulo (sin relleno). */
    LienzoPdf marco(double x, double yDesdeArriba, double ancho, double alto, String colorRgb, double grosor) {
        contenido.append(colorRgb).append(" RG ").append(f(grosor)).append(" w ").append(f(x)).append(' ')
                .append(f(ALTO - yDesdeArriba - alto)).append(' ').append(f(ancho)).append(' ').append(f(alto)).append(" re S\n");
        return this;
    }

    /** Línea recta. */
    LienzoPdf linea(double x1, double y1, double x2, double y2, String colorRgb, double grosor) {
        contenido.append(colorRgb).append(" RG ").append(f(grosor)).append(" w ").append(f(x1)).append(' ').append(f(ALTO - y1))
                .append(" m ").append(f(x2)).append(' ').append(f(ALTO - y2)).append(" l S\n");
        return this;
    }

    /** Ancho aproximado de un texto en Helvetica (sirve para alinear y partir líneas). */
    static double ancho(String texto, double tamano) {
        double unidades = 0;
        for (char c : texto.toCharArray()) {
            if ("il.,:;|!'I ".indexOf(c) >= 0) unidades += 0.28;
            else if (Character.isUpperCase(c) || c == 'm' || c == 'w') unidades += 0.68;
            else unidades += 0.53;
        }
        return unidades * tamano;
    }

    /** Genera los bytes del PDF: catálogo, páginas, 2 fuentes, la hoja y la tabla xref. */
    byte[] pdf() {
        byte[] flujo = contenido.toString().getBytes(CP1252);
        String[] objetos = {
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [5 0 R] /Count 1 >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + f(ANCHO) + " " + f(ALTO) + "] "
                        + "/Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents 6 0 R >>",
                null};
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            out.write("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));
            long[] posiciones = new long[objetos.length + 1];
            for (int i = 0; i < objetos.length; i++) {
                posiciones[i + 1] = out.size();
                out.write(((i + 1) + " 0 obj\n").getBytes(StandardCharsets.US_ASCII));
                if (objetos[i] != null) {
                    out.write(objetos[i].getBytes(StandardCharsets.US_ASCII));
                } else {
                    out.write(("<< /Length " + flujo.length + " >>\nstream\n").getBytes(StandardCharsets.US_ASCII));
                    out.write(flujo);
                    out.write("\nendstream".getBytes(StandardCharsets.US_ASCII));
                }
                out.write("\nendobj\n".getBytes(StandardCharsets.US_ASCII));
            }
            long inicioXref = out.size();
            StringBuilder xref = new StringBuilder("xref\n0 " + (objetos.length + 1) + "\n0000000000 65535 f \n");
            for (int i = 1; i <= objetos.length; i++) xref.append(String.format("%010d 00000 n \n", posiciones[i]));
            xref.append("trailer\n<< /Size ").append(objetos.length + 1).append(" /Root 1 0 R >>\nstartxref\n")
                    .append(inicioXref).append("\n%%EOF\n");
            out.write(xref.toString().getBytes(StandardCharsets.US_ASCII));
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static String f(double v) { return String.format(Locale.ROOT, "%.2f", v); }

    /** Escapa paréntesis y barra invertida; reemplaza lo que no existe en Windows-1252. */
    private static String escapar(String texto) {
        StringBuilder sb = new StringBuilder();
        for (char c : (texto == null ? "" : texto).toCharArray()) {
            if (c == '(' || c == ')' || c == '\\') sb.append('\\').append(c);
            else if (CP1252.newEncoder().canEncode(c)) sb.append(c);
            else sb.append('?');
        }
        return sb.toString();
    }
}
