package pe.edu.upt.autorent.modelo;
/** Validaciones de teléfonos peruanos y números de Yape, en un solo lugar para todo el sistema. */

public final class TelefonoPeru {
    private TelefonoPeru() { }

    /** Teléfono de contacto: celular de 9 dígitos que empieza con 9, o fijo de 6 o 7 dígitos. */
    public static boolean esContactoValido(String telefono) {
        return ValidacionDatos.telefonoValido(telefono);
    }

    /** Celular o número Yape: 9 dígitos, empieza con 9 y se escribe sin +51. */
    public static boolean esMovilValido(String telefono) {
        return telefono != null && telefono.trim().matches("9[0-9]{8}");
    }

    /** Quita espacios al inicio y al final antes de guardar. */
    public static String normalizar(String telefono) {
        return telefono == null ? "" : telefono.trim();
    }
}
