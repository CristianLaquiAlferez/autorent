package pe.edu.upt.autorent.modelo;

import java.math.BigDecimal;

/** Orden creada en PayPal: el cliente la aprueba en la ventana de PayPal y luego el servidor la cobra. */
public record OrdenEnLinea(String id, BigDecimal monto, String moneda) { }
