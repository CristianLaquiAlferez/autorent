package pe.edu.upt.autorent.modelo;
/** Modelo de la imagen del vehículo: bytes y tipo de contenido. */

public record ImagenAuto(byte[] datos, String tipoContenido) { }
