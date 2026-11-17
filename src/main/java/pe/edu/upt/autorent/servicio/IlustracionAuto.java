package pe.edu.upt.autorent.servicio;

import pe.edu.upt.autorent.modelo.Auto;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

/**
 * Dibuja (en SVG) una ilustración del vehículo cuando todavía no tiene foto:
 * la silueta depende de la categoría (sedán, compacto, hatchback, SUV, camioneta, van)
 * y la carrocería se pinta con el color registrado del auto.
 *
 * Es un dibujo propio y genérico (sin logos ni diseños de una marca), así que se puede publicar sin
 * problemas de derechos de imagen. Si el administrador sube una foto real, se muestra la foto.
 */
public final class IlustracionAuto {
    private IlustracionAuto() { }

    /** Colores en español (sin tildes, en minúsculas) -> color de la carrocería. */
    private static final Map<String, String> COLORES = Map.ofEntries(
            Map.entry("negro", "#23272e"), Map.entry("blanco", "#f3f4f6"), Map.entry("rojo", "#c62828"),
            Map.entry("plata", "#b9c0c8"), Map.entry("plateado", "#b9c0c8"), Map.entry("gris", "#6b7280"),
            Map.entry("azul", "#1e5aa8"), Map.entry("celeste", "#5fa8d3"), Map.entry("verde", "#2e7d32"),
            Map.entry("amarillo", "#f2c230"), Map.entry("naranja", "#ef6c00"), Map.entry("marron", "#6d4c41"),
            Map.entry("cafe", "#6d4c41"), Map.entry("beige", "#d8c7a3"), Map.entry("dorado", "#c9a646"),
            Map.entry("vino", "#7b1e2b"), Map.entry("guinda", "#7b1e2b"), Map.entry("morado", "#6a3d9a"),
            Map.entry("turquesa", "#1aa39a"), Map.entry("grafito", "#3f454d"));

    /** Silueta de carrocería y ventanas por categoría (lienzo de 400 x 200, ruedas en y = 150). */
    private record Silueta(String carroceria, String ventanas, int ruedaTrasera, int ruedaDelantera) { }

    private static final Silueta SEDAN = new Silueta(
            "M38 142 L36 118 Q38 104 58 100 L112 92 Q140 62 182 58 L246 58 Q276 60 302 88 L352 98 Q370 102 372 120 L372 142 Z",
            "M126 92 Q148 68 182 66 L212 66 L212 92 Z M220 66 L244 66 Q266 68 288 92 L220 92 Z", 104, 302);
    private static final Silueta COMPACTO = new Silueta(
            "M58 142 L56 118 Q58 104 76 100 L118 92 Q144 60 186 56 L238 56 Q270 58 292 92 L334 100 Q350 104 352 120 L352 142 Z",
            "M132 92 Q152 66 186 64 L210 64 L210 92 Z M218 64 L238 64 Q262 66 282 92 L218 92 Z", 118, 292);
    private static final Silueta HATCHBACK = new Silueta(
            "M50 142 L48 108 Q50 66 84 60 L240 56 Q272 58 300 88 L346 98 Q364 102 366 120 L366 142 Z",
            "M70 92 Q72 70 92 68 L176 66 L176 92 Z M184 66 L238 64 Q262 66 286 92 L184 92 Z", 108, 300);
    private static final Silueta SUV = new Silueta(
            "M40 144 L38 100 Q40 54 74 48 L250 46 Q282 48 306 82 L352 94 Q370 98 372 118 L372 144 Z",
            "M60 86 Q62 60 84 58 L170 56 L170 86 Z M178 56 L246 56 Q272 58 292 86 L178 86 Z", 104, 304);
    private static final Silueta CAMIONETA = new Silueta(
            "M30 144 L30 96 L176 96 L178 58 Q180 46 196 46 L262 46 Q288 48 306 84 L354 94 Q372 98 374 118 L374 144 Z",
            "M188 86 L190 60 Q192 54 200 54 L240 54 L240 86 Z M248 54 L258 54 Q280 56 294 86 L248 86 Z", 96, 306);
    private static final Silueta VAN = new Silueta(
            "M34 144 L32 66 Q34 38 64 36 L284 36 Q306 38 322 70 L358 92 Q374 98 374 118 L374 144 Z",
            "M54 80 L56 54 Q58 46 68 46 L132 46 L132 80 Z M140 46 L214 46 L214 80 L140 80 Z M222 46 L282 46 Q300 48 312 80 L222 80 Z", 98, 306);

