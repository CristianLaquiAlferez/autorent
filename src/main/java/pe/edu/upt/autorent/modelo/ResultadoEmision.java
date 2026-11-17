package pe.edu.upt.autorent.modelo;

/** Respuesta del sistema de facturación electrónica (Nubefact o el modo de prueba). */
public record ResultadoEmision(EstadoComprobante estado, String enlacePdf, String hash, String cadenaQr, String mensaje) { }
