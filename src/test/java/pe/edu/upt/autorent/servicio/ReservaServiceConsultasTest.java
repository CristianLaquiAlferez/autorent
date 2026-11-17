package pe.edu.upt.autorent.servicio;

import org.junit.jupiter.api.Test;
import pe.edu.upt.autorent.modelo.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de las consultas que se movieron de los controladores a ReservaService:
 * saldo de cada reserva, reservas cancelables y reservas en las que se puede reintentar el pago.
 * Estas consultas no usan la base de datos, por eso el servicio se crea sin repositorios.
 */
class ReservaServiceConsultasTest {

    private final ReservaService servicio = new ReservaService(null, null, null, null, null, null, null,
            java.time.Clock.systemDefaultZone(), null, 24, 30);

    private static Reserva reserva(long id, EstadoReserva estado, LocalDate inicio, String total) {
        return new Reserva(id, null, null, inicio, inicio.plusDays(3), 3, new BigDecimal("100.00"),
                new BigDecimal(total), estado, null, null, LocalDateTime.now());
    }

    private static Pago pago(long reservaId, String monto, EstadoPago estado) {
        return new Pago(null, reservaId, new BigDecimal(monto), MetodoPago.YAPE, TipoPago.ANTICIPO,
                estado, "999888777", "OP-" + reservaId + monto, LocalDateTime.now(), null, "cliente", null, null, null);
    }

    @Test
    void elSaldoRestaSoloLosPagosConfirmados() {
        LocalDate inicio = LocalDate.now().plusDays(10);
        List<Reserva> reservas = List.of(reserva(1, EstadoReserva.CONFIRMADA, inicio, "300.00"));
        List<Pago> pagos = List.of(pago(1, "60.00", EstadoPago.CONFIRMADO),
                pago(1, "100.00", EstadoPago.RECHAZADO),
                pago(1, "50.00", EstadoPago.PENDIENTE));

        Map<Long, BigDecimal> saldos = servicio.calcularSaldos(reservas, pagos);

        assertEquals(0, new BigDecimal("240.00").compareTo(saldos.get(1L)));
    }

    @Test
    void soloSePuedenCancelarReservasActivasConMasDe24Horas() {
        LocalDate lejos = LocalDate.now().plusDays(10);
        LocalDate manana = LocalDate.now().plusDays(1);
        List<Reserva> reservas = List.of(
                reserva(1, EstadoReserva.CONFIRMADA, lejos, "300.00"),
                reserva(2, EstadoReserva.EN_CURSO, lejos, "300.00"),
                reserva(3, EstadoReserva.PENDIENTE_PAGO, manana, "300.00"));

        Set<Long> cancelables = servicio.reservasCancelables(reservas);

        assertEquals(Set.of(1L), cancelables);
    }

    @Test
    void seReintentaElPagoSoloSiNoHayPagoVigente() {
        LocalDate inicio = LocalDate.now().plusDays(10);
        List<Reserva> reservas = List.of(
                reserva(1, EstadoReserva.PENDIENTE_PAGO, inicio, "300.00"),
                reserva(2, EstadoReserva.PENDIENTE_VALIDACION, inicio, "300.00"),
                reserva(3, EstadoReserva.CONFIRMADA, inicio, "300.00"));
        List<Pago> pagos = List.of(pago(1, "60.00", EstadoPago.RECHAZADO),
                pago(2, "60.00", EstadoPago.PENDIENTE));

        Set<Long> reintentables = servicio.reservasReintentables(reservas, pagos);

        assertEquals(Set.of(1L), reintentables);
    }
}
