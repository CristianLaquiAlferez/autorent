package pe.edu.upt.autorent.modelo;

/** Respuesta de la pasarela al devolver dinero: si se hizo, con qué referencia y un mensaje. */
public record ResultadoReembolso(boolean exitoso, String referencia, String mensaje) { }
