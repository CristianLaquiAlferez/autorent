package pe.edu.upt.autorent.modelo;

/**
 * Estados del mantenimiento.
 * PROGRAMADO: tiene fechas futuras; el auto sigue alquilándose fuera de esas fechas.
 * EN_PROCESO: el auto está en el taller (estado MANTENIMIENTO) hasta que el administrador lo finaliza.
 * FINALIZADO: terminado, o un programado que se canceló.
 */
public enum EstadoMantenimiento {
    PROGRAMADO, EN_PROCESO, FINALIZADO;

    /** Ocupa días del auto: no se puede reservar en esas fechas. */
    public boolean ocupaElAuto() { return this == PROGRAMADO || this == EN_PROCESO; }
}
