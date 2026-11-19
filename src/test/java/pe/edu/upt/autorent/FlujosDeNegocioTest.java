package pe.edu.upt.autorent;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.upt.autorent.modelo.*;
import pe.edu.upt.autorent.repositorio.DevolucionRepositoryPort;
import pe.edu.upt.autorent.repositorio.PagoRepositoryPort;
import pe.edu.upt.autorent.servicio.contrato.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pruebas de flujos completos de la unidad 2: pago en línea, boleta electrónica, reembolso, nota de crédito,
 * tareas automáticas y reglas nuevas.
 *
 * A diferencia de AutoRentIntegracionTest, esta clase NO usa @Transactional: cada operación se guarda de verdad
 * (COMMIT), para que se ejecuten los eventos posteriores (boleta, reembolso, nota de crédito).
 * Usa su propia base H2 y un reloj ajustable para "adelantar el tiempo" sin esperar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FlujosDeNegocioTest.ConfiguracionDePrueba.class)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:autorent_flujos;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class FlujosDeNegocioTest {

    /** Reloj que se puede adelantar en las pruebas. */
    static class RelojAjustable extends Clock {
        private final ZoneId zona = ZoneId.of("America/Lima");
        private Instant instante = Instant.now();
        void reiniciar() { instante = Instant.now(); }
        void avanzar(Duration d) { instante = instante.plus(d); }
        @Override public ZoneId getZone() { return zona; }
        @Override public Clock withZone(ZoneId z) { return this; }
        @Override public Instant instant() { return instante; }
    }

    @TestConfiguration
    static class ConfiguracionDePrueba {
        @Bean @Primary RelojAjustable relojDePrueba() { return new RelojAjustable(); }
    }

    private static final AtomicInteger SECUENCIA = new AtomicInteger(100);
    private static final DatosTarjeta VISA_OK = new DatosTarjeta("4111111111111111", "Cliente Prueba", "12/30", "123");

    @Autowired private RelojAjustable reloj;
    @Autowired private ReservaUseCase reservas;
    @Autowired private PagoEnLineaUseCase pagosEnLinea;
    @Autowired private ComprobanteUseCase comprobantes;
    @Autowired private AutoUseCase autos;
    @Autowired private UsuarioUseCase usuarios;
    @Autowired private PagoRepositoryPort pagos;
    @Autowired private DevolucionRepositoryPort devoluciones;
    @Autowired private MockMvc mvc;

    @BeforeEach void reiniciarReloj() { reloj.reiniciar(); }

    private LocalDate hoy() { return LocalDate.now(reloj); }

    private Usuario nuevoCliente() {
        int n = SECUENCIA.incrementAndGet();
        return usuarios.registrarCliente("cliente" + n, "Clave1234", "Cliente", "Prueba",
                String.format("71%06d", n), "cliente" + n + "@prueba.pe", "999" + String.format("%06d", n), "Tacna");
    }

    private Auto nuevoAuto() {
        int n = SECUENCIA.incrementAndGet();
        return autos.guardar(new Auto(null, "PRB-" + n, "Toyota", "Yaris", 2024, "Compacto", "Azul",
                new BigDecimal("100.00"), "Auto de prueba", EstadoAuto.DISPONIBLE));
    }

    private Pago pagoPendiente(Reserva r, String usuario) {
        return reservas.buscarPagoEnLineaPendiente(usuario, r.id()).orElseThrow();
    }

    private Reserva reservaConTarjeta(Usuario c, Auto a, int diasHastaInicio, int dias, String anticipo) {
        return reservas.crearReservaOnline(c.username(), a.id(), hoy().plusDays(diasHastaInicio),
                hoy().plusDays(diasHastaInicio + dias), new BigDecimal(anticipo), MetodoPago.TARJETA, null, null, null);
    }

    // ------------------------------------------------------------------------------------------

    @Test
    void pagoConTarjetaConfirmaLaReservaYEmiteBoleta() {
        Usuario c = nuevoCliente(); Auto a = nuevoAuto();
        Reserva r = reservaConTarjeta(c, a, 5, 3, "60.00");                      // total S/ 300
        assertEquals(EstadoReserva.PENDIENTE_PAGO, reservas.obtenerReserva(r.id()).estado());
        Pago pendiente = pagoPendiente(r, c.username());

        ResultadoCobro rechazo = pagosEnLinea.pagarConTarjeta(c.username(), pendiente.id(),
                new DatosTarjeta("4000000000009995", "Cliente", "12/30", "123"));
        assertFalse(rechazo.aprobado());
        assertEquals(EstadoPago.PENDIENTE, pagos.findById(pendiente.id()).orElseThrow().estado());

        ResultadoCobro cobro = pagosEnLinea.pagarConTarjeta(c.username(), pendiente.id(), VISA_OK);
        assertTrue(cobro.aprobado());
        Pago pagado = pagos.findById(pendiente.id()).orElseThrow();
        assertEquals(EstadoPago.CONFIRMADO, pagado.estado());
        assertEquals("VISA **** 1111", pagado.detalle());
        assertEquals(EstadoReserva.CONFIRMADA, reservas.obtenerReserva(r.id()).estado());

        Comprobante boleta = comprobantes.listarDeCliente(c.username()).get(0);
        assertEquals(TipoComprobante.BOLETA, boleta.tipo());
        assertEquals(EstadoComprobante.EMITIDO, boleta.estado());
        assertEquals(0, boleta.total().compareTo(new BigDecimal("60.00")));
        assertEquals(0, boleta.gravada().add(boleta.igv()).compareTo(boleta.total()));
        assertEquals(new BigDecimal("50.85"), boleta.gravada());
        assertThrows(IllegalArgumentException.class, () -> pagosEnLinea.pagarConTarjeta(c.username(), pendiente.id(), VISA_OK),
                "un pago ya cobrado no se puede volver a cobrar");
    }

    @Test
    void cancelarReembolsaLaTarjetaYEmiteNotaDeCredito() {
        Usuario c = nuevoCliente(); Auto a = nuevoAuto();
        Reserva r = reservaConTarjeta(c, a, 10, 2, "200.00");
        Pago p = pagoPendiente(r, c.username());
        pagosEnLinea.pagarConTarjeta(c.username(), p.id(), VISA_OK);

        reservas.cancelarReserva(r.id(), c.username(), null);

        Devolucion d = devoluciones.findByReservaId(r.id()).get(0);
        assertEquals(EstadoDevolucion.COMPLETADA, d.estado(), "la pasarela devuelve el dinero sin intervención del personal");
        assertEquals("SISTEMA", d.completadoPor());
        assertTrue(d.codigoOperacion().startsWith("SIMR-"));
        assertEquals("Tarjeta **** 1111", d.telefonoDestino());
        List<Comprobante> lista = comprobantes.listarDeCliente(c.username());
        Comprobante nota = lista.stream().filter(x -> x.tipo() == TipoComprobante.NOTA_CREDITO).findFirst().orElseThrow();
        Comprobante boleta = lista.stream().filter(x -> x.tipo() == TipoComprobante.BOLETA).findFirst().orElseThrow();
        assertEquals(boleta.id(), nota.referenciaId());
        assertEquals("BC01", nota.serie());
    }

    @Test
    void yapeConCodigoDeAprobacionSeConfirmaSoloYSeDevuelveSolo() {
        Usuario c = nuevoCliente(); Auto a = nuevoAuto();
        Reserva r = reservas.crearReservaOnline(c.username(), a.id(), hoy().plusDays(10), hoy().plusDays(12),
                new BigDecimal("60.00"), MetodoPago.YAPE_EN_LINEA, null, null, null);
        Pago p = pagoPendiente(r, c.username());
        // Códigos de prueba: incorrecto o sin saldo se rechazan y el pago sigue pendiente.
        assertFalse(pagosEnLinea.pagarConYape(c.username(), p.id(), "951234567", "000000").aprobado());
        assertFalse(pagosEnLinea.pagarConYape(c.username(), p.id(), "951234567", "111111").aprobado());
        assertFalse(pagosEnLinea.pagarConYape(c.username(), p.id(), "12345", "123456").aprobado());
        assertFalse(pagosEnLinea.pagarConYape(c.username(), p.id(), "951234567", "12ab56").aprobado());
        ResultadoCobro ok = pagosEnLinea.pagarConYape(c.username(), p.id(), "951234567", "482915");
        assertTrue(ok.aprobado());
        assertEquals(EstadoReserva.CONFIRMADA, reservas.obtenerReserva(r.id()).estado());
        assertThrows(IllegalArgumentException.class, () -> pagosEnLinea.pagarConYape(c.username(), p.id(), "951234567", "482915"));
        assertTrue(comprobantes.listarDeCliente(c.username()).stream().anyMatch(x -> x.tipo() == TipoComprobante.BOLETA));
        // Al cancelar, la pasarela devuelve el dinero al mismo Yape sin que intervenga la sucursal.
        reservas.cancelarReserva(r.id(), c.username(), null);
        Devolucion d = devoluciones.findByReservaId(r.id()).get(0);
        assertEquals(EstadoDevolucion.COMPLETADA, d.estado());
        assertTrue(d.codigoOperacion().startsWith("YSIMR-"));
        assertEquals("Yape *****4567", d.telefonoDestino());
    }

    @Test
    void pagoSaldoEnLineaYPresencialEmitenSusBoletas() {
        Usuario c = nuevoCliente(); Auto a = nuevoAuto();
        Reserva r = reservas.crearReservaPresencial(c.id(), a.id(), hoy().plusDays(3), hoy().plusDays(5),
                new BigDecimal("50.00"), MetodoPago.EFECTIVO, null, null, "999111222", "trabajador");
        Pago saldo = pagosEnLinea.iniciarPagoSaldo(c.username(), r.id());
        assertEquals(0, saldo.monto().compareTo(new BigDecimal("150.00")));
        assertEquals(saldo.id(), pagosEnLinea.iniciarPagoSaldo(c.username(), r.id()).id(), "no duplica el pago pendiente");
        pagosEnLinea.pagarConTarjeta(c.username(), saldo.id(), VISA_OK);
        assertEquals(2, comprobantes.listarDeCliente(c.username()).stream().filter(x -> x.tipo() == TipoComprobante.BOLETA).count());
        assertEquals(0, pagos.totalConfirmado(r.id()).compareTo(new BigDecimal("200.00")));
    }

    @Test
    void otroClienteNoPuedePagarNiVerElPagoAjeno() {
        Usuario duena = nuevoCliente(); Usuario intruso = nuevoCliente(); Auto a = nuevoAuto();
        Reserva r = reservaConTarjeta(duena, a, 5, 1, "20.00");
        Pago p = pagoPendiente(r, duena.username());
        assertThrows(IllegalArgumentException.class, () -> pagosEnLinea.pagarConTarjeta(intruso.username(), p.id(), VISA_OK));
        assertThrows(IllegalArgumentException.class, () -> pagosEnLinea.prepararCheckout(intruso.username(), p.id()));
        assertTrue(reservas.buscarPagoEnLineaPendiente(intruso.username(), r.id()).isEmpty());
    }

    @Test
    void elPersonalNoConfirmaPagosEnLineaAMano() {
        Usuario c = nuevoCliente(); Auto a = nuevoAuto();
        Reserva r = reservaConTarjeta(c, a, 5, 1, "20.00");
        Pago p = pagoPendiente(r, c.username());
        assertThrows(IllegalArgumentException.class, () -> reservas.confirmarPago(p.id(), "trabajador"));
    }

    @Test
    void unClienteNoPuedeAcapararAutosSinPagar() {
        Usuario c = nuevoCliente();
        reservaConTarjeta(c, nuevoAuto(), 5, 1, "20.00");
        reservaConTarjeta(c, nuevoAuto(), 5, 1, "20.00");
        Auto tercero = nuevoAuto();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> reservaConTarjeta(c, tercero, 5, 1, "20.00"));
        assertTrue(ex.getMessage().contains("esperando pago"));
    }

    @Test
    void pagoEnLineaAbandonadoSeAnulaYLaReservaVenceSinPago() {
        Usuario c = nuevoCliente(); Auto a = nuevoAuto();
        Reserva r = reservaConTarjeta(c, a, 5, 2, "40.00");
        Pago p = pagoPendiente(r, c.username());

        reloj.avanzar(Duration.ofMinutes(31));
        reservas.liberarPagosEnLineaAbandonados();
        assertEquals(EstadoPago.RECHAZADO, pagos.findById(p.id()).orElseThrow().estado());
        assertEquals(EstadoReserva.PENDIENTE_PAGO, reservas.obtenerReserva(r.id()).estado(), "todavía puede reintentar");

        reloj.avanzar(Duration.ofHours(24));
        reservas.vencerReservasSinPago();
        assertEquals(EstadoReserva.VENCIDA, reservas.obtenerReserva(r.id()).estado(), "el auto vuelve a quedar libre");
        assertDoesNotThrow(() -> reservaConTarjeta(nuevoCliente(), a, 4, 2, "40.00"));
    }

    @Test
    void unYapeEsperandoValidacionNoVenceAutomaticamente() {
        Usuario c = nuevoCliente(); Auto a = nuevoAuto();
        Reserva r = reservas.crearReservaOnline(c.username(), a.id(), hoy().plusDays(5), hoy().plusDays(6),
                new BigDecimal("20.00"), MetodoPago.YAPE, "999888777", "YP-" + SECUENCIA.incrementAndGet(), null);
        reloj.avanzar(Duration.ofHours(30));
        reservas.vencerReservasSinPago();
        assertEquals(EstadoReserva.PENDIENTE_VALIDACION, reservas.obtenerReserva(r.id()).estado());
    }

    @Test
    void clienteQueNoSePresentaPierdeElAnticipoYSeLeDevuelveElSaldo() {
        Usuario c = nuevoCliente(); Auto a = nuevoAuto();
        Reserva r = reservas.crearReservaPresencial(c.id(), a.id(), hoy().plusDays(1), hoy().plusDays(3),
                new BigDecimal("60.00"), MetodoPago.EFECTIVO, null, null, "999111222", "trabajador");
        reservas.registrarPagoSaldo(r.id(), new BigDecimal("140.00"), MetodoPago.EFECTIVO, null, null, null, "trabajador");

        reloj.avanzar(Duration.ofDays(3));
        reservas.marcarNoPresentados();

        assertEquals(EstadoReserva.NO_PRESENTADO, reservas.obtenerReserva(r.id()).estado());
        List<Devolucion> lista = devoluciones.findByReservaId(r.id());
        assertEquals(1, lista.size(), "solo se devuelve el saldo; el anticipo queda como penalidad");
        assertEquals(0, lista.get(0).monto().compareTo(new BigDecimal("140.00")));
    }

    @Test
    void devolucionTardiaGeneraCargoQueSeCobraConBoleta() {
        Usuario c = nuevoCliente(); Auto a = nuevoAuto();
        Reserva r = reservas.crearReservaPresencial(c.id(), a.id(), hoy(), hoy().plusDays(2),
                new BigDecimal("200.00"), MetodoPago.EFECTIVO, null, null, "999111222", "trabajador");
        reservas.entregarVehiculo(r.id(), "trabajador", "45000 km, tanque lleno");

        reloj.avanzar(Duration.ofDays(4));                                      // vuelve 2 días tarde
        reservas.recibirVehiculo(r.id(), "trabajador", "Sin daños", null, null);

        Reserva cerrada = reservas.obtenerReserva(r.id());
        assertEquals(EstadoReserva.FINALIZADA, cerrada.estado());
        assertEquals(0, cerrada.total().compareTo(new BigDecimal("400.00")));
        assertEquals(4, cerrada.dias());
        assertTrue(cerrada.observacion().contains("2 día(s) de retraso"));
        Pago cargo = reservas.registrarPagoSaldo(r.id(), new BigDecimal("200.00"), MetodoPago.EFECTIVO, null, null, null, "trabajador");
        assertEquals(EstadoPago.CONFIRMADO, cargo.estado());
        assertEquals(2, comprobantes.listarDeCliente(c.username()).size());
    }

    @Test
    void elClienteCancelaFueraDePlazoSoloSiNoHayDineroDePorMedio() {
        // Efectivo que aún no llevó a la sucursal: no hay nada que devolver, puede cancelar y el auto queda libre.
        Usuario c = nuevoCliente(); Auto a = nuevoAuto();
        Reserva enEfectivo = reservas.crearReservaOnline(c.username(), a.id(), hoy().plusDays(1), hoy().plusDays(2),
                new BigDecimal("50.00"), MetodoPago.EFECTIVO, null, null, "999111222");
        assertTrue(reservas.reservasCancelables(List.of(enEfectivo), reservas.listarPagosDeCliente(c.username())).contains(enEfectivo.id()));
        reservas.cancelarReserva(enEfectivo.id(), c.username(), null);
        assertEquals(EstadoReserva.CANCELADA, reservas.obtenerReserva(enEfectivo.id()).estado());
        // Yape enviado y por validar: el dinero pudo haber llegado, así que debe esperar a la sucursal.
        Reserva conYape = reservas.crearReservaOnline(c.username(), a.id(), hoy().plusDays(1), hoy().plusDays(2),
                new BigDecimal("50.00"), MetodoPago.YAPE, "999111222", "YAPE-TARDE-01", null);
        assertFalse(reservas.reservasCancelables(List.of(conYape), reservas.listarPagosDeCliente(c.username())).contains(conYape.id()));
        assertThrows(IllegalArgumentException.class, () -> reservas.cancelarReserva(conYape.id(), c.username(), null));
    }

    @Test
    void elPersonalPuedeAnularDentroDeLas24HorasPeroElClienteNo() {
        Usuario c = nuevoCliente(); Auto a = nuevoAuto();
        Reserva r = reservas.crearReservaPresencial(c.id(), a.id(), hoy().plusDays(1), hoy().plusDays(2),
                new BigDecimal("50.00"), MetodoPago.EFECTIVO, null, null, "999111222", "trabajador");
        assertThrows(IllegalArgumentException.class, () -> reservas.cancelarReserva(r.id(), c.username(), null));
        reservas.cancelarReserva(r.id(), "trabajador", "Auto averiado");
        assertEquals(EstadoReserva.CANCELADA, reservas.obtenerReserva(r.id()).estado());
        assertEquals(1, devoluciones.findByReservaId(r.id()).size());
    }

    @Test
    void elMantenimientoProgramadoRespetaSusFechasYEntraAlTallerCuandoVuelveElAuto() {
        Usuario c = nuevoCliente(); Auto a = nuevoAuto();
        Reserva r = reservas.crearReservaPresencial(c.id(), a.id(), hoy(), hoy().plusDays(1),
                new BigDecimal("100.00"), MetodoPago.EFECTIVO, null, null, "999111222", "trabajador");
        // Hoy no: el auto sale hoy en ese alquiler.
        assertThrows(IllegalArgumentException.class,
                () -> autos.iniciarMantenimiento(a.id(), "Cambio de aceite", hoy(), hoy()));
        // Desde mañana (el día en que vuelve) sí: queda PROGRAMADO y el auto sigue disponible hoy.
        autos.iniciarMantenimiento(a.id(), "Cambio de aceite", hoy().plusDays(1), hoy().plusDays(2));
        assertEquals(EstadoAuto.DISPONIBLE, autos.obtener(a.id()).estado());
        assertTrue(autos.fechasOcupadas(a.id()).stream().anyMatch(PeriodoOcupado::mantenimiento));
        // Nadie puede reservar los días del taller, ni otro mantenimiento encima.
        Usuario c2 = nuevoCliente();
        IllegalArgumentException choque = assertThrows(IllegalArgumentException.class, () -> reservas.crearReservaPresencial(
                c2.id(), a.id(), hoy().plusDays(2), hoy().plusDays(3), new BigDecimal("100.00"), MetodoPago.EFECTIVO, null, null, "999111222", "trabajador"));
        assertTrue(choque.getMessage().contains("mantenimiento"));
        assertThrows(IllegalArgumentException.class,
                () -> autos.iniciarMantenimiento(a.id(), "Revisión de frenos", hoy().plusDays(2), hoy().plusDays(4)));
        // La entrega de hoy sí se hace: programar el taller para mañana ya no bloquea el auto desde hoy.
        reservas.entregarVehiculo(r.id(), "trabajador", "45000 km, tanque lleno");
        // Llega el día de inicio con el auto todavía fuera: el taller espera.
        reloj.avanzar(Duration.ofDays(1));
        autos.activarMantenimientosProgramados();
        assertEquals(EstadoAuto.DISPONIBLE, autos.obtener(a.id()).estado());
        // El auto vuelve y entra al taller en la siguiente revisión automática.
        reservas.recibirVehiculo(r.id(), "trabajador", "45100 km, tanque lleno, sin daños", null, null);
        assertTrue(autos.activarMantenimientosProgramados() >= 1);
        assertEquals(EstadoAuto.MANTENIMIENTO, autos.obtener(a.id()).estado());
        // Mientras está en el taller se puede reservar para después del mantenimiento.
        Reserva despues = reservas.crearReservaPresencial(c2.id(), a.id(), hoy().plusDays(2), hoy().plusDays(3),
                new BigDecimal("100.00"), MetodoPago.EFECTIVO, null, null, "999111222", "trabajador");
        assertNotNull(despues.id());
        Long enTaller = autos.listarMantenimientos().stream()
                .filter(m -> m.autoId().equals(a.id()) && m.estado() == EstadoMantenimiento.EN_PROCESO).findFirst().orElseThrow().id();
        assertEquals("Mantenimiento finalizado. Vehículo disponible.", autos.finalizarMantenimiento(enTaller));
        assertEquals(EstadoAuto.DISPONIBLE, autos.obtener(a.id()).estado());
    }

    @Test
    void elPersonalLiberaUnaReservaConYapeSinValidarCuandoPasoSuInicio() {
        Usuario c = nuevoCliente(); Auto a = nuevoAuto();
        Reserva r = reservas.crearReservaOnline(c.username(), a.id(), hoy().plusDays(1), hoy().plusDays(2),
                new BigDecimal("40.00"), MetodoPago.YAPE, "999111222", "YAPE-VENCE-" + a.id(), null);
        // Antes del inicio no se puede liberar.
        assertThrows(IllegalArgumentException.class, () -> reservas.vencerReservaSinPago(r.id(), "trabajador"));
        reloj.avanzar(Duration.ofDays(2));
        // La tarea automática no toca un Yape por validar (el cliente pudo haber pagado): decide el personal.
        reservas.vencerReservasSinPago();
        assertEquals(EstadoReserva.PENDIENTE_VALIDACION, reservas.obtenerReserva(r.id()).estado());
        reservas.vencerReservaSinPago(r.id(), "trabajador");
        assertEquals(EstadoReserva.VENCIDA, reservas.obtenerReserva(r.id()).estado());
        assertTrue(pagos.findByReservaId(r.id()).stream().allMatch(p -> p.estado() == EstadoPago.RECHAZADO));
        assertTrue(autos.fechasOcupadas(a.id()).isEmpty());
    }

    @Test
    void unMantenimientoProgramadoSePuedeCancelarYLiberaSusFechas() {
        Auto a = nuevoAuto();
        autos.iniciarMantenimiento(a.id(), "Cambio de llantas", hoy().plusDays(10), hoy().plusDays(11));
        Long programado = autos.listarMantenimientos().stream()
                .filter(m -> m.autoId().equals(a.id()) && m.estado() == EstadoMantenimiento.PROGRAMADO).findFirst().orElseThrow().id();
        assertTrue(autos.finalizarMantenimiento(programado).startsWith("Mantenimiento programado cancelado"));
        assertTrue(autos.fechasOcupadas(a.id()).isEmpty());
        Usuario c = nuevoCliente();
        assertNotNull(reservas.crearReservaPresencial(c.id(), a.id(), hoy().plusDays(10), hoy().plusDays(11),
                new BigDecimal("100.00"), MetodoPago.EFECTIVO, null, null, "999111222", "trabajador").id());
        assertEquals(1, autos.fechasOcupadas(a.id()).size());
    }

    @Test
    void lasPantallasNuevasSeMuestranYElPdfDeLaBoletaSeDescarga() throws Exception {
        Usuario c = nuevoCliente(); Auto a = nuevoAuto();
        Reserva r = reservaConTarjeta(c, a, 6, 2, "40.00");
        Pago p = pagoPendiente(r, c.username());
        mvc.perform(get("/pagos/" + p.id() + "/pagar").with(user(c.username()).roles("CLIENTE")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Tarjetas de prueba")));
        mvc.perform(post("/pagos/" + p.id() + "/tarjeta").with(csrf()).with(user(c.username()).roles("CLIENTE"))
                        .param("numero", "4111 1111 1111 1111").param("titular", "Cliente Prueba")
                        .param("vencimiento", "12/30").param("cvv", "123"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/mis-reservas"))
                .andExpect(flash().attributeExists("ok"));
        mvc.perform(get("/mis-reservas").with(user(c.username()).roles("CLIENTE")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("B001-")));
        Comprobante boleta = comprobantes.listarDeCliente(c.username()).get(0);
        mvc.perform(get("/comprobantes/" + boleta.id() + "/pdf").with(user(c.username()).roles("CLIENTE")))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"));
        Usuario otro = nuevoCliente();
        mvc.perform(get("/comprobantes/" + boleta.id() + "/pdf").with(user(otro.username()).roles("CLIENTE")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/operaciones/comprobantes").with(user("trabajador").roles("TRABAJADOR")))
                .andExpect(status().isOk());
        mvc.perform(get("/operaciones/reservas").with(user("trabajador").roles("TRABAJADOR")))
                .andExpect(status().isOk());
        mvc.perform(get("/pagos/" + p.id() + "/pagar").with(user("trabajador").roles("TRABAJADOR")))
                .andExpect(status().isForbidden());
    }
}
