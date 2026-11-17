package pe.edu.upt.autorent.servicio.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import pe.edu.upt.autorent.modelo.*;
/**
 * Genera el reporte en PDF sin usar librerías externas (ni iText ni PDFBox).
 *
 * Un PDF es un archivo de texto con "objetos" numerados: catálogo, lista de páginas, fuentes y,
 * por cada página, un bloque de dibujo con instrucciones simples (rectángulo, texto, línea).
 * Esta clase arma ese texto a mano:
 *   1) Pagina guarda las instrucciones de dibujo de una hoja.
 *   2) Documento decide qué va en cada hoja (encabezado, tarjetas de resumen, tablas) y salta
 *      de hoja cuando ya no hay espacio.
 *   3) Documento.pdf() escribe todos los objetos y la tabla "xref" con la posición de cada uno.
 * Medidas en puntos (1 punto = 1/72 de pulgada). Hoja A4 horizontal: 842 x 595.
 * El origen (0,0) del PDF está en la esquina inferior izquierda, por eso "y" va de arriba hacia abajo restando.
 */

public final class ReportePdfGenerator {
    private ReportePdfGenerator() { }
    // Tamaño de la hoja y margen, en puntos.
    private static final double ANCHO = 842, ALTO = 595, MARGEN = 34;
    // Codificación Windows-1252: la que usan las fuentes estándar del PDF; admite tildes y la ñ.
    private static final Charset CP1252 = Charset.forName("windows-1252");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    // Colores en formato RGB del PDF (cada componente de 0 a 1).
    private static final String NAVY = "0.08 0.17 0.29", AZUL = "0.11 0.36 0.57";
    private static final String TEXTO = "0.16 0.22 0.30", MUTED = "0.40 0.46 0.52";
    private static final String BLANCO = "1 1 1", FONDO = "0.94 0.96 0.98";

    /**
     * Punto de entrada: arma el PDF con el resumen y tres tablas (pagos, devoluciones y reservas).
     * Cada tabla recibe sus encabezados, el ancho relativo ("peso") de cada columna y las filas como texto.
     */
    public static byte[] generar(ResumenReporte resumen) {
        if (resumen == null) throw new IllegalArgumentException("No hay datos para generar el reporte.");
        Documento doc = new Documento();
        doc.nuevaPagina();
        doc.resumen(resumen);

        List<List<String>> pagos = new ArrayList<>();
        for (Pago p : resumen.pagos()) {
            pagos.add(List.of(valor(p.id()), valor(p.reservaId()), valor(p.tipo()), valor(p.metodo()),
                    dinero(p.monto()), valor(p.telefonoOrigen()), valor(p.codigoOperacion()),
                    valor(p.estado()), fecha(p.registradoEn())));
        }
        doc.tabla("HISTORIAL COMPLETO DE PAGOS",
                new String[]{"ID", "Reserva", "Tipo", "Método", "Monto", "Yape / efectivo", "Operación", "Estado", "Registrado"},
                new double[]{0.5, 0.6, 0.78, 0.75, 0.85, 1.15, 1.1, 0.95, 1.32}, pagos);

        List<List<String>> devoluciones = new ArrayList<>();
        for (Devolucion d : resumen.devoluciones()) {
            devoluciones.add(List.of(valor(d.id()), valor(d.reservaId()), dinero(d.monto()),
                    valor(d.metodo()), valor(d.telefonoDestino()), valor(d.estado()), fecha(d.registradoEn())));
        }
        doc.tabla("HISTORIAL COMPLETO DE DEVOLUCIONES",
                new String[]{"ID", "Reserva", "Monto", "Método", "Destino", "Estado", "Fecha"},
                new double[]{0.5, 0.65, 1.05, 0.9, 1.1, 1.05, 1.35}, devoluciones);

        List<List<String>> reservas = new ArrayList<>();
        for (Reserva r : resumen.reservas()) {
            String cliente = r.cliente() == null ? "-" : r.cliente().nombres() + " " + r.cliente().apellidos();
            String auto = r.auto() == null ? "-" : r.auto().marca() + " " + r.auto().modelo() + " (" + r.auto().placa() + ")";
            reservas.add(List.of(valor(r.id()), valor(cliente), valor(auto), fecha(r.fechaInicio()),
                    fecha(r.fechaFin()), dinero(r.total()), valor(r.estado())));
        }
        doc.tabla("HISTORIAL COMPLETO DE RESERVAS",
                new String[]{"ID", "Cliente", "Vehículo", "Inicio", "Fin", "Total", "Estado"},
                new double[]{0.45, 1.55, 1.8, 0.85, 0.85, 0.9, 1.15}, reservas);
        return doc.pdf();
    }

    // ---- Utilidades de formato: texto, fechas y dinero ----

