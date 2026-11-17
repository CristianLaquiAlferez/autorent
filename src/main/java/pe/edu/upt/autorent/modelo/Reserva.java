package pe.edu.upt.autorent.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
/** Modelo de negocio que representa un alquiler y su estado actual. */

public record Reserva(Long id, Auto auto, Usuario cliente, LocalDate fechaInicio,
                      LocalDate fechaFin, int dias, BigDecimal tarifaDiaria,
                      BigDecimal total, EstadoReserva estado, String telefonoDevolucion,
                      String observacion, LocalDateTime creadoEn) {

    /** Copia de la reserva con otro estado (los records son inmutables). */
    public Reserva conEstado(EstadoReserva nuevo) {
        return new Reserva(id, auto, cliente, fechaInicio, fechaFin, dias, tarifaDiaria, total, nuevo,
                telefonoDevolucion, observacion, creadoEn);
    }

    /** Copia con otra fecha de devolución, días y total (por ejemplo, cuando el auto se devuelve tarde). */
    public Reserva conNuevoPeriodo(LocalDate nuevaFechaFin, int nuevosDias, BigDecimal nuevoTotal, String nota) {
        return new Reserva(id, auto, cliente, fechaInicio, nuevaFechaFin, nuevosDias, tarifaDiaria, nuevoTotal, estado,
                telefonoDevolucion, nota, creadoEn);
    }
}
