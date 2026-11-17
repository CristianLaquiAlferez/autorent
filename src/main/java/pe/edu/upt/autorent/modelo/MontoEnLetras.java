package pe.edu.upt.autorent.modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Convierte un importe a letras, como exige la representación impresa de una boleta.
 * Ejemplo: 1250.50 -> "MIL DOSCIENTOS CINCUENTA CON 50/100 SOLES".
 */
public final class MontoEnLetras {
    private static final String[] UNIDADES = {"", "UNO", "DOS", "TRES", "CUATRO", "CINCO", "SEIS", "SIETE", "OCHO", "NUEVE",
            "DIEZ", "ONCE", "DOCE", "TRECE", "CATORCE", "QUINCE", "DIECISÉIS", "DIECISIETE", "DIECIOCHO", "DIECINUEVE",
            "VEINTE", "VEINTIUNO", "VEINTIDÓS", "VEINTITRÉS", "VEINTICUATRO", "VEINTICINCO", "VEINTISÉIS", "VEINTISIETE",
            "VEINTIOCHO", "VEINTINUEVE"};
    private static final String[] DECENAS = {"", "", "", "TREINTA", "CUARENTA", "CINCUENTA", "SESENTA", "SETENTA", "OCHENTA", "NOVENTA"};
    private static final String[] CENTENAS = {"", "CIENTO", "DOSCIENTOS", "TRESCIENTOS", "CUATROCIENTOS", "QUINIENTOS",
            "SEISCIENTOS", "SETECIENTOS", "OCHOCIENTOS", "NOVECIENTOS"};

    private MontoEnLetras() { }

    /** Importe en soles con los céntimos en formato NN/100. */
    public static String soles(BigDecimal monto) {
        BigDecimal redondeado = monto.setScale(2, RoundingMode.HALF_UP);
        long enteros = redondeado.longValue();
        int centimos = redondeado.remainder(BigDecimal.ONE).movePointRight(2).abs().intValue();
        String letras = enteros == 0 ? "CERO" : convertir(enteros);
        return letras + " CON " + String.format("%02d", centimos) + "/100 SOLES";
    }

    /** Delante de MIL o MILLONES "uno" se acorta: "veintiún mil", "treinta y un mil". */
    private static String apocopar(String texto) {
        return texto.replaceAll("VEINTIUNO$", "VEINTIÚN").replaceAll("UNO$", "UN");
    }

    static String convertir(long n) {
        if (n >= 1_000_000) {
            long millones = n / 1_000_000;
            String texto = millones == 1 ? "UN MILLÓN" : apocopar(convertir(millones)) + " MILLONES";
            return (texto + " " + (n % 1_000_000 == 0 ? "" : convertir(n % 1_000_000))).trim();
        }
        if (n >= 1000) {
            long miles = n / 1000;
            String texto = miles == 1 ? "MIL" : apocopar(convertir(miles)) + " MIL";
            return (texto + " " + (n % 1000 == 0 ? "" : convertir(n % 1000))).trim();
        }
        if (n >= 100) {
            if (n == 100) return "CIEN";
            return (CENTENAS[(int) (n / 100)] + " " + (n % 100 == 0 ? "" : convertir(n % 100))).trim();
        }
        if (n < 30) return UNIDADES[(int) n];
        return DECENAS[(int) (n / 10)] + (n % 10 == 0 ? "" : " Y " + UNIDADES[(int) (n % 10)]);
    }
}
