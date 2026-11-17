package pe.edu.upt.autorent.modelo;

/** Tipos de comprobante electrónico que emite AutoRent, con su código en el catálogo de SUNAT. */
public enum TipoComprobante {
    BOLETA("03", "BOLETA DE VENTA ELECTRÓNICA"),
    NOTA_CREDITO("07", "NOTA DE CRÉDITO ELECTRÓNICA");

    private final String codigoSunat;
    private final String nombre;

    TipoComprobante(String codigoSunat, String nombre) { this.codigoSunat = codigoSunat; this.nombre = nombre; }

    public String getCodigoSunat() { return codigoSunat; }
    public String getNombre() { return nombre; }
}
