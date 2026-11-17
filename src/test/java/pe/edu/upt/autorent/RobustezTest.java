package pe.edu.upt.autorent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import pe.edu.upt.autorent.modelo.*;
import pe.edu.upt.autorent.repositorio.*;
import pe.edu.upt.autorent.servicio.contrato.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de robustez del negocio completo:
 * - PayPal de punta a punta (con un PayPal falso que se puede programar).
 * - Concurrencia real: varios hilos reservando el mismo auto o pagando el mismo pago a la vez.
 * - Reglas en sus bordes.
 * - Simulación aleatoria de cientos de operaciones y verificación de que los datos siguen siendo coherentes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({FlujosDeNegocioTest.ConfiguracionDePrueba.class, RobustezTest.ConfiguracionPayPal.class})
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:autorent_robustez;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class RobustezTest {

    /** PayPal falso: aprueba, rechaza o cobra un monto distinto según se programe. Registra los reembolsos. */
    static class PayPalFalso implements PayPalPort {
        enum Modo { APROBAR, RECHAZAR, MONTO_DISTINTO }
        volatile Modo modo = Modo.APROBAR;
        final Map<String, BigDecimal> ordenes = new ConcurrentHashMap<>();
        final List<String> reembolsos = Collections.synchronizedList(new ArrayList<>());
        private final AtomicInteger secuencia = new AtomicInteger();

        @Override public boolean configurado() { return true; }
        @Override public String clientId() { return "falso"; }
        @Override public String moneda() { return "USD"; }
        @Override public BigDecimal convertir(BigDecimal soles) { return soles.divide(new BigDecimal("3.75"), 2, RoundingMode.HALF_UP); }
        @Override public OrdenEnLinea crearOrden(String ref, BigDecimal soles, String desc) {
            String id = "ORD-" + secuencia.incrementAndGet();
            ordenes.put(id, convertir(soles));
            return new OrdenEnLinea(id, convertir(soles), "USD");
        }
        @Override public ResultadoCobro capturar(String ordenId) {
            BigDecimal monto = ordenes.get(ordenId);
            return switch (modo) {
                case RECHAZAR -> ResultadoCobro.rechazado("PayPal rechazó el medio de pago. Elija otro.");
                case MONTO_DISTINTO -> new ResultadoCobro(true, "CAP-" + ordenId, MetodoPago.PAYPAL, "PayPal x@y.pe",
                        "ok", monto.subtract(new BigDecimal("1.00")), "USD");
                case APROBAR -> new ResultadoCobro(true, "CAP-" + ordenId, MetodoPago.PAYPAL, "PayPal x@y.pe", "ok", monto, "USD");
            };
        }
        @Override public ResultadoReembolso reembolsar(String capturaId, BigDecimal soles) {
            reembolsos.add(capturaId);
            return new ResultadoReembolso(true, "REF-" + capturaId, "ok");
        }
    }

    @TestConfiguration
    static class ConfiguracionPayPal {
        @Bean @Primary PayPalFalso payPalFalso() { return new PayPalFalso(); }
    }

    private static final AtomicInteger SEC = new AtomicInteger(1000);

    @Autowired private FlujosDeNegocioTest.RelojAjustable reloj;
    @Autowired private PayPalFalso payPal;
    @Autowired private ReservaUseCase reservas;
    @Autowired private PagoEnLineaUseCase pagosEnLinea;
    @Autowired private ConsultaReservaUseCase consultas;
    @Autowired private ComprobanteUseCase comprobantes;
    @Autowired private AutoUseCase autos;
    @Autowired private UsuarioUseCase usuarios;
    @Autowired private PagoRepositoryPort pagos;
    @Autowired private ReservaRepositoryPort reservasRepo;
    @Autowired private DevolucionRepositoryPort devoluciones;
    @Autowired private ComprobanteRepositoryPort comprobantesRepo;
    @Autowired private MockMvc mvc;

    @BeforeEach void preparar() { reloj.reiniciar(); payPal.modo = PayPalFalso.Modo.APROBAR; }

    private LocalDate hoy() { return LocalDate.now(reloj); }

    private Usuario cliente() {
        int n = SEC.incrementAndGet();
        return usuarios.registrarCliente("robusto" + n, "Clave1234", "Cliente", "Robusto " + n,
                String.format("71%06d", n), "robusto" + n + "@prueba.pe", "997" + String.format("%06d", n), "Tacna");
    }

    private Auto auto(String tarifa) {
        int n = SEC.incrementAndGet();
        return autos.guardar(new Auto(null, "RB-" + n, "Toyota", "Yaris", 2023, "Sedán", "Blanco",
                new BigDecimal(tarifa), "Prueba", EstadoAuto.DISPONIBLE));
    }

    private String yape() { return "OP" + SEC.incrementAndGet(); }

    private static final DatosTarjeta VISA_OK = new DatosTarjeta("4111111111111111", "Cliente", "12/30", "123");

    // ---------------------------------------------------------------- PayPal de punta a punta

    @Test
    void payPalAprobadoConfirmaEmiteBoletaYAlCancelarSeReembolsaConNotaDeCredito() {
        Usuario c = cliente(); Auto a = auto("75.00");
        Reserva r = reservas.crearReservaOnline(c.username(), a.id(), hoy().plusDays(5), hoy().plusDays(7),
                new BigDecimal("150.00"), MetodoPago.TARJETA, null, null, null);
        Pago p = reservas.buscarPagoEnLineaPendiente(c.username(), r.id()).orElseThrow();

        OrdenEnLinea orden = pagosEnLinea.crearOrdenPayPal(c.username(), p.id());
        assertEquals(0, new BigDecimal("40.00").compareTo(orden.monto()));
        // Otra orden distinta a la creada para este pago no se acepta.
        assertThrows(IllegalArgumentException.class, () -> pagosEnLinea.capturarPayPal(c.username(), p.id(), "ORD-INVENTADA"));
        ResultadoCobro cobro = pagosEnLinea.capturarPayPal(c.username(), p.id(), orden.id());
        assertTrue(cobro.aprobado());
        // Capturar dos veces no cobra dos veces.
        assertThrows(IllegalArgumentException.class, () -> pagosEnLinea.capturarPayPal(c.username(), p.id(), orden.id()));

        Pago pagado = pagos.findById(p.id()).orElseThrow();
        assertEquals(EstadoPago.CONFIRMADO, pagado.estado());
        assertEquals(MetodoPago.PAYPAL, pagado.metodo());
        assertEquals("CAP-" + orden.id(), pagado.codigoOperacion());
        assertEquals(EstadoReserva.CONFIRMADA, reservas.obtenerReserva(r.id()).estado());
        assertEquals(1, comprobantesRepo.findByReservaId(r.id()).size());

        reservas.cancelarReserva(r.id(), c.username(), null);
        assertTrue(payPal.reembolsos.contains("CAP-" + orden.id()));
        Devolucion d = devoluciones.findByReservaId(r.id()).get(0);
        assertEquals(EstadoDevolucion.COMPLETADA, d.estado());
        assertEquals("REF-CAP-" + orden.id(), d.codigoOperacion());
        assertTrue(comprobantesRepo.findByReservaId(r.id()).stream().anyMatch(x -> x.tipo() == TipoComprobante.NOTA_CREDITO));
    }

    /**
     * La dirección del script de PayPal debe llegar intacta al navegador. Antes, "&currency" sin escapar
     * se leía en HTML como el carácter ¤ y PayPal rechazaba el client-id (solo se notaba con PayPal real).
     */
    @Test
    void laPantallaDePagoCargaElScriptDePayPalConLaDireccionCorrecta() throws Exception {
        Usuario c = cliente(); Auto a = auto("50.00");
        Reserva r = reservas.crearReservaOnline(c.username(), a.id(), hoy().plusDays(5), hoy().plusDays(6),
                new BigDecimal("50.00"), MetodoPago.TARJETA, null, null, null);
        Pago p = reservas.buscarPagoEnLineaPendiente(c.username(), r.id()).orElseThrow();
        String html = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/pagos/" + p.id() + "/pagar")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(c.username()).roles("CLIENTE")))
                .andReturn().getResponse().getContentAsString();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("src=\"(https://www\\.paypal\\.com/sdk/js[^\"]*)\"").matcher(html);
        assertTrue(m.find(), "Debe estar el script de PayPal");
        String src = org.springframework.web.util.HtmlUtils.htmlUnescape(m.group(1));   // lo que interpreta el navegador
        assertTrue(src.contains("client-id=falso&currency=USD"), "Dirección mal formada: " + src);
        assertFalse(src.contains("¤"));
    }

    @Test
    void payPalQueCobraOtroMontoSeDevuelveYNoConfirmaNada() {
        Usuario c = cliente(); Auto a = auto("80.00");
        Reserva r = reservas.crearReservaOnline(c.username(), a.id(), hoy().plusDays(5), hoy().plusDays(6),
                new BigDecimal("80.00"), MetodoPago.TARJETA, null, null, null);
        Pago p = reservas.buscarPagoEnLineaPendiente(c.username(), r.id()).orElseThrow();
        OrdenEnLinea orden = pagosEnLinea.crearOrdenPayPal(c.username(), p.id());
        payPal.modo = PayPalFalso.Modo.MONTO_DISTINTO;
        ResultadoCobro cobro = pagosEnLinea.capturarPayPal(c.username(), p.id(), orden.id());
        assertFalse(cobro.aprobado());
        assertTrue(payPal.reembolsos.contains("CAP-" + orden.id()));
        assertEquals(EstadoPago.PENDIENTE, pagos.findById(p.id()).orElseThrow().estado());
        assertEquals(EstadoReserva.PENDIENTE_PAGO, reservas.obtenerReserva(r.id()).estado());
        assertTrue(comprobantesRepo.findByReservaId(r.id()).isEmpty(), "Sin cobro válido no hay boleta");

        payPal.modo = PayPalFalso.Modo.RECHAZAR;
        assertFalse(pagosEnLinea.capturarPayPal(c.username(), p.id(), orden.id()).aprobado());
        // Después de un rechazo puede pagar con tarjeta.
        assertTrue(pagosEnLinea.pagarConTarjeta(c.username(), p.id(), VISA_OK).aprobado());
    }

    // ---------------------------------------------------------------- Concurrencia

    @Test
    void diezPersonasReservandoElMismoAutoALaVezSoloUnaLoConsigue() throws Exception {
        Auto a = auto("50.00");
        List<Usuario> clientes = new ArrayList<>();
        for (int i = 0; i < 10; i++) clientes.add(cliente());
        LocalDate ini = hoy().plusDays(10), fin = hoy().plusDays(12);
        ExecutorService hilos = Executors.newFixedThreadPool(10);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Boolean>> resultados = new ArrayList<>();
        for (Usuario c : clientes) {
            resultados.add(hilos.submit(() -> {
                salida.await();
                try {
                    reservas.crearReservaOnline(c.username(), a.id(), ini, fin, new BigDecimal("20.00"),
                            MetodoPago.TARJETA, null, null, null);
                    return true;
                } catch (IllegalArgumentException ex) { return false; }
            }));
        }
        salida.countDown();
        int exitos = 0;
        for (Future<Boolean> f : resultados) if (f.get(60, TimeUnit.SECONDS)) exitos++;
        hilos.shutdown();
        assertEquals(1, exitos, "Solo una reserva puede quedarse con el auto en esas fechas");
        long activas = reservasRepo.findAll().stream()
                .filter(r -> r.auto().id().equals(a.id()) && EstadoReserva.BLOQUEAN_DISPONIBILIDAD.contains(r.estado())).count();
        assertEquals(1, activas);
    }

    @Test
    void unClienteEnviandoSeisReservasALaVezNoSuperaElLimiteDePendientes() throws Exception {
        Usuario c = cliente();
        List<Auto> flota = new ArrayList<>();
        for (int i = 0; i < 6; i++) flota.add(auto("30.00"));
        ExecutorService hilos = Executors.newFixedThreadPool(6);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Boolean>> res = new ArrayList<>();
        for (Auto a : flota) res.add(hilos.submit(() -> {
            salida.await();
            try {
                reservas.crearReservaOnline(c.username(), a.id(), hoy().plusDays(9), hoy().plusDays(10),
                        new BigDecimal("6.00"), MetodoPago.TARJETA, null, null, null);
                return true;
            } catch (IllegalArgumentException ex) { return false; }
        }));
        salida.countDown();
        int creadas = 0;
        for (Future<Boolean> f : res) if (f.get(60, TimeUnit.SECONDS)) creadas++;
        hilos.shutdown();
        assertEquals(2, creadas, "Un cliente no puede tener más de 2 reservas esperando pago, aunque envíe todo a la vez");
    }

    @Test
    void elPersonalConfirmandoElMismoYapeDosVecesALaVezSoloLoConfirmaUnaVez() throws Exception {
        Usuario c = cliente(); Auto a = auto("70.00");
        Reserva r = reservas.crearReservaOnline(c.username(), a.id(), hoy().plusDays(5), hoy().plusDays(6),
                new BigDecimal("70.00"), MetodoPago.YAPE, "987654321", yape(), null);
        Long pagoId = pagos.findByReservaId(r.id()).get(0).id();
        ExecutorService hilos = Executors.newFixedThreadPool(4);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Boolean>> res = new ArrayList<>();
        for (int i = 0; i < 4; i++) res.add(hilos.submit(() -> {
            salida.await();
            try { reservas.confirmarPago(pagoId, "trabajador"); return true; }
            catch (IllegalArgumentException ex) { return false; }
        }));
        salida.countDown();
        int confirmados = 0;
        for (Future<Boolean> f : res) if (f.get(60, TimeUnit.SECONDS)) confirmados++;
        hilos.shutdown();
        assertEquals(1, confirmados);
        assertEquals(1, comprobantesRepo.findByReservaId(r.id()).size(), "Una sola boleta");
    }

    @Test
    void elMismoPagoEnviadoDosVecesALaVezSoloSeCobraUnaVez() throws Exception {
        Usuario c = cliente(); Auto a = auto("60.00");
        Reserva r = reservas.crearReservaOnline(c.username(), a.id(), hoy().plusDays(5), hoy().plusDays(6),
                new BigDecimal("60.00"), MetodoPago.TARJETA, null, null, null);
        Pago p = reservas.buscarPagoEnLineaPendiente(c.username(), r.id()).orElseThrow();
        ExecutorService hilos = Executors.newFixedThreadPool(4);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Boolean>> res = new ArrayList<>();
        for (int i = 0; i < 4; i++) res.add(hilos.submit(() -> {
            salida.await();
            try { return pagosEnLinea.pagarConTarjeta(c.username(), p.id(), VISA_OK).aprobado(); }
            catch (IllegalArgumentException ex) { return false; }
        }));
        salida.countDown();
        int aprobados = 0;
        for (Future<Boolean> f : res) if (f.get(60, TimeUnit.SECONDS)) aprobados++;
        hilos.shutdown();
        assertEquals(1, aprobados, "Un doble clic no debe cobrar dos veces");
        assertEquals(1, comprobantesRepo.findByReservaId(r.id()).size());
    }

    @Test
    void boletasEmitidasEnParaleloTienenNumerosCorrelativosSinHuecosNiRepetidos() throws Exception {
        int antes = comprobantesRepo.findAll().size();
        List<Usuario> cs = new ArrayList<>(); List<Auto> as = new ArrayList<>();
        for (int i = 0; i < 8; i++) { cs.add(cliente()); as.add(auto("40.00")); }
        ExecutorService hilos = Executors.newFixedThreadPool(8);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            int k = i;
            fs.add(hilos.submit(() -> {
                salida.await();
                return reservas.crearReservaPresencial(cs.get(k).id(), as.get(k).id(), hoy().plusDays(3), hoy().plusDays(4),
                        new BigDecimal("40.00"), MetodoPago.EFECTIVO, null, null, "999000111", "trabajador");
            }));
        }
        salida.countDown();
        for (Future<?> f : fs) f.get(60, TimeUnit.SECONDS);
        hilos.shutdown();
        assertEquals(antes + 8, comprobantesRepo.findAll().size());
        verificarNumeracion(comprobantesRepo.findAll());
    }

    // ---------------------------------------------------------------- Reglas en sus bordes

    @Test
    void reglasEnLosBordes() {
        Usuario c = cliente(); Auto a = auto("100.00");
        String u = c.username();
        // Fechas: no en el pasado, fin después del inicio.
        assertThrows(IllegalArgumentException.class, () -> reservas.crearReservaOnline(u, a.id(), hoy().minusDays(1), hoy().plusDays(1),
                new BigDecimal("40.00"), MetodoPago.TARJETA, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> reservas.crearReservaOnline(u, a.id(), hoy().plusDays(3), hoy().plusDays(3),
                new BigDecimal("40.00"), MetodoPago.TARJETA, null, null, null));
        // Anticipo: no más que el total, no negativo, no menos del 20 %.
        assertThrows(IllegalArgumentException.class, () -> reservas.crearReservaOnline(u, a.id(), hoy().plusDays(3), hoy().plusDays(4),
                new BigDecimal("100.01"), MetodoPago.TARJETA, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> reservas.crearReservaOnline(u, a.id(), hoy().plusDays(3), hoy().plusDays(4),
                new BigDecimal("-5.00"), MetodoPago.TARJETA, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> reservas.crearReservaOnline(u, a.id(), hoy().plusDays(3), hoy().plusDays(4),
                new BigDecimal("19.99"), MetodoPago.TARJETA, null, null, null));
        // Reserva web pagando en efectivo en la sucursal: exige un celular para una posible devolución.
        assertThrows(IllegalArgumentException.class, () -> reservas.crearReservaOnline(u, a.id(), hoy().plusDays(3), hoy().plusDays(4),
                new BigDecimal("20.00"), MetodoPago.EFECTIVO, null, null, null));
        // Reserva que empieza hoy: sí se permite.
        Reserva hoyMismo = reservas.crearReservaOnline(u, a.id(), hoy(), hoy().plusDays(1),
                new BigDecimal("20.00"), MetodoPago.TARJETA, null, null, null);
        assertNotNull(hoyMismo.id());
        // Auto inactivo o en mantenimiento: no se reserva.
        Auto inactivo = auto("100.00");
        autos.cambiarEstado(inactivo.id(), EstadoAuto.INACTIVO);
        assertThrows(IllegalArgumentException.class, () -> reservas.crearReservaOnline(cliente().username(), inactivo.id(),
                hoy().plusDays(3), hoy().plusDays(4), new BigDecimal("20.00"), MetodoPago.TARJETA, null, null, null));

        // Saldo: no se puede pagar de más ni entregar sin pagar todo.
        Usuario c2 = cliente(); Auto b = auto("100.00");
        Reserva r = reservas.crearReservaPresencial(c2.id(), b.id(), hoy(), hoy().plusDays(2), new BigDecimal("50.00"),
                MetodoPago.EFECTIVO, null, null, "999111222", "trabajador");
        assertThrows(IllegalArgumentException.class, () -> reservas.entregarVehiculo(r.id(), "trabajador", "ok"));
        assertThrows(IllegalArgumentException.class, () -> reservas.registrarPagoSaldo(r.id(), new BigDecimal("150.01"),
                MetodoPago.EFECTIVO, null, null, null, "trabajador"));
        reservas.registrarPagoSaldo(r.id(), new BigDecimal("150.00"), MetodoPago.EFECTIVO, null, null, null, "trabajador");
        assertThrows(IllegalArgumentException.class, () -> reservas.registrarPagoSaldo(r.id(), new BigDecimal("0.01"),
                MetodoPago.EFECTIVO, null, null, null, "trabajador"));
        reservas.entregarVehiculo(r.id(), "trabajador", "ok");
        // En curso: no se cancela ni se entrega otra vez; no se recibe dos veces.
        assertThrows(IllegalArgumentException.class, () -> reservas.cancelarReserva(r.id(), "trabajador", "x"));
        assertThrows(IllegalArgumentException.class, () -> reservas.entregarVehiculo(r.id(), "trabajador", "ok"));
        assertThrows(IllegalArgumentException.class, () -> reservas.recibirVehiculo(r.id(), "trabajador", "ok",
                new BigDecimal("-1"), "x"));
        reservas.recibirVehiculo(r.id(), "trabajador", "ok", null, null);
        assertThrows(IllegalArgumentException.class, () -> reservas.recibirVehiculo(r.id(), "trabajador", "ok", null, null));
        assertThrows(IllegalArgumentException.class, () -> reservas.cancelarReserva(r.id(), "trabajador", "x"));
        // Un Yape con el mismo código no se registra dos veces.
        String codigo = yape();
        reservas.crearReservaOnline(cliente().username(), auto("50.00").id(), hoy().plusDays(3), hoy().plusDays(4),
                new BigDecimal("10.00"), MetodoPago.YAPE, "987654321", codigo, null);
        assertThrows(RuntimeException.class, () -> reservas.crearReservaOnline(cliente().username(), auto("50.00").id(),
                hoy().plusDays(3), hoy().plusDays(4), new BigDecimal("10.00"), MetodoPago.YAPE, "987654321", codigo, null));
    }

    @Test
    void retrasoYDaniosJuntosSumanBienYQuedanComoDeuda() {
        Usuario c = cliente(); Auto a = auto("90.00");
        Reserva r = reservas.crearReservaPresencial(c.id(), a.id(), hoy(), hoy().plusDays(1), new BigDecimal("90.00"),
                MetodoPago.EFECTIVO, null, null, "999111222", "trabajador");
        reservas.entregarVehiculo(r.id(), "trabajador", "ok");
        reloj.avanzar(Duration.ofDays(3));                         // devuelve 2 días tarde
        reservas.recibirVehiculo(r.id(), "trabajador", "faro roto", new BigDecimal("120.50"), "Faro delantero roto");
        Reserva f = reservas.obtenerReserva(r.id());
        // 90 (1 día) + 2 x 90 de retraso + 120.50 de daños = 390.50
        assertEquals(0, new BigDecimal("390.50").compareTo(f.total()));
        assertEquals(0, new BigDecimal("300.50").compareTo(reservas.deudaDelCliente(c.id())));
        assertEquals(2, consultas.detalle(r.id(), "trabajador").historial().stream()
                .filter(e -> e.accion() == AccionReserva.CARGO_ADICIONAL).count());
    }

    // ---------------------------------------------------------------- Aritmética de boletas

    @Test
    void elIgvSiempreCuadraYElMontoEnLetrasEsCorrecto() throws Exception {
        var separar = Class.forName("pe.edu.upt.autorent.servicio.ComprobanteService").getDeclaredMethod("separarIgv", BigDecimal.class);
        separar.setAccessible(true);
        Random azar = new Random(7);
        for (int i = 0; i < 20_000; i++) {
            BigDecimal total = BigDecimal.valueOf(1 + azar.nextInt(5_000_000), 2);   // de 0.01 a 50 000.00
            BigDecimal[] partes = (BigDecimal[]) separar.invoke(null, total);
            assertEquals(0, partes[0].add(partes[1]).compareTo(total), "gravada + IGV = total para " + total);
            BigDecimal igvTeorico = partes[0].multiply(new BigDecimal("0.18"));
            assertTrue(partes[1].subtract(igvTeorico).abs().compareTo(new BigDecimal("0.01")) <= 0, "IGV ≈ 18 % para " + total);
            assertTrue(partes[0].signum() >= 0 && partes[1].signum() >= 0);
        }
        assertEquals("CERO CON 01/100 SOLES", MontoEnLetras.soles(new BigDecimal("0.01")));
        assertEquals("UNO CON 00/100 SOLES", MontoEnLetras.soles(new BigDecimal("1.00")));
        assertEquals("VEINTIUNO CON 50/100 SOLES", MontoEnLetras.soles(new BigDecimal("21.50")));
        assertEquals("CIEN CON 00/100 SOLES", MontoEnLetras.soles(new BigDecimal("100")));
        assertEquals("CIENTO UNO CON 00/100 SOLES", MontoEnLetras.soles(new BigDecimal("101")));
        assertEquals("QUINIENTOS CON 00/100 SOLES", MontoEnLetras.soles(new BigDecimal("500")));
        assertEquals("MIL CON 00/100 SOLES", MontoEnLetras.soles(new BigDecimal("1000")));
        assertEquals("VEINTIÚN MIL CUATROCIENTOS CON 00/100 SOLES", MontoEnLetras.soles(new BigDecimal("21400")));
        assertEquals("CIENTO VEINTIÚN MIL CON 00/100 SOLES", MontoEnLetras.soles(new BigDecimal("121000")));
        assertEquals("UN MILLÓN CON 00/100 SOLES", MontoEnLetras.soles(new BigDecimal("1000000")));
    }

    // ---------------------------------------------------------------- Simulación aleatoria

    /**
     * Simula muchos días de operación con decisiones al azar (semilla fija, se puede repetir):
     * clientes que reservan, pagan bien o mal, cancelan; personal que valida, rechaza, anula, entrega,
     * recibe con o sin cargos; tareas automáticas que vencen y marcan no presentados.
     * Al final se verifica que los datos sigan siendo coherentes.
     */
    @Test
    void simulacionAleatoriaMantieneLaCoherenciaDeTodosLosDatos() {
        // Por defecto 1 corrida; con -Dautorent.simulacion.semillas=20 se repite con 20 semillas distintas.
        int semillas = Integer.getInteger("autorent.simulacion.semillas", 1);
        for (int i = 0; i < semillas; i++) {
            reloj.reiniciar();
            simular(20261116L + i);
        }
    }

    private void simular(long semilla) {
        Random azar = new Random(semilla);
        List<Usuario> clientes = new ArrayList<>();
        for (int i = 0; i < 6; i++) clientes.add(cliente());
        List<Auto> flota = new ArrayList<>();
        for (int i = 0; i < 4; i++) flota.add(auto(String.valueOf(40 + 15 * i) + ".00"));
        Set<Long> autosSimulados = flota.stream().map(Auto::id).collect(Collectors.toSet());
        List<DatosTarjeta> tarjetas = List.of(VISA_OK, new DatosTarjeta("5555555555554444", "C", "11/29", "321"),
                new DatosTarjeta("4000000000000002", "C", "12/30", "123"), new DatosTarjeta("4000000000009995", "C", "12/30", "123"));
        Map<String, Integer> conteo = new TreeMap<>();
        int rechazosDeNegocio = 0;

        for (int paso = 0; paso < 400; paso++) {
            int accion = azar.nextInt(13);
            Usuario c = clientes.get(azar.nextInt(clientes.size()));
            Auto a = flota.get(azar.nextInt(flota.size()));
            List<Reserva> todas = reservasRepo.findAll().stream().filter(r -> autosSimulados.contains(r.auto().id())).toList();
            // Se elige una reserva en un estado donde la acción tiene sentido (a veces una cualquiera, para probar rechazos).
            EnumSet<EstadoReserva> utiles = switch (accion) {
                case 3, 12 -> EnumSet.of(EstadoReserva.PENDIENTE_PAGO);
                case 5 -> EnumSet.of(EstadoReserva.CONFIRMADA, EstadoReserva.FINALIZADA);
                case 6, 8, 9 -> EnumSet.of(EstadoReserva.CONFIRMADA);
                case 7 -> EnumSet.of(EstadoReserva.EN_CURSO);
                default -> EnumSet.allOf(EstadoReserva.class);
            };
            List<Reserva> candidatas = azar.nextInt(5) == 0 ? todas : todas.stream().filter(r -> utiles.contains(r.estado())).toList();
            Reserva alguna = candidatas.isEmpty() ? null : candidatas.get(azar.nextInt(candidatas.size()));
            try {
                switch (accion) {
                    case 0, 1 -> {            // reserva web con tarjeta o Yape
                        LocalDate ini = hoy().plusDays(azar.nextInt(5));
                        LocalDate fin = ini.plusDays(1 + azar.nextInt(4));
                        long dias = fin.toEpochDay() - ini.toEpochDay();
                        BigDecimal total = a.tarifaDiaria().multiply(BigDecimal.valueOf(dias));
                        BigDecimal anticipo = total.multiply(BigDecimal.valueOf(20 + azar.nextInt(81)))
                                .divide(BigDecimal.valueOf(100), 2, RoundingMode.UP).min(total);
                        if (accion == 0) reservas.crearReservaOnline(c.username(), a.id(), ini, fin, anticipo, MetodoPago.TARJETA, null, null, null);
                        else reservas.crearReservaOnline(c.username(), a.id(), ini, fin, anticipo, MetodoPago.YAPE, "987654321", yape(), null);
                        conteo.merge("reserva web", 1, Integer::sum);
                    }
                    case 2 -> {               // reserva en mostrador
                        LocalDate ini = hoy().plusDays(azar.nextInt(3));
                        LocalDate fin = ini.plusDays(1 + azar.nextInt(3));
                        BigDecimal total = a.tarifaDiaria().multiply(BigDecimal.valueOf(fin.toEpochDay() - ini.toEpochDay()));
                        reservas.crearReservaPresencial(c.id(), a.id(), ini, fin, azar.nextBoolean() ? total : total.multiply(new BigDecimal("0.5")).setScale(2, RoundingMode.UP),
                                MetodoPago.EFECTIVO, null, null, "999111222", "trabajador");
                        conteo.merge("reserva mostrador", 1, Integer::sum);
                    }
                    case 3 -> {               // pagar en línea un pago pendiente (tarjeta buena o mala)
                        if (alguna == null) break;
                        Optional<Pago> p = reservas.buscarPagoEnLineaPendiente(alguna.cliente().username(), alguna.id());
                        if (p.isPresent()) {
                            pagosEnLinea.pagarConTarjeta(alguna.cliente().username(), p.get().id(), tarjetas.get(azar.nextInt(tarjetas.size())));
                            conteo.merge("pago tarjeta", 1, Integer::sum);
                        }
                    }
                    case 4 -> {               // validar o rechazar un Yape pendiente
                        Optional<Pago> yp = pagos.findAll().stream().filter(p -> p.estado() == EstadoPago.PENDIENTE
                                && p.metodo() == MetodoPago.YAPE).findAny();
                        if (yp.isPresent()) {
                            if (azar.nextInt(4) > 0) reservas.confirmarPago(yp.get().id(), "trabajador");
                            else reservas.rechazarPago(yp.get().id(), "trabajador", "No llegó");
                            conteo.merge("validar yape", 1, Integer::sum);
                        }
                    }
                    case 5 -> {               // cobrar el saldo en efectivo
                        if (alguna == null) break;
                        BigDecimal saldo = alguna.total().subtract(pagos.totalConfirmado(alguna.id()));
                        if (saldo.signum() > 0) {
                            reservas.registrarPagoSaldo(alguna.id(), saldo, MetodoPago.EFECTIVO, null, null, null, "trabajador");
                            conteo.merge("saldo efectivo", 1, Integer::sum);
                        }
                    }
                    case 6 -> {               // entregar
                        if (alguna == null) break;
                        reservas.entregarVehiculo(alguna.id(), "trabajador", "ok");
                        conteo.merge("entrega", 1, Integer::sum);
                    }
                    case 7 -> {               // recibir, a veces con daños
                        if (alguna == null) break;
                        boolean danios = azar.nextInt(3) == 0;
                        reservas.recibirVehiculo(alguna.id(), "trabajador", "ok",
                                danios ? BigDecimal.valueOf(10 + azar.nextInt(200)) : null, danios ? "Daño" : null);
                        conteo.merge("recepcion", 1, Integer::sum);
                    }
                    case 8 -> {               // el cliente cancela
                        if (alguna == null) break;
                        reservas.cancelarReserva(alguna.id(), alguna.cliente().username(), null);
                        conteo.merge("cancelacion", 1, Integer::sum);
                    }
                    case 9 -> {               // el personal anula
                        if (alguna == null || azar.nextInt(3) > 0) break;
                        reservas.cancelarReserva(alguna.id(), "trabajador", "Simulación");
                        conteo.merge("anulacion", 1, Integer::sum);
                    }
                    case 10 -> {              // completar devoluciones manuales
                        devoluciones.findAll().stream().filter(d -> d.estado() == EstadoDevolucion.PENDIENTE).findAny()
                                .ifPresent(d -> { reservas.completarDevolucion(d.id(), "trabajador", "DEV" + SEC.incrementAndGet());
                                    conteo.merge("devolucion completada", 1, Integer::sum); });
                    }
                    case 11 -> {              // pasa el tiempo y corren las tareas automáticas
                        reloj.avanzar(Duration.ofHours(1 + azar.nextInt(30)));
                        reservas.liberarPagosEnLineaAbandonados();
                        reservas.vencerReservasSinPago();
                        reservas.marcarNoPresentados();
                        conteo.merge("tiempo + tareas", 1, Integer::sum);
                    }
                    case 12 -> {              // reintentar anticipo de una reserva sin pago vigente
                        if (alguna == null) break;
                        BigDecimal minimo = ReglasAlquiler.minimoAnticipo(alguna.total());
                        reservas.reintentarAnticipo(alguna.cliente().username(), alguna.id(), minimo, MetodoPago.TARJETA, null, null, null);
                        conteo.merge("reintento", 1, Integer::sum);
                    }
                    default -> { }
                }
            } catch (IllegalArgumentException ex) {
                rechazosDeNegocio++;          // la regla dijo que no: es correcto, se sigue
            }
        }
        System.out.println("Simulación semilla " + semilla + ": " + conteo + " | rechazos por reglas: " + rechazosDeNegocio);
        assertTrue(conteo.getOrDefault("entrega", 0) > 0 && conteo.getOrDefault("recepcion", 0) > 0
                && conteo.getOrDefault("cancelacion", 0) + conteo.getOrDefault("anulacion", 0) > 0, "La simulación debe recorrer el ciclo completo: " + conteo);
        verificarCoherencia(autosSimulados);
    }

    // ---------------------------------------------------------------- Verificaciones de coherencia

    private void verificarCoherencia(Set<Long> autosSimulados) {
        List<Reserva> rs = reservasRepo.findAll().stream().filter(r -> autosSimulados.contains(r.auto().id())).toList();
        List<Comprobante> cps = comprobantesRepo.findAll();
        for (Reserva r : rs) {
            List<Pago> ps = pagos.findByReservaId(r.id());
            BigDecimal pagado = ps.stream().filter(p -> p.estado() == EstadoPago.CONFIRMADO).map(Pago::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
            List<Devolucion> ds = devoluciones.findByReservaId(r.id());
            BigDecimal devuelto = ds.stream().map(Devolucion::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
            String id = "Reserva " + r.id() + " (" + r.estado() + ")";

            // 1. Nunca se cobra más que el total, ni se devuelve más de lo cobrado.
            assertTrue(pagado.compareTo(r.total()) <= 0, id + ": pagado " + pagado + " > total " + r.total());
            assertTrue(devuelto.compareTo(pagado) <= 0, id + ": devuelto " + devuelto + " > pagado " + pagado);
            // 2. Estado coherente con el dinero.
            if (r.estado() == EstadoReserva.CONFIRMADA)
                assertTrue(pagado.compareTo(ReglasAlquiler.minimoAnticipo(r.total())) >= 0, id + ": confirmada sin anticipo");
            if (r.estado() == EstadoReserva.EN_CURSO)
                assertEquals(0, pagado.compareTo(r.total()), id + ": entregada sin pagar todo");
            if (r.estado() == EstadoReserva.CANCELADA)
                assertEquals(0, devuelto.compareTo(pagado), id + ": cancelada sin devolver todo lo pagado");
            if (r.estado().esFinal())
                assertTrue(ps.stream().noneMatch(p -> p.estado() == EstadoPago.PENDIENTE), id + ": quedó un pago pendiente");
            // 3. Cada pago confirmado tiene exactamente una boleta por su mismo monto; ningún otro pago tiene boleta.
            for (Pago p : ps) {
                List<Comprobante> boletas = cps.stream().filter(x -> x.tipo() == TipoComprobante.BOLETA && p.id().equals(x.pagoId())).toList();
                if (p.estado() == EstadoPago.CONFIRMADO) {
                    assertEquals(1, boletas.size(), id + ": el pago " + p.id() + " confirmado debe tener 1 boleta");
                    assertEquals(0, boletas.get(0).total().compareTo(p.monto()), id + ": boleta con otro monto");
                    assertTrue(boletas.get(0).emitido(), id + ": boleta no emitida");
                } else {
                    assertTrue(boletas.isEmpty(), id + ": el pago " + p.id() + " " + p.estado() + " no debe tener boleta");
                }
                if (p.estado() != EstadoPago.PENDIENTE) {
                    assertNotNull(p.procesadoPor(), id + ": pago " + p.id() + " procesado sin responsable");
                    assertNotNull(p.procesadoEn(), id + ": pago " + p.id() + " procesado sin fecha");
                }
            }
            // 4. Cada devolución tiene su nota de crédito; las completadas tienen evidencia.
            for (Devolucion d : ds) {
                assertEquals(1, cps.stream().filter(x -> x.tipo() == TipoComprobante.NOTA_CREDITO && d.reservaId().equals(x.reservaId())
                        && d.pagoId().equals(x.pagoId())).count(), id + ": devolución " + d.id() + " sin su nota de crédito");
                if (d.estado() == EstadoDevolucion.COMPLETADA) assertNotNull(d.codigoOperacion(), id + ": devolución sin evidencia");
            }
            // 5. La bitácora empieza con la creación y su cadena de estados termina en el estado actual.
            List<EventoReserva> h = reservas.historialDe(r.id());
            assertFalse(h.isEmpty(), id + ": sin historial");
            assertEquals(AccionReserva.CREADA, h.get(0).accion(), id + ": el historial no empieza con la creación");
            EstadoReserva actual = null;
            for (EventoReserva e : h) {
                if (e.estadoNuevo() == null) continue;
                if (actual != null && e.estadoAnterior() != null)
                    assertEquals(actual, e.estadoAnterior(), id + ": salto de estado en el historial en " + e.accion());
                actual = e.estadoNuevo();
            }
            assertEquals(r.estado(), actual, id + ": el historial no termina en el estado actual");
        }
        // 6. Un auto nunca tiene dos reservas activas que se crucen, ni dos alquileres en curso.
        Map<Long, List<Reserva>> porAuto = rs.stream().filter(r -> EstadoReserva.BLOQUEAN_DISPONIBILIDAD.contains(r.estado()))
                .collect(Collectors.groupingBy(r -> r.auto().id()));
        for (List<Reserva> lista : porAuto.values()) {
            for (int i = 0; i < lista.size(); i++)
                for (int j = i + 1; j < lista.size(); j++) {
                    Reserva x = lista.get(i), y = lista.get(j);
                    boolean cruzan = x.fechaInicio().isBefore(y.fechaFin()) && y.fechaInicio().isBefore(x.fechaFin());
                    assertFalse(cruzan, "Reservas " + x.id() + " y " + y.id() + " se cruzan en el mismo auto");
                }
            assertTrue(lista.stream().filter(r -> r.estado() == EstadoReserva.EN_CURSO).count() <= 1);
        }
        // 7. Numeración de comprobantes correlativa y sin huecos.
        verificarNumeracion(cps);
        // 8. La caja cuadra con la suma de pagos.
        ConsultaReservaUseCase.ReporteCaja caja = consultas.caja(null, null, null, null);
        BigDecimal confirmado = pagos.findAll().stream().filter(p -> p.estado() == EstadoPago.CONFIRMADO).map(Pago::monto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, confirmado.compareTo(caja.totalConfirmado()));
        assertEquals(0, caja.totalPorMetodo().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(caja.totalConfirmado()));
    }

    private static void verificarNumeracion(List<Comprobante> cps) {
        Map<String, List<Integer>> porSerie = cps.stream().collect(Collectors.groupingBy(c -> c.tipo() + "-" + c.serie(),
                Collectors.mapping(Comprobante::numero, Collectors.toList())));
        porSerie.forEach((serie, numeros) -> {
            List<Integer> orden = numeros.stream().sorted().toList();
            for (int i = 0; i < orden.size(); i++)
                assertEquals(i + 1, orden.get(i), "La serie " + serie + " tiene un hueco o repetido: " + orden);
        });
    }
}