    private static Silueta silueta(String categoria) {
        String c = normalizar(categoria);
        if (c.contains("camioneta") || c.contains("pick")) return CAMIONETA;
        if (c.contains("suv")) return SUV;
        if (c.contains("van") || c.contains("minivan")) return VAN;
        if (c.contains("hatch")) return HATCHBACK;
        if (c.contains("compacto") || c.contains("city")) return COMPACTO;
        return SEDAN;
    }

    /** Color de la carrocería según el texto del color (por ejemplo "Rojo", "gris oscuro", "Plata"). */
    static String colorCarroceria(String color) {
        String c = normalizar(color);
        for (var e : COLORES.entrySet()) if (c.contains(e.getKey())) return e.getValue();
        return "#5b7083";
    }

    private static String normalizar(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    private static String escapar(String texto) {
        if (texto == null) return "";
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** SVG completo listo para enviar con tipo image/svg+xml. */
    public static String svg(Auto auto) {
        Silueta s = silueta(auto.categoria());
        String pintura = colorCarroceria(auto.color());
        boolean claro = pintura.equals("#f3f4f6") || pintura.equals("#d8c7a3") || pintura.equals("#f2c230");
        String borde = claro ? "#9aa3ad" : "rgba(0,0,0,.25)";
        String titulo = escapar(auto.marca() + " " + auto.modelo());
        String subtitulo = escapar((auto.categoria() == null ? "" : auto.categoria()) + (auto.color() == null || auto.color().isBlank() ? "" : " · " + auto.color()));
        return """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 220" role="img" aria-label="%s">
                  <title>%s</title>
                  <defs>
                    <linearGradient id="fondo" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0" stop-color="#eef3f8"/><stop offset="1" stop-color="#dbe5ef"/>
                    </linearGradient>
                    <linearGradient id="brillo" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0" stop-color="#ffffff" stop-opacity=".35"/><stop offset=".5" stop-color="#ffffff" stop-opacity="0"/>
                      <stop offset="1" stop-color="#000000" stop-opacity=".18"/>
                    </linearGradient>
                  </defs>
                  <rect width="400" height="220" fill="url(#fondo)"/>
                  <ellipse cx="205" cy="166" rx="170" ry="9" fill="#000" opacity=".12"/>
                  <path d="%s" fill="%s" stroke="%s" stroke-width="2" stroke-linejoin="round"/>
                  <path d="%s" fill="url(#brillo)"/>
                  <path d="%s" fill="#2b3a4a" opacity=".85"/>
                  <rect x="352" y="108" width="16" height="8" rx="3" fill="#fde68a"/>
                  <rect x="40" y="110" width="12" height="8" rx="3" fill="#ef4444"/>
                  %s
                  %s
                  <text x="200" y="196" text-anchor="middle" font-family="Segoe UI, Arial, sans-serif" font-size="15" font-weight="700" fill="#183a5a">%s</text>
                  <text x="200" y="213" text-anchor="middle" font-family="Segoe UI, Arial, sans-serif" font-size="11" fill="#5b6b7b">%s</text>
                </svg>
                """.formatted(titulo, titulo, s.carroceria(), pintura, borde, s.carroceria(), s.ventanas(),
                rueda(s.ruedaTrasera()), rueda(s.ruedaDelantera()), titulo, subtitulo);
    }

    private static String rueda(int x) {
        return ("<g><circle cx=\"%d\" cy=\"148\" r=\"22\" fill=\"#1f2328\"/><circle cx=\"%d\" cy=\"148\" r=\"11\" fill=\"#c9ced6\"/>"
                + "<circle cx=\"%d\" cy=\"148\" r=\"4\" fill=\"#6b7280\"/></g>").formatted(x, x, x);
    }
}
