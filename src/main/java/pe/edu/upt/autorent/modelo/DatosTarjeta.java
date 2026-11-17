package pe.edu.upt.autorent.modelo;

/**
 * Datos de tarjeta que escribe el cliente en la pasarela de prueba.
 * Solo viajan en memoria hasta la pasarela: nunca se guardan en la base de datos.
 * toString() está enmascarado para que el número completo jamás aparezca en un log.
 */
public record DatosTarjeta(String numero, String titular, String vencimiento, String cvv) {

    /** Número sin espacios ni guiones. */
    public String numeroLimpio() { return numero == null ? "" : numero.replaceAll("[\\s-]", ""); }

    /** Últimos 4 dígitos, lo único que se puede mostrar o guardar. */
    public String ultimos4() {
        String n = numeroLimpio();
        return n.length() >= 4 ? n.substring(n.length() - 4) : n;
    }

    @Override public String toString() { return "DatosTarjeta[**** " + ultimos4() + "]"; }
}