    /** Convierte cualquier valor a texto de una sola línea; si no hay dato, muestra "-". */
    private static String valor(Object valor) {
        if (valor == null) return "-";
        String s = valor.toString().trim().replace('\n', ' ').replace('\r', ' ');
        return s.isEmpty() ? "-" : s;
    }
    private static String fecha(LocalDate fecha) { return fecha == null ? "-" : FECHA.format(fecha); }
    private static String fecha(LocalDateTime fecha) { return fecha == null ? "-" : FECHA_HORA.format(fecha); }
    private static String dinero(BigDecimal valor) {
        return String.format(Locale.US, "S/ %,.2f", valor == null ? BigDecimal.ZERO : valor);
    }
    /** Número con 2 decimales y punto decimal, como exige el formato PDF. */
    private static String f(double valor) { return String.format(Locale.US, "%.2f", valor); }

    /**
     * Prepara un texto para el PDF: los paréntesis y la barra invertida se escapan porque delimitan
     * el texto en el formato; los caracteres que no existen en Windows-1252 se cambian por "?".
     */
    private static String escapar(String texto) {
        StringBuilder sb = new StringBuilder();
        for (int cp : valor(texto).codePoints().toArray()) {
            String caracter = new String(Character.toChars(cp));
            if (!CP1252.newEncoder().canEncode(caracter)) caracter = "?";
            for (int i = 0; i < caracter.length(); i++) {
                char c = caracter.charAt(i);
                if (c == '\\' || c == '(' || c == ')') sb.append('\\');
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Una hoja del PDF. Guarda sus instrucciones de dibujo y la posición vertical "y" donde se dibujará
     * lo siguiente. Operadores usados: rg = color de relleno, re f = rectángulo relleno,
     * BT/ET = bloque de texto, Tf = fuente y tamaño, Tm = posición, Tj = escribir, m/l/S = línea.
     */
    private static final class Pagina {
        final StringBuilder contenido = new StringBuilder();
        double y = ALTO - 85;
        void rect(double x, double arriba, double ancho, double alto, String color) {
            contenido.append(color).append(" rg ").append(f(x)).append(' ').append(f(arriba - alto))
                    .append(' ').append(f(ancho)).append(' ').append(f(alto)).append(" re f\n");
        }
        void texto(double x, double baseline, double tamano, boolean negrita, String texto, String color) {
            contenido.append("BT /F").append(negrita ? '2' : '1').append(' ').append(f(tamano))
                    .append(" Tf ").append(color).append(" rg 1 0 0 1 ")
                    .append(f(x)).append(' ').append(f(baseline)).append(" Tm (")
                    .append(escapar(texto)).append(") Tj ET\n");
        }
        void linea(double x1, double y1, double x2, double y2) {
            contenido.append("0.84 0.88 0.92 RG 0.35 w ").append(f(x1)).append(' ').append(f(y1))
                    .append(" m ").append(f(x2)).append(' ').append(f(y2)).append(" l S\n");
        }
    }

    /** El documento completo: lista de hojas, la hoja actual y la fecha de emisión. */
    private static final class Documento {
        private final List<Pagina> paginas = new ArrayList<>();
        private Pagina actual;
        private final String generado = FECHA_HORA.format(LocalDateTime.now());

        /** Abre una hoja nueva con la franja azul de encabezado (título y fecha de emisión). */
        void nuevaPagina() {
            actual = new Pagina();
            paginas.add(actual);
            actual.rect(0, ALTO, ANCHO, 65, NAVY);
            actual.texto(MARGEN, ALTO - 28, 19, true, "AutoRent", BLANCO);
            actual.texto(MARGEN, ALTO - 47, 10, false, "REPORTE GENERAL DEL NEGOCIO", BLANCO);
            actual.texto(ANCHO - 225, ALTO - 37, 9, false, "Emitido: " + generado, BLANCO);
        }

        /** Dibuja 9 tarjetas de indicadores en 3 filas x 3 columnas. */
        void resumen(ResumenReporte r) {
            actual.texto(MARGEN, actual.y, 14, true, "Resumen de movimientos", NAVY);
            actual.y -= 17;
            String[] nombres = {"Reservas totales", "Reservas pendientes", "Reservas confirmadas",
                    "Alquileres en curso", "Pagos registrados", "Devoluciones pendientes",
                    "Ingresos confirmados", "Devoluciones completadas", "Ingreso neto"};
            String[] valores = {"" + r.totalReservas(), "" + r.reservasPendientes(), "" + r.reservasConfirmadas(),
                    "" + r.alquileresEnCurso(), "" + r.totalPagos(), "" + r.devolucionesPendientes(),
                    dinero(r.ingresosConfirmados()), dinero(r.devolucionesCompletadas()), dinero(r.ingresosNetos())};
            double espacio = 9, ancho = (ANCHO - 2 * MARGEN - 2 * espacio) / 3, alto = 48;
            for (int i = 0; i < nombres.length; i++) {
                int fila = i / 3, columna = i % 3;
                double x = MARGEN + columna * (ancho + espacio);
                double arriba = actual.y - fila * (alto + espacio);
                actual.rect(x, arriba, ancho, alto, FONDO);
                actual.texto(x + 11, arriba - 17, 9, false, nombres[i], MUTED);
                actual.texto(x + 11, arriba - 37, 15, true, valores[i], NAVY);
            }
            actual.y -= 3 * (alto + espacio) + 6;
            actual.y -= 19;
        }

        /**
         * Dibuja una tabla. Los anchos de columna se reparten según los pesos. Si un texto no entra
         * en su celda se parte en varias líneas, y si la fila no cabe en la hoja se abre otra
         * y se repiten los encabezados ("continuación"). Las filas alternan blanco y gris.
         */
        void tabla(String titulo, String[] encabezados, double[] pesos, List<List<String>> filas) {
            if (encabezados.length != pesos.length) throw new IllegalArgumentException("Columnas del PDF incorrectas.");
            double total = 0;
            for (double peso : pesos) total += peso;
            double[] anchos = new double[pesos.length];
            for (int i = 0; i < pesos.length; i++) anchos[i] = (ANCHO - 2 * MARGEN) * pesos[i] / total;
            tituloTabla(titulo, encabezados, anchos, false);
            if (filas.isEmpty()) {
                if (actual.y < 70) tituloTabla(titulo, encabezados, anchos, true);
                double y = actual.y;
                actual.rect(MARGEN, y, ANCHO - 2 * MARGEN, 24, FONDO);
                actual.texto(MARGEN + 9, y - 16, 9, false, "No hay registros.", MUTED);
                actual.y -= 24;
            }
            int indice = 0;
            for (List<String> fila : filas) {
                if (fila.size() != encabezados.length) throw new IllegalArgumentException("Fila del PDF incorrecta.");
                List<List<String>> lineas = new ArrayList<>();
                int maximo = 1;
                for (int i = 0; i < fila.size(); i++) {
                    List<String> trozos = partir(fila.get(i), anchos[i] - 12, 7.4);
                    lineas.add(trozos);
                    maximo = Math.max(maximo, trozos.size());
                }
                double alto = Math.max(23, maximo * 10.2 + 9);
                if (actual.y - alto < 40) tituloTabla(titulo, encabezados, anchos, true);
                double arriba = actual.y;
                actual.rect(MARGEN, arriba, ANCHO - 2 * MARGEN, alto,
                        indice % 2 == 0 ? "1 1 1" : FONDO);
                double x = MARGEN;
                for (int i = 0; i < fila.size(); i++) {
                    for (int l = 0; l < lineas.get(i).size(); l++) {
                        actual.texto(x + 6, arriba - 13 - l * 10.2, 7.4, false, lineas.get(i).get(l), TEXTO);
                    }
                    x += anchos[i];
                    if (i + 1 < fila.size()) actual.linea(x, arriba, x, arriba - alto);
                }
                actual.linea(MARGEN, arriba - alto, ANCHO - MARGEN, arriba - alto);
                actual.y -= alto;
                indice++;
            }
            actual.y -= 24;
        }

        /** Título de la tabla y fila de encabezados en azul oscuro. */
        private void tituloTabla(String titulo, String[] encabezados, double[] anchos, boolean continuacion) {
            if (continuacion || actual.y < 95) nuevaPagina();
            actual.texto(MARGEN, actual.y, 11.5, true, titulo + (continuacion ? " (continuación)" : ""), AZUL);
            actual.y -= 13;
            double arriba = actual.y, x = MARGEN;
            actual.rect(MARGEN, arriba, ANCHO - 2 * MARGEN, 25, NAVY);
            for (int i = 0; i < encabezados.length; i++) {
                actual.texto(x + 5, arriba - 16, 7.5, true, encabezados[i], BLANCO);
                x += anchos[i];
            }
            actual.y -= 25;
        }

        /** Parte un texto en líneas que entren en el ancho de la celda (primero por palabras, luego por letras). */
        private static List<String> partir(String texto, double ancho, double tamano) {
            String limpio = valor(texto);
            List<String> lineas = new ArrayList<>();
            StringBuilder linea = new StringBuilder();
            for (String palabra : limpio.split("\\s+")) {
                if (palabra.isEmpty()) continue;
                if (linea.length() > 0 && anchoTexto(linea + " " + palabra, tamano) > ancho) {
                    lineas.add(linea.toString());
                    linea.setLength(0);
                }
                for (int cp : palabra.codePoints().toArray()) {
                    String caracter = new String(Character.toChars(cp));
                    if (linea.length() > 0 && anchoTexto(linea + caracter, tamano) > ancho) {
                        lineas.add(linea.toString());
                        linea.setLength(0);
                    }
                    linea.append(caracter);
                }
                linea.append(' ');
            }
            if (linea.length() > 0) lineas.add(linea.toString().trim());
            if (lineas.isEmpty()) lineas.add("-");
            return lineas;
        }
        /** Ancho aproximado de un texto en Helvetica: letras finas, normales, mayúsculas y anchas. */
        private static double anchoTexto(CharSequence texto, double tamano) {
            double puntos = 0;
            for (int i = 0; i < texto.length(); i++) {
                char c = texto.charAt(i);
                if ("il.,:;!| '".indexOf(c) >= 0) puntos += 0.29;
                else if ("MW@%".indexOf(c) >= 0) puntos += 0.88;
                else if (Character.isUpperCase(c)) puntos += 0.67;
                else puntos += 0.55;
            }
            return puntos * tamano;
        }

        /**
         * Genera los bytes finales del PDF:
         * agrega el pie "Página X de N" a cada hoja, escribe los objetos (1 catálogo, 2 páginas,
         * 3-4 fuentes Helvetica normal y negrita, y por cada hoja su objeto Page y su contenido)
         * y al final la tabla xref y el trailer, que le dicen al lector dónde está cada objeto.
         */
        byte[] pdf() {
            for (int i = 0; i < paginas.size(); i++) {
                Pagina p = paginas.get(i);
                p.linea(MARGEN, 30, ANCHO - MARGEN, 30);
                p.texto(MARGEN, 18, 8, false, "AutoRent - Reporte completo - " + generado, MUTED);
                p.texto(ANCHO - 108, 18, 8, false, "Página " + (i + 1) + " de " + paginas.size(), MUTED);
            }
            try {
                ByteArrayOutputStream pdf = new ByteArrayOutputStream();
                pdf.write("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));
                pdf.write(new byte[]{'%', (byte) 0xE2, (byte) 0xE3, (byte) 0xCF, (byte) 0xD3, '\n'});
                int cantidad = 4 + paginas.size() * 2;
                byte[][] objetos = new byte[cantidad + 1][];
                objetos[1] = bytes("<< /Type /Catalog /Pages 2 0 R >>");
                StringBuilder hijos = new StringBuilder();
                for (int i = 0; i < paginas.size(); i++) hijos.append(5 + i * 2).append(" 0 R ");
                objetos[2] = bytes("<< /Type /Pages /Kids [" + hijos + "] /Count " + paginas.size() + " >>");
                objetos[3] = bytes("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
                objetos[4] = bytes("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");
                for (int i = 0; i < paginas.size(); i++) {
                    int paginaId = 5 + i * 2, contenidoId = paginaId + 1;
                    objetos[paginaId] = bytes("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + f(ANCHO) + " "
                            + f(ALTO) + "] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents "
                            + contenidoId + " 0 R >>");
                    byte[] contenido = paginas.get(i).contenido.toString().getBytes(CP1252);
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    stream.write(bytes("<< /Length " + contenido.length + " >>\nstream\n"));
                    stream.write(contenido);
                    stream.write(bytes("\nendstream"));
                    objetos[contenidoId] = stream.toByteArray();
                }
                // Se escribe cada objeto y se anota en qué byte empieza (lo necesita la tabla xref).
                long[] posiciones = new long[cantidad + 1];
                for (int id = 1; id <= cantidad; id++) {
                    posiciones[id] = pdf.size();
                    pdf.write(bytes(id + " 0 obj\n"));
                    pdf.write(objetos[id]);
                    pdf.write(bytes("\nendobj\n"));
                }
                long inicioXref = pdf.size();
                pdf.write(bytes("xref\n0 " + (cantidad + 1) + "\n0000000000 65535 f \n"));
                for (int id = 1; id <= cantidad; id++)
                    pdf.write(bytes(String.format(Locale.US, "%010d 00000 n \n", posiciones[id])));
                pdf.write(bytes("trailer\n<< /Size " + (cantidad + 1) + " /Root 1 0 R >>\nstartxref\n"
                        + inicioXref + "\n%%EOF\n"));
                return pdf.toByteArray();
            } catch (IOException e) {
                throw new IllegalStateException("No se pudo generar el PDF.", e);
            }
        }
        /** La estructura del PDF se escribe en ASCII; solo el contenido de las hojas usa Windows-1252. */
        private static byte[] bytes(String texto) { return texto.getBytes(StandardCharsets.US_ASCII); }
    }
}
