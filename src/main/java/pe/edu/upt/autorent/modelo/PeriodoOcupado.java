package pe.edu.upt.autorent.modelo;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Días en que un vehículo ya está comprometido: una reserva activa, un alquiler en curso o un mantenimiento.
 * Una reserva va del día de recojo al día de devolución; ese último día el auto vuelve y puede salir de nuevo,
 * por eso dos reservas pueden empalmarse en ese día (la misma regla que aplica el servidor).
 * Un mantenimiento ocupa del inicio al fin, ambos incluidos.
 *
 * @param reservaId  reserva que ocupa el auto (null si es un mantenimiento)
 * @param estado     estado de la reserva (null si es un mantenimiento)
 * @param finLibre   primer día en que el auto vuelve a estar libre (para comparar fechas)
 */
public record PeriodoOcupado(Long reservaId, LocalDate inicio, LocalDate fin, EstadoReserva estado,
                             boolean mantenimiento, LocalDate finLibre) {
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public static PeriodoOcupado deReserva(Reserva r) {
        return new PeriodoOcupado(r.id(), r.fechaInicio(), r.fechaFin(), r.estado(), false, r.fechaFin());
    }

    public static PeriodoOcupado deMantenimiento(Mantenimiento m, LocalDate hoy) {
        LocalDate ultimo = m.ultimoDiaOcupado(hoy);
        return new PeriodoOcupado(null, m.fechaInicio(), ultimo, null, true, ultimo.plusDays(1));
    }

    /** El auto está fuera de la sucursal (alquilado) ahora mismo. */
    public boolean enCurso() { return estado == EstadoReserva.EN_CURSO; }

    /** Alquiler en curso cuya fecha de devolución ya pasó: el auto debió volver y no volvió. */
    public boolean atrasado(LocalDate hoy) { return enCurso() && fin.isBefore(hoy); }

    /** "25/11/2026 al 27/11/2026". */
    public String texto() { return inicio.format(DIA) + " al " + fin.format(DIA); }

    /** Fecha de fin en formato corto, para "devuelve el ...". */
    public String finTexto() { return fin.format(DIA); }

    /** Qué ocupa el auto, en palabras. */
    public String etiqueta() {
        if (mantenimiento) return "mantenimiento";
        return switch (estado) {
            case EN_CURSO -> "alquilado";
            case CONFIRMADA -> "reservado";
            case PENDIENTE_VALIDACION -> "reservado, pago por validar";
            case PENDIENTE_PAGO -> "reservado, esperando pago";
            default -> estado.name().toLowerCase();
        };
    }
}
