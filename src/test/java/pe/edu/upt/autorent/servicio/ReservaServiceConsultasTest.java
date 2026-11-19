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
            java.time.Clock.systemDefaultZone(), null, null, 24, 30);

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

        // La reserva 3 empieza mañana y tiene un Yape por validar: ya no se puede cancelar.
        Set<Long> cancelables = servicio.reservasCancelables(reservas, List.of(pago(3, "60.00", EstadoPago.PENDIENTE)));

        assertEquals(Set.of(1L), cancelables);
    }

    @Test
    void fueraDePlazoSeCancelaSiSoloHayUnEfectivoSinLlevar() {
        LocalDate manana = LocalDate.now().plusDays(1);
        List<Reserva> reservas = List.of(reserva(1, EstadoReserva.PENDIENTE_PAGO, manana, "300.00"),
                reserva(2, EstadoReserva.CONFIRMADA, manana, "300.00"));
        Pago efectivo = new Pago(null, 1L, new BigDecimal("60.00"), MetodoPago.EFECTIVO, TipoPago.ANTICIPO,
                EstadoPago.PENDIENTE, null, null, LocalDateTime.now(), null, "cliente", null, null, null);

        Set<Long> cancelables = servicio.reservasCancelables(reservas, List.of(efectivo, pago(2, "60.00", EstadoPago.CONFIRMADO)));

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

    @org.junit.jupiter.api.Test
    void elDestinoDeUnReembolsoEnLineaEsCortoYNoSeCorta() {
        java.util.function.BiFunction<MetodoPago, String, String> destino = (m, detalle) -> ReservaService.destinoEnLinea(
                new Pago(1L, 1L, java.math.BigDecimal.TEN, m, TipoPago.ANTICIPO, EstadoPago.CONFIRMADO, null, "X",
                        java.time.LocalDateTime.now(), null, "c", detalle, null, null));
        org.junit.jupiter.api.Assertions.assertEquals("Cuenta PayPal", destino.apply(MetodoPago.PAYPAL, "PayPal comprador-muy-largo@personal.example.com"));
        org.junit.jupiter.api.Assertions.assertEquals("Tarjeta **** 1111", destino.apply(MetodoPago.PAYPAL, "VISA **** 1111 (PayPal)"));
        org.junit.jupiter.api.Assertions.assertEquals("Tarjeta **** 4444", destino.apply(MetodoPago.TARJETA, "MASTERCARD **** 4444"));
        org.junit.jupiter.api.Assertions.assertEquals("Tarjeta del pago", destino.apply(MetodoPago.TARJETA, null));
        for (String d : new String[]{"Cuenta PayPal", "Tarjeta **** 1111", "Tarjeta del pago"})
            org.junit.jupiter.api.Assertions.assertTrue(d.length() <= 20);
    }
}
