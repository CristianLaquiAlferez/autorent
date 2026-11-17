package pe.edu.upt.autorent.modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
/**
 * Reglas puras del negocio de alquiler: días, anticipo mínimo, cancelación y montos.
 *
 * Es una clase "pura": no usa base de datos ni Spring, solo cálculos. Por eso se puede probar
 * con JUnit de forma directa (ver ReglasAlquilerTest) y la reutilizan los servicios.
 */

public final class ReglasAlquiler {
    /** El cliente debe adelantar como mínimo el 20 % del total del alquiler. */
    private static final BigDecimal PORCENTAJE_MINIMO = new BigDecimal("0.20");

    /** Un alquiler dura como máximo 30 días; para más tiempo se hace un contrato aparte. */
    public static final int MAXIMO_DIAS_ALQUILER = 30;
    /** Solo se puede reservar con hasta 180 días (unos 6 meses) de anticipación. */
    public static final int MAXIMO_DIAS_ANTICIPACION = 180;

    private ReglasAlquiler() { } // clase de utilidades: no se crean objetos de ella

    /** Anticipo mínimo = 20 % del total, redondeado a 2 decimales. Ej.: total S/ 450 -> S/ 90,00. */
    public static BigDecimal minimoAnticipo(BigDecimal total) {
        return total.multiply(PORCENTAJE_MINIMO).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Días de alquiler entre la fecha de recojo y la de devolución.
     * El periodo es [inicio, fin): del 10 al 13 son 3 días. La devolución debe ser posterior al inicio.
     */
    public static int calcularDias(LocalDate inicio, LocalDate fin) {
        if (inicio == null || fin == null || !fin.isAfter(inicio)) {
            throw new IllegalArgumentException("La fecha de devolución debe ser posterior a la fecha de inicio.");
        }
        return Math.toIntExact(Math.max(1, ChronoUnit.DAYS.between(inicio, fin)));
    }

    /**
     * Valida el periodo pedido: no en el pasado, como máximo 30 días de alquiler
     * y como máximo 180 días de anticipación. Devuelve la cantidad de días.
     */
    public static int validarPeriodo(LocalDate inicio, LocalDate fin, LocalDate hoy) {
        if (inicio == null || fin == null) throw new IllegalArgumentException("Seleccione las fechas del alquiler.");
        if (inicio.isBefore(hoy)) throw new IllegalArgumentException("No se puede reservar para una fecha pasada.");
        int dias = calcularDias(inicio, fin);
        if (dias > MAXIMO_DIAS_ALQUILER)
            throw new IllegalArgumentException("El alquiler puede durar como máximo " + MAXIMO_DIAS_ALQUILER + " días.");
        if (ChronoUnit.DAYS.between(hoy, inicio) > MAXIMO_DIAS_ANTICIPACION)
            throw new IllegalArgumentException("Solo se puede reservar con hasta " + MAXIMO_DIAS_ANTICIPACION + " días de anticipación.");
        return dias;
    }

    /**
     * Días de retraso en la devolución: si el auto vuelve después de la fecha pactada,
     * se cobra cada día adicional con la misma tarifa. Devolver antes no genera descuento.
     */
    public static int diasDeRetraso(LocalDate fechaFinPactada, LocalDate fechaDevolucion) {
        return Math.toIntExact(Math.max(0, ChronoUnit.DAYS.between(fechaFinPactada, fechaDevolucion)));
    }

    /**
     * Regla de cancelación: se permite hasta 24 horas antes de las 00:00 del día de inicio.
     * Ej.: si el alquiler empieza el 10/12, se puede cancelar hasta el 09/12 a las 00:00.
     */
    public static boolean sePuedeCancelar(LocalDate fechaInicio, LocalDateTime ahora) {
        return !ahora.isAfter(fechaInicio.atStartOfDay().minusHours(24));
    }

    /** Un monto válido es mayor que cero y tiene como máximo dos decimales (céntimos). */
    public static void validarMonto(BigDecimal monto) {
        if (monto == null || monto.signum() <= 0 || monto.stripTrailingZeros().scale() > 2)
            throw new IllegalArgumentException("El importe debe ser positivo y tener como máximo dos decimales.");
    }

    /** El anticipo debe estar entre el 20 % del total y el total; si no, se rechaza con un mensaje claro. */
    public static void validarAnticipo(BigDecimal monto, BigDecimal total) {
        validarMonto(monto);
        if (monto == null || monto.compareTo(minimoAnticipo(total)) < 0) {
            throw new IllegalArgumentException("El anticipo debe ser como mínimo el 20% del alquiler (" + minimoAnticipo(total) + ").");
        }
        if (monto.compareTo(total) > 0 || monto.signum() <= 0) {
            throw new IllegalArgumentException("El anticipo no puede superar el total del alquiler.");
        }
    }
}
