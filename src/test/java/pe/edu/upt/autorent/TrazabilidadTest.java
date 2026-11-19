package pe.edu.upt.autorent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.upt.autorent.modelo.*;
import pe.edu.upt.autorent.repositorio.DevolucionRepositoryPort;
import pe.edu.upt.autorent.repositorio.PagoRepositoryPort;
import pe.edu.upt.autorent.servicio.contrato.*;
import pe.edu.upt.autorent.servicio.contrato.ConsultaReservaUseCase.DetalleReserva;
import pe.edu.upt.autorent.servicio.contrato.ConsultaReservaUseCase.ReporteCaja;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pruebas de trazabilidad: que cada operación deje registro de qué pasó, cuándo y quién lo hizo,
 * y de las reglas que cierran huecos lógicos (deuda, motivos obligatorios, evidencia de devoluciones).
 *
 * Igual que FlujosDeNegocioTest, no usa @Transactional (los eventos posteriores al COMMIT deben ejecutarse)
 * y tiene su propia base H2.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FlujosDeNegocioTest.ConfiguracionDePrueba.class)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:autorent_trazabilidad;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class TrazabilidadTest {

    private static final AtomicInteger SECUENCIA = new AtomicInteger(500);

    @Autowired private FlujosDeNegocioTest.RelojAjustable reloj;
    @Autowired private ReservaUseCase reservas;
    @Autowired private PagoEnLineaUseCase pagosEnLinea;
    @Autowired private ConsultaReservaUseCase consultas;
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
        return usuarios.registrarCliente("traza" + n, "Clave1234", "Cliente", "Traza",
                String.format("72%06d", n), "traza" + n + "@prueba.pe", "998" + String.format("%06d", n), "Tacna");
    }

    private Auto nuevoAuto() {
        int n = SECUENCIA.incrementAndGet();
        return autos.guardar(new Auto(null, "TRZ-" + n, "Kia", "Rio", 2024, "Compacto", "Rojo",
                new BigDecimal("100.00"), "Auto de prueba", EstadoAuto.DISPONIBLE));
    }

    private String codigoYape() { return "YP" + SECUENCIA.incrementAndGet(); }

    private static List<AccionReserva> acciones(DetalleReserva d) {
        return d.historial().stream().map(EventoReserva::accion).toList();
    }

    /** Alquiler completo en mostrador, con cargo por daños: todo queda en el historial y la deuda bloquea al cliente. */
    @Test
    void elAlquilerCompletoQuedaEnElHistorialYLaDeudaBloqueaNuevasReservas() {
        Usuario c = nuevoCliente(); Auto a = nuevoAuto();
        Reserva r = reservas.crearReservaPresencial(c.id(), a.id(), hoy(), hoy().plusDays(2), new BigDecimal("100.00"),
                MetodoPago.EFECTIVO, null, null, "999111222", "trabajador");
        reservas.registrarPagoSaldo(r.id(), new BigDecimal("100.00"), MetodoPago.EFECTIVO, null, null, null, "trabajador");
        reservas.entregarVehiculo(r.id(), "trabajador", "12000 km, tanque lleno");

        // Cargo sin concepto: no se acepta.
        assertThrows(IllegalArgumentException.class,
                () -> reservas.recibirVehiculo(r.id(), "trabajador", "Rayón", new BigDecimal("50.00"), " "));
        reservas.recibirVehiculo(r.id(), "trabajador", "12350 km, rayón en puerta", new BigDecimal("50.00"), "Rayón en puerta");

        Reserva finalizada = reservas.obtenerReserva(r.id());
        assertEquals(EstadoReserva.FINALIZADA, finalizada.estado());
        assertEquals(0, new BigDecimal("250.00").compareTo(finalizada.total()));
        assertEquals(0, new BigDecimal("50.00").compareTo(reservas.deudaDelCliente(c.id())));

        // Con deuda: no puede reservar otro auto ni se le puede desactivar la cuenta.
        Auto otro = nuevoAuto();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> reservas.crearReservaOnline(c.username(), otro.id(), hoy().plusDays(5), hoy().plusDays(6),
                        new BigDecimal("20.00"), MetodoPago.TARJETA, null, null, null));
        assertTrue(ex.getMessage().contains("deuda"));
        assertThrows(IllegalArgumentException.class, () -> usuarios.cambiarEstado(c.id(), false));

        // Paga la deuda: se libera.
        reservas.registrarPagoSaldo(r.id(), new BigDecimal("50.00"), MetodoPago.EFECTIVO, null, null, null, "trabajador");
        assertEquals(0, BigDecimal.ZERO.compareTo(reservas.deudaDelCliente(c.id())));

        DetalleReserva d = consultas.detalle(r.id(), "trabajador");
        assertFalse(d.tieneDeuda());
        assertEquals(0, new BigDecimal("250.00").compareTo(d.pagado()));
        assertEquals(List.of(AccionReserva.CREADA, AccionReserva.PAGO_CONFIRMADO, AccionReserva.COMPROBANTE_EMITIDO,
                        AccionReserva.PAGO_REGISTRADO, AccionReserva.PAGO_CONFIRMADO, AccionReserva.COMPROBANTE_EMITIDO,
                        AccionReserva.ENTREGA, AccionReserva.CARGO_ADICIONAL, AccionReserva.RECEPCION,
                        AccionReserva.PAGO_REGISTRADO, AccionReserva.PAGO_CONFIRMADO, AccionReserva.COMPROBANTE_EMITIDO),
                acciones(d));
        EventoReserva entrega = d.historial().stream().filter(e -> e.accion() == AccionReserva.ENTREGA).findFirst().orElseThrow();
        assertEquals("trabajador", entrega.usuario());
        assertEquals(EstadoReserva.CONFIRMADA, entrega.estadoAnterior());
        assertEquals(EstadoReserva.EN_CURSO, entrega.estadoNuevo());
        assertTrue(entrega.detalle().contains("12000 km"));
        // Tres pagos = tres boletas; la del cargo explica qué se cobra.
        assertEquals(3, d.comprobantes().size());
        assertTrue(d.comprobantes().get(2).descripcion().contains("Rayón en puerta"));
    }

    /** Rechazar un Yape exige motivo y deja separado quién registró el pago y quién lo rechazó. */
    @Test
    void elRechazoGuardaMotivoYQuienLoHizo() {
        Usuario c = nuevoCliente(); Auto a = nuevoAuto();
        Reserva r = reservas.crearReservaOnline(c.username(), a.id(), hoy().plusDays(4), hoy().plusDays(6),
                new BigDecimal("40.00"), MetodoPago.YAPE, "987654321", codigoYape(), null);
        Pago yape = pagos.findByReservaId(r.id()).get(0);
        assertThrows(IllegalArgumentException.class, () -> reservas.rechazarPago(yape.id(), "trabajador", ""));
        reservas.rechazarPago(yape.id(), "trabajador", "El Yape no llegó a la cuenta");

        Pago rechazado = pagos.findById(yape.id()).orElseThrow();
        assertEquals(EstadoPago.RECHAZADO, rechazado.estado());
        assertEquals(c.username(), rechazado.registradoPor());
        assertEquals("trabajador", rechazado.procesadoPor());
        assertNotNull(rechazado.procesadoEn());
        assertEquals("El Yape no llegó a la cuenta", rechazado.observacion());
        DetalleReserva d = consultas.detalle(r.id(), c.username());
        assertEquals(EstadoReserva.PENDIENTE_PAGO, d.reserva().estado());
        assertTrue(acciones(d).contains(AccionReserva.PAGO_RECHAZADO));
    }

    /** Una tarjeta rechazada no cobra nada, pero el intento queda en el historial (sin el número completo). */
    @Test
    void elIntentoDeTarjetaRechazadaQuedaRegistrado() {
        Usuario c = nuevoCliente(); Auto a = nuevoAuto();
        Reserva r = reservas.crearReservaOnline(c.username(), a.id(), hoy().plusDays(4), hoy().plusDays(5),
                new BigDecimal("20.00"), MetodoPago.TARJETA, null, null, null);
        Pago p = reservas.buscarPagoEnLineaPendiente(c.username(), r.id()).orElseThrow();
        ResultadoCobro cobro = pagosEnLinea.pagarConTarjeta(c.username(), p.id(),
                new DatosTarjeta("4000000000000002", "Cliente", "12/30", "123"));
        assertFalse(cobro.aprobado());
        EventoReserva fallo = consultas.detalle(r.id(), c.username()).historial().stream()
                .filter(e -> e.accion() == AccionReserva.PAGO_EN_LINEA_FALLIDO).findFirst().orElseThrow();
        assertTrue(fallo.detalle().contains("**** 0002"));
        assertFalse(fallo.detalle().contains("4000000000000002"));
    }

    /** Anular exige motivo; una devolución manual solo se completa con el código de la operación como evidencia. */
    @Test
    void laAnulacionYLaDevolucionDejanEvidencia() {
        Usuario c = nuevoCliente(); Auto a = nuevoAuto();
        Reserva r = reservas.crearReservaOnline(c.username(), a.id(), hoy().plusDays(4), hoy().plusDays(6),
                new BigDecimal("40.00"), MetodoPago.YAPE, "987654322", codigoYape(), null);
        reservas.confirmarPago(pagos.findByReservaId(r.id()).get(0).id(), "trabajador");

        assertThrows(IllegalArgumentException.class, () -> reservas.cancelarReserva(r.id(), "trabajador", null));
        reservas.cancelarReserva(r.id(), "trabajador", "Auto chocado en el estacionamiento");

        Devolucion d = devoluciones.findByReservaId(r.id()).get(0);
        assertThrows(IllegalArgumentException.class, () -> reservas.completarDevolucion(d.id(), "trabajador", ""));
        reservas.completarDevolucion(d.id(), "trabajador", "dev-yape-77");
        Devolucion hecha = devoluciones.findById(d.id()).orElseThrow();
        assertEquals("DEV-YAPE-77", hecha.codigoOperacion());
        assertEquals("trabajador", hecha.completadoPor());

        DetalleReserva detalle = consultas.detalle(r.id(), "trabajador");
        assertTrue(acciones(detalle).containsAll(List.of(AccionReserva.ANULADA, AccionReserva.DEVOLUCION_REGISTRADA,
                AccionReserva.DEVOLUCION_COMPLETADA)));
        EventoReserva anulada = detalle.historial().stream().filter(e -> e.accion() == AccionReserva.ANULADA).findFirst().orElseThrow();
        assertTrue(anulada.detalle().contains("Auto chocado"));
        // La boleta del anticipo se anula con su nota de crédito.
        assertTrue(detalle.comprobantes().stream().anyMatch(cp -> cp.tipo() == TipoComprobante.NOTA_CREDITO));
    }

    /** No se desactiva a un cliente con una reserva en marcha. */
    @Test
    void noSeDesactivaUnClienteConReservaActiva() {
        Usuario c = nuevoCliente(); Auto a = nuevoAuto();
        reservas.crearReservaOnline(c.username(), a.id(), hoy().plusDays(3), hoy().plusDays(4),
                new BigDecimal("20.00"), MetodoPago.TARJETA, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> usuarios.cambiarEstado(c.id(), false));
    }

    /** La caja suma por método y estado, y el expediente solo lo ve el dueño o el personal. */
    @Test
    void laCajaYElExpedienteSeMuestranSoloAQuienCorresponde() throws Exception {
        Usuario c = nuevoCliente(); Auto a = nuevoAuto();
        Reserva r = reservas.crearReservaPresencial(c.id(), a.id(), hoy().plusDays(2), hoy().plusDays(3), new BigDecimal("30.00"),
                MetodoPago.EFECTIVO, null, null, "999111333", "trabajador");

        ReporteCaja caja = consultas.caja(hoy(), hoy(), MetodoPago.EFECTIVO, EstadoPago.CONFIRMADO);
        assertTrue(caja.pagos().stream().anyMatch(p -> p.reservaId().equals(r.id())));
        assertTrue(caja.pagos().stream().allMatch(p -> p.metodo() == MetodoPago.EFECTIVO && p.estado() == EstadoPago.CONFIRMADO));
        assertEquals(0, caja.totalConfirmado().compareTo(caja.totalPorMetodo().get(MetodoPago.EFECTIVO)));
        assertThrows(IllegalArgumentException.class, () -> consultas.caja(hoy(), hoy().minusDays(1), null, null));

        mvc.perform(get("/mis-reservas/" + r.id()).with(user(c.username()).roles("CLIENTE")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Historial de la reserva")))
                .andExpect(content().string(containsString("Reserva creada")));
        Usuario otro = nuevoCliente();
        mvc.perform(get("/mis-reservas/" + r.id()).with(user(otro.username()).roles("CLIENTE")))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error"));
        mvc.perform(get("/operaciones/reservas/" + r.id()).with(user("trabajador").roles("TRABAJADOR")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Comprobantes electrónicos")));
        mvc.perform(get("/operaciones/caja").with(user("trabajador").roles("TRABAJADOR")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Cobrado por método")));
        mvc.perform(get("/operaciones/caja").param("desde", "").param("hasta", "").param("metodo", "YAPE")
                        .with(user("trabajador").roles("TRABAJADOR")))
                .andExpect(status().isOk());
        mvc.perform(get("/operaciones/caja").with(user(c.username()).roles("CLIENTE")))
                .andExpect(status().isForbidden());
    }
}
