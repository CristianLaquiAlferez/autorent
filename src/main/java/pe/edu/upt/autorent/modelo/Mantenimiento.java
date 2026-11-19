package pe.edu.upt.autorent.modelo;

import java.time.LocalDate;
import java.time.LocalDateTime;
/** Modelo de negocio de un trabajo de mantenimiento de vehículo. */

public record Mantenimiento(Long id, Long autoId, String descripcion,
                            LocalDate fechaInicio, LocalDate fechaFin,
                            EstadoMantenimiento estado, LocalDateTime registradoEn) {

    /**
     * Último día que el auto queda en el taller. Si ya está en el taller y la fecha estimada pasó,
     * sigue ocupado hasta hoy (hasta que el administrador lo finalice).
     */
    public LocalDate ultimoDiaOcupado(LocalDate hoy) {
        LocalDate fin = fechaFin == null ? fechaInicio : fechaFin;
        return estado == EstadoMantenimiento.EN_PROCESO && fin.isBefore(hoy) ? hoy : fin;
    }

    /**
     * ¿Choca con un uso del auto del día inicio al día finExclusivo (el día en que el auto vuelve)?
     * El taller ocupa del inicio al último día, ambos incluidos; una reserva que termina el día en que
     * empieza el taller no choca (el auto vuelve y entra al taller ese día).
     */
    public boolean choca(LocalDate inicio, LocalDate finExclusivo, LocalDate hoy) {
        return estado.ocupaElAuto() && fechaInicio.isBefore(finExclusivo) && ultimoDiaOcupado(hoy).plusDays(1).isAfter(inicio);
    }
}
