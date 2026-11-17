package pe.edu.upt.autorent.modelo;

import java.time.LocalDate;
import java.time.LocalDateTime;
/** Modelo de negocio de un trabajo de mantenimiento de vehículo. */

public record Mantenimiento(Long id, Long autoId, String descripcion,
                            LocalDate fechaInicio, LocalDate fechaFin,
                            EstadoMantenimiento estado, LocalDateTime registradoEn) {
}
