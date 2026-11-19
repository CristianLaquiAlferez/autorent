package pe.edu.upt.autorent.modelo;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reglas de formato de los datos que escribe una persona (nombres, correo, DNI, teléfono, marca, motivos).
 * Están en un solo lugar para que el registro web, el registro en sucursal, la gestión de trabajadores,
 * los vehículos y las operaciones apliquen exactamente lo mismo. El navegador repite estas reglas
 * (atributos pattern de los formularios), pero la regla real es esta: el servidor siempre la vuelve a aplicar.
 */
public final class ValidacionDatos {
    private ValidacionDatos() { }

    private static final String LETRA = "A-Za-zÁÉÍÓÚÜÑáéíóúüñ";
    /** Palabras de letras separadas por un espacio, apóstrofo o guion: "María José", "D'Angelo", "Pérez-Rojas". */
    private static final Pattern NOMBRE = Pattern.compile("[" + LETRA + "]+([ '\\-][" + LETRA + "]+)*");
    /** Parte local y dominio sin puntos al inicio, al final ni dobles; dominio con extensión de 2 a 24 letras. */
    private static final Pattern CORREO = Pattern.compile(
            "[a-z0-9](?:[a-z0-9_%+\\-]|\\.(?!\\.))*(?<!\\.)@(?:[a-z0-9](?:[a-z0-9\\-]*[a-z0-9])?\\.)+[a-z]{2,24}");
    /** Celular peruano (9 dígitos que empiezan con 9) o teléfono fijo (6 o 7 dígitos que no empiezan con 0 ni 9). */
    private static final Pattern TELEFONO = Pattern.compile("9\\d{8}|[1-8]\\d{5,6}");
    /** Marca o modelo: letras, números, espacio, punto y guion, con al menos una letra ("Hyundai", "CX-5", "Model 3"). */
    private static final Pattern MARCA_MODELO = Pattern.compile("(?=.*[" + LETRA + "])[" + LETRA + "0-9][" + LETRA + "0-9 .\\-]*");
    /** Color: solo letras y espacios ("Gris oscuro"). */
    private static final Pattern COLOR = Pattern.compile("[" + LETRA + "]+( [" + LETRA + "]+)*");

    /** Errores de escritura frecuentes en los dominios de correo más usados en Perú. */
    private static final Map<String, String> DOMINIOS_MAL_ESCRITOS = Map.ofEntries(
            Map.entry("gmial.com", "gmail.com"), Map.entry("gmal.com", "gmail.com"), Map.entry("gmai.com", "gmail.com"),
            Map.entry("gamil.com", "gmail.com"), Map.entry("gnail.com", "gmail.com"), Map.entry("gmaill.com", "gmail.com"),
            Map.entry("gmail.co", "gmail.com"), Map.entry("gmail.cm", "gmail.com"), Map.entry("gmail.om", "gmail.com"),
            Map.entry("gmail.comm", "gmail.com"),
            Map.entry("hotmial.com", "hotmail.com"), Map.entry("hotmal.com", "hotmail.com"), Map.entry("hotmai.com", "hotmail.com"),
            Map.entry("hotmail.co", "hotmail.com"), Map.entry("homail.com", "hotmail.com"),
            Map.entry("outlok.com", "outlook.com"), Map.entry("outllok.com", "outlook.com"), Map.entry("outlook.co", "outlook.com"),
            Map.entry("yaho.com", "yahoo.com"), Map.entry("yahooo.com", "yahoo.com"), Map.entry("yahoo.co", "yahoo.com"));

    /** Nombres o apellidos: de 2 a 80 caracteres, solo letras (con tildes y ñ), espacios, apóstrofo o guion. */
    public static void nombre(String valor, String campo) {
        String v = valor == null ? "" : valor.trim();
        if (v.isEmpty()) throw new IllegalArgumentException("Ingrese " + campo + ".");
        if (v.length() < 2 || v.length() > 80)
            throw new IllegalArgumentException("Los " + campo + " deben tener entre 2 y 80 caracteres.");
        if (!NOMBRE.matcher(v).matches())
            throw new IllegalArgumentException("Los " + campo + " solo admiten letras, un espacio entre palabras, apóstrofo o guion (sin números ni símbolos).");
    }

    /** Correo con formato real y sin errores de escritura comunes en el dominio (gmial.com, hotmail.co, ...com.pe sí vale). */
    public static void correo(String valor) {
        String v = valor == null ? "" : valor.trim().toLowerCase();
        if (v.isEmpty() || v.length() > 120 || !CORREO.matcher(v).matches())
            throw new IllegalArgumentException("Ingrese un correo válido, por ejemplo: nombre@gmail.com.");
        String dominio = v.substring(v.indexOf('@') + 1);
        String sugerido = DOMINIOS_MAL_ESCRITOS.get(dominio);
        if (sugerido != null)
            throw new IllegalArgumentException("Revise el correo: ¿quiso decir @" + sugerido + "?");
        if (dominio.endsWith(".con") || dominio.endsWith(".cmo") || dominio.endsWith(".ocm"))
            throw new IllegalArgumentException("Revise el correo: la terminación \"." + dominio.substring(dominio.lastIndexOf('.') + 1) + "\" no existe; ¿quiso decir \".com\"?");
    }

    /** DNI: exactamente 8 dígitos y no todos iguales (00000000, 11111111... no son documentos reales). */
    public static void dni(String valor) {
        String v = valor == null ? "" : valor.trim();
        if (!v.matches("\\d{8}")) throw new IllegalArgumentException("El DNI debe tener exactamente 8 dígitos.");
        if (v.chars().distinct().count() == 1) throw new IllegalArgumentException("El DNI no es válido (todos sus dígitos son iguales).");
    }

    /** Teléfono de contacto (opcional): celular de 9 dígitos que empieza con 9, o fijo de 6 o 7 dígitos; sin +51. */
    public static boolean telefonoValido(String valor) {
        return valor != null && TELEFONO.matcher(valor.trim()).matches();
    }

    public static void telefonoOpcional(String valor) {
        if (valor == null || valor.isBlank()) return;
        if (!telefonoValido(valor))
            throw new IllegalArgumentException("El teléfono debe ser un celular de 9 dígitos que empiece con 9, o un fijo de 6 o 7 dígitos, sin +51.");
    }

    /** Marca o modelo del vehículo; campo = "La marca" o "El modelo" (va al inicio del mensaje). */
    public static void marcaModelo(String valor, String campo) {
        String v = valor == null ? "" : valor.trim();
        if (v.isEmpty() || v.length() > 60 || !MARCA_MODELO.matcher(v).matches())
            throw new IllegalArgumentException(campo + " debe tener letras (puede llevar números, espacio, punto o guion) y como máximo 60 caracteres.");
    }

    /** Color del vehículo (opcional): solo letras. */
    public static void colorOpcional(String valor) {
        if (valor == null || valor.isBlank()) return;
        String v = valor.trim();
        if (v.length() > 40 || !COLOR.matcher(v).matches())
            throw new IllegalArgumentException("El color solo admite letras, por ejemplo: Gris oscuro.");
    }

    /**
     * Un motivo, concepto u observación que queda en el historial: al menos 5 caracteres y alguna letra,
     * para que explique lo ocurrido ("a", "...", "12345" no dicen nada).
     */
    public static void textoExplicativo(String valor, String mensajeSiFalta) {
        String v = valor == null ? "" : valor.trim();
        if (v.length() < 5 || !v.matches(".*[" + LETRA + "].*")) throw new IllegalArgumentException(mensajeSiFalta);
    }
}
