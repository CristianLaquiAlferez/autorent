package pe.edu.upt.autorent.modelo;

/**
 * Estados de un comprobante electrónico.
 * PENDIENTE: guardado, aún no enviado. EMITIDO: generado (en modo de prueba o esperando respuesta de SUNAT).
 * ACEPTADO: SUNAT lo aceptó. RECHAZADO: SUNAT lo rechazó. ERROR: no se pudo enviar (se puede reintentar).
 */
public enum EstadoComprobante {
    PENDIENTE, EMITIDO, ACEPTADO, RECHAZADO, ERROR
}
