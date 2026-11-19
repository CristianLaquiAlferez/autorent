package pe.edu.upt.autorent;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.HttpHeaders;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upt.autorent.modelo.*;
import pe.edu.upt.autorent.servicio.contrato.*;
import pe.edu.upt.autorent.repositorio.PagoRepositoryPort;
import pe.edu.upt.autorent.repositorio.DevolucionRepositoryPort;
import pe.edu.upt.autorent.repositorio.ReservaRepositoryPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Pruebas reales de Spring Boot + Spring Security + Thymeleaf + JPA con H2 (perfil de test). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AutoRentIntegracionTest {
    @Autowired private MockMvc mvc;
    @Autowired private ReservaUseCase reservas;
    @Autowired private AutoUseCase autos;
    @Autowired private UsuarioUseCase usuarios;
    @Autowired private PagoRepositoryPort pagos;
    @Autowired private DevolucionRepositoryPort devoluciones;
    @Autowired private ReservaRepositoryPort reservaRepository;

    @Test @WithMockUser(username = "admin", roles = "ADMIN")
    void renderizaPantallasAdministrador() throws Exception {
        mvc.perform(get("/inicio")).andExpect(status().isOk());
        mvc.perform(get("/autos")).andExpect(status().isOk());
        mvc.perform(get("/admin/autos")).andExpect(status().isOk());
        mvc.perform(get("/admin/autos/nuevo")).andExpect(status().isOk());
        mvc.perform(get("/admin/autos/" + autos.listarTodos().get(0).id() + "/editar")).andExpect(status().isOk());
        mvc.perform(get("/admin/mantenimiento")).andExpect(status().isOk());
        mvc.perform(get("/admin/usuarios")).andExpect(status().isOk());
        mvc.perform(get("/operaciones/reservas")).andExpect(status().isOk());
        mvc.perform(get("/operaciones/reservas/nueva")).andExpect(status().isOk());
        mvc.perform(get("/operaciones/clientes/nuevo")).andExpect(status().isOk());
        mvc.perform(get("/operaciones/devoluciones")).andExpect(status().isOk());
        mvc.perform(get("/reportes")).andExpect(status().isOk());
    }

    @Test @WithMockUser(username = "cliente", roles = "CLIENTE")
    void clienteSoloPuedeAccederASusPantallas() throws Exception {
        mvc.perform(get("/mis-reservas")).andExpect(status().isOk());
        mvc.perform(get("/reservas/nueva/" + autos.listarDisponibles().get(0).id())).andExpect(status().isOk());
        mvc.perform(get("/admin/usuarios")).andExpect(status().isForbidden());
        mvc.perform(get("/operaciones/reservas")).andExpect(status().isForbidden());
        mvc.perform(get("/reportes")).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(username = "trabajador", roles = "TRABAJADOR")
    void trabajadorNoAccedeAModulosDelAdministradorNiDelCliente() throws Exception {
        mvc.perform(get("/operaciones/reservas")).andExpect(status().isOk());
        mvc.perform(get("/reportes")).andExpect(status().isOk());
        mvc.perform(get("/admin/autos")).andExpect(status().isForbidden());
        mvc.perform(get("/mis-reservas")).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(username = "cliente", roles = "CLIENTE")
    void registraReservaDesdeFormularioHttpYLaPersiste() throws Exception {
        Auto auto = autos.listarDisponibles().get(0);
        LocalDate inicio = LocalDate.now().plusDays(7);
        LocalDate fin = inicio.plusDays(2);
        BigDecimal anticipo = auto.tarifaDiaria().multiply(new BigDecimal("0.40"));
        mvc.perform(post("/reservas/crear").with(csrf())
                .param("autoId", auto.id().toString())
                .param("fechaInicio", inicio.toString()).param("fechaFin", fin.toString())
                .param("montoAnticipo", anticipo.toPlainString())
                .param("metodoPago", "YAPE").param("telefonoYape", "999888777")
                .param("codigoOperacion", "HTTP-YAPE-001"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/mis-reservas"));
        List<Reserva> creadas = reservas.listarReservas("cliente");
        assertEquals(1, creadas.size());
        assertEquals(EstadoReserva.PENDIENTE_VALIDACION, creadas.get(0).estado());
    }

    @Test
    void anticipoYapeCancelacionYReembolsoPersisten() {
        Auto auto = autos.listarDisponibles().get(0);
        LocalDate inicio = LocalDate.now().plusDays(7);
        Reserva r = reservas.crearReservaOnline("cliente", auto.id(), inicio, inicio.plusDays(2),
                new BigDecimal("60.00"), MetodoPago.YAPE, "999888777", "INTEG-001", null);
        Pago p = reservas.listarPagosDeCliente("cliente").get(0);
        assertEquals(EstadoPago.PENDIENTE, p.estado());
        reservas.confirmarPago(p.id(), "trabajador");
        assertEquals(EstadoReserva.CONFIRMADA, reservas.obtenerReserva(r.id()).estado());
        reservas.cancelarReserva(r.id(), "cliente", null);
        assertEquals(EstadoReserva.CANCELADA, reservas.obtenerReserva(r.id()).estado());
        Devolucion d = reservas.listarDevolucionesDeCliente("cliente").get(0);
        assertEquals("999888777", d.telefonoDestino());
        reservas.completarDevolucion(d.id(), "trabajador", "YAPE-DEV-01");
        assertEquals(EstadoDevolucion.COMPLETADA, reservas.listarDevolucionesDeCliente("cliente").get(0).estado());
    }

    @Test
    void pagoSaldoYapeNoPermiteEntregaSinValidacion() {
        Auto auto = autos.listarDisponibles().get(0);
        Long clienteId = usuarios.buscarPorUsername("cliente").orElseThrow().id();
        Reserva r = reservas.crearReservaPresencial(clienteId, auto.id(), LocalDate.now(), LocalDate.now().plusDays(1),
                new BigDecimal("30.00"), MetodoPago.EFECTIVO, null, null, "999555555", "trabajador");
        Pago saldo = reservas.registrarPagoSaldo(r.id(), auto.tarifaDiaria().subtract(new BigDecimal("30.00")), MetodoPago.YAPE,
                "999777666", "INTEG-002", null, "trabajador");
        assertEquals(EstadoPago.PENDIENTE, saldo.estado());
        assertThrows(IllegalArgumentException.class, () -> reservas.entregarVehiculo(r.id(), "trabajador", "45000 km, tanque lleno"));
        reservas.confirmarPago(saldo.id(), "trabajador");
        reservas.entregarVehiculo(r.id(), "trabajador", "45000 km, tanque lleno");
        assertEquals(EstadoReserva.EN_CURSO, reservas.obtenerReserva(r.id()).estado());
        reservas.recibirVehiculo(r.id(), "trabajador", "Sin daños", null, null);
        assertEquals(EstadoReserva.FINALIZADA, reservas.obtenerReserva(r.id()).estado());
    }

    @Test
    void rechazoDePagoPermiteReintentoSinDuplicarAnticipo() {
        Auto auto = autos.listarDisponibles().get(0);
        LocalDate inicio = LocalDate.now().plusDays(7);
        Reserva r = reservas.crearReservaOnline("cliente", auto.id(), inicio, inicio.plusDays(1),
                new BigDecimal("30.00"), MetodoPago.YAPE, "999888777", "INTEG-003", null);
        Pago original = reservas.listarPagosDeCliente("cliente").get(0);
        reservas.rechazarPago(original.id(), "trabajador", "El Yape no llegó");
        reservas.reintentarAnticipo("cliente", r.id(), new BigDecimal("30.00"), MetodoPago.YAPE,
                "999888777", "INTEG-004", null);
        assertEquals(2, reservas.listarPagosDeCliente("cliente").size());
        Pago nuevo = reservas.listarPagosDeCliente("cliente").stream()
                .filter(p -> p.estado() == EstadoPago.PENDIENTE).findFirst().orElseThrow();
        reservas.confirmarPago(nuevo.id(), "trabajador");
        assertEquals(EstadoReserva.CONFIRMADA, reservas.obtenerReserva(r.id()).estado());
    }

    @Test
    void noSePuedeReservarDosVecesElMismoAutoEnFechasSuperpuestas() {
        Auto auto = autos.listarDisponibles().get(0);
        LocalDate inicio = LocalDate.now().plusDays(7);
        reservas.crearReservaOnline("cliente", auto.id(), inicio, inicio.plusDays(3),
                new BigDecimal("90.00"), MetodoPago.YAPE, "999888777", "INTEG-005", null);
        assertThrows(IllegalArgumentException.class, () -> reservas.crearReservaOnline("cliente", auto.id(),
                inicio.plusDays(1), inicio.plusDays(4), new BigDecimal("90.00"),
                MetodoPago.YAPE, "999888777", "INTEG-006", null));
    }
    @Test
    void consultasOptimizadasMantienenSaldosYFiltrosDeCliente() {
        Auto auto = autos.listarDisponibles().get(0);
        Long clienteId = usuarios.buscarPorUsername("cliente").orElseThrow().id();
        LocalDate inicio = LocalDate.now().plusDays(7);
        Reserva reserva = reservas.crearReservaPresencial(clienteId, auto.id(), inicio, inicio.plusDays(1),
                new BigDecimal("30.00"), MetodoPago.EFECTIVO, null, null, "999555555", "trabajador");

        assertTrue(reservaRepository.existsReservaActiva(auto.id(), EstadoReserva.BLOQUEAN_DISPONIBILIDAD));
        assertEquals(0, pagos.totalConfirmado(reserva.id()).compareTo(new BigDecimal("30.00")));
        assertFalse(pagos.existePendiente(reserva.id()));
        assertEquals(1, pagos.findByClienteId(clienteId).size());

        Pago pendiente = reservas.registrarPagoSaldo(reserva.id(), auto.tarifaDiaria().subtract(new BigDecimal("30.00")),
                MetodoPago.YAPE, "999777666", "OPT-001", null, "trabajador");
        assertTrue(pagos.existePendiente(reserva.id()));
        assertEquals(0, pagos.totalConfirmado(reserva.id()).compareTo(new BigDecimal("30.00")));
        reservas.confirmarPago(pendiente.id(), "trabajador");
        assertFalse(pagos.existePendiente(reserva.id()));
        assertEquals(0, pagos.totalConfirmado(reserva.id()).compareTo(auto.tarifaDiaria()));
        assertEquals(2, pagos.findByClienteId(clienteId).size());

        reservas.cancelarReserva(reserva.id(), "cliente", null);
        assertFalse(reservaRepository.existsReservaActiva(auto.id(), EstadoReserva.BLOQUEAN_DISPONIBILIDAD));
        assertEquals(2, devoluciones.findByClienteId(clienteId).size());
    }


    @Test @WithMockUser(username = "cliente", roles = "CLIENTE")
    void erroresDeFormulariosNoQuedanSinRespuesta() throws Exception {
        mvc.perform(get("/reservas/nueva/999999999"))
                .andExpect(redirectedUrl("/autos"))
                .andExpect(flash().attribute("error", "Vehículo no encontrado."));
        mvc.perform(post("/reservas/crear").with(csrf())
                .param("autoId", "no-es-un-numero")
                .param("fechaInicio", "fecha-invalida")
                .param("fechaFin", "2027-01-01")
                .param("montoAnticipo", "10.00")
                .param("metodoPago", "YAPE"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error-operacion"));
    }

    @Test @WithMockUser(username = "trabajador", roles = "TRABAJADOR")
    void operacionesPresentaSaldosYBloqueaEntradasInvalidas() throws Exception {
        mvc.perform(get("/operaciones/reservas"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("saldos", "fechaHoy"));
        mvc.perform(post("/operaciones/reservas/999999999/vencer").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/operaciones/reservas"));
    }

    @Test @WithMockUser(username = "cliente", roles = "CLIENTE")
    void clienteNoPuedeCerrarReservasVencidasDeSucursal() throws Exception {
        mvc.perform(post("/operaciones/reservas/1/vencer").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test @WithMockUser(username = "admin", roles = "ADMIN")
    void registraFotoDesdeArchivoYLaSirveEnCatalogo() throws Exception {
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+eQgAAAABJRU5ErkJggg==");
        MockMultipartFile foto = new MockMultipartFile("imagenArchivo", "coche.png", "image/png", png);
        mvc.perform(multipart("/admin/autos/guardar").file(foto).with(csrf())
                .param("placa", "IMG-901").param("marca", "Toyota").param("modelo", "Yaris")
                .param("anio", "2024").param("categoria", "Compacto").param("color", "Azul")
                .param("tarifaDiaria", "120.00").param("descripcion", "Imagen desde archivos"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/autos"));
        Auto creado = autos.listarTodos().stream().filter(a -> "IMG-901".equals(a.placa())).findFirst().orElseThrow();
        assertEquals("/autos/" + creado.id() + "/imagen", creado.imagenUrl());
        mvc.perform(get("/autos/" + creado.id() + "/imagen"))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(png));
        mvc.perform(get("/autos")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/autos/" + creado.id() + "/imagen")));
    }

    @Test @WithMockUser(username = "admin", roles = "ADMIN")
    void registraUrlYRechazaArchivoNoValido() throws Exception {
        mvc.perform(multipart("/admin/autos/guardar").with(csrf())
                .param("placa", "IMG-902").param("marca", "Kia").param("modelo", "Rio")
                .param("anio", "2024").param("categoria", "Compacto")
                .param("tarifaDiaria", "110.00").param("imagenUrl", "https://example.com/auto.jpg"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/autos"));
        Auto creado = autos.listarTodos().stream().filter(a -> "IMG-902".equals(a.placa())).findFirst().orElseThrow();
        assertEquals("https://example.com/auto.jpg", creado.imagenUrl());
        MockMultipartFile archivoFalso = new MockMultipartFile("imagenArchivo", "falso.png", "image/png", new byte[]{1, 2, 3});
        mvc.perform(multipart("/admin/autos/guardar").file(archivoFalso).with(csrf())
                .param("placa", "IMG-903").param("marca", "Kia").param("modelo", "Rio")
                .param("anio", "2024").param("categoria", "Compacto").param("tarifaDiaria", "110.00"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/autos/nuevo"))
                .andExpect(flash().attributeExists("error"));
        assertFalse(autos.listarTodos().stream().anyMatch(a -> "IMG-903".equals(a.placa())));
    }

    @Test @WithMockUser(username = "trabajador", roles = "TRABAJADOR")
    void descargaPdfCompletoDeReportes() throws Exception {
        mvc.perform(get("/reportes/descargar-pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.startsWith("attachment; filename=\"AutoRent_Reporte_")))
                .andExpect(result -> {
                    byte[] bytes = result.getResponse().getContentAsByteArray();
                    assertTrue(bytes.length > 500);
                    assertTrue(new String(bytes, 0, 8, StandardCharsets.US_ASCII).startsWith("%PDF-1.4"));
                });
    }

    @Test @WithMockUser(username = "cliente", roles = "CLIENTE")
    void clienteNoPuedeDescargarReportes() throws Exception {
        mvc.perform(get("/reportes/descargar-pdf")).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(username = "admin", roles = "ADMIN")
    void reemplazaFotoPorUrlYLaQuitaSinTablaAdicional() throws Exception {
        Auto existente = autos.listarTodos().get(0);
        String id = existente.id().toString();
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+eQgAAAABJRU5ErkJggg==");
        MockMultipartFile foto = new MockMultipartFile("imagenArchivo", "foto.png", "image/png", png);
        mvc.perform(multipart("/admin/autos/guardar").file(foto).with(csrf())
                .param("id", id).param("placa", existente.placa())
                .param("marca", existente.marca()).param("modelo", existente.modelo())
                .param("anio", String.valueOf(existente.anio()))
                .param("categoria", existente.categoria())
                .param("tarifaDiaria", existente.tarifaDiaria().toPlainString()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/autos"));
        mvc.perform(get("/autos/" + id + "/imagen")).andExpect(status().isOk());

        mvc.perform(multipart("/admin/autos/guardar").with(csrf())
                .param("id", id).param("placa", existente.placa())
                .param("marca", existente.marca()).param("modelo", existente.modelo())
                .param("anio", String.valueOf(existente.anio()))
                .param("categoria", existente.categoria())
                .param("tarifaDiaria", existente.tarifaDiaria().toPlainString())
                .param("imagenUrl", "https://example.com/vehiculo.jpg"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/autos"));
        assertEquals("https://example.com/vehiculo.jpg", autos.obtener(existente.id()).imagenUrl());
        mvc.perform(get("/autos/" + id + "/imagen")).andExpect(status().isNotFound());

        mvc.perform(multipart("/admin/autos/guardar").with(csrf())
                .param("id", id).param("placa", existente.placa())
                .param("marca", existente.marca()).param("modelo", existente.modelo())
                .param("anio", String.valueOf(existente.anio()))
                .param("categoria", existente.categoria())
                .param("tarifaDiaria", existente.tarifaDiaria().toPlainString())
                .param("eliminarImagen", "true"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/autos"));
        assertNull(autos.obtener(existente.id()).imagenUrl());
    }


    @Test
    void javascriptDeValidacionTelefonicaEsPublico() throws Exception {
        mvc.perform(get("/js/telefono-peru.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("slice(0, 9)")));
    }

    @Test
    void validaTelefonosPeruanosEnRegistro() {
        assertThrows(IllegalArgumentException.class, () -> usuarios.registrarCliente(
                "peru10", "Test1234*", "Maria", "Prueba", "71112233", "peru10@example.com", "9991234567", "Tacna"));
        assertThrows(IllegalArgumentException.class, () -> usuarios.registrarCliente(
                "peru51", "Test1234*", "Maria", "Prueba", "71112234", "peru51@example.com", "+51999123456", "Tacna"));
        var usuario = usuarios.registrarCliente(
                "perufijo", "Test1234*", "Maria", "Prueba", "71112235", "perufijo@example.com", "2345678", "Tacna");
        assertEquals("2345678", usuario.telefono());
    }

    @Test @WithMockUser(username = "cliente", roles = "CLIENTE")
    void rechazaYapeDeDiezDigitosDesdeHttpSinCrearReserva() throws Exception {
        Auto auto = autos.listarDisponibles().get(0);
        LocalDate inicio = LocalDate.now().plusDays(7);
        mvc.perform(post("/reservas/crear").with(csrf())
                .param("autoId", auto.id().toString())
                .param("fechaInicio", inicio.toString()).param("fechaFin", inicio.plusDays(2).toString())
                .param("montoAnticipo", "60.00")
                .param("metodoPago", "YAPE").param("telefonoYape", "9998887771")
                .param("codigoOperacion", "HTTP-INVALIDO-01"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/reservas/nueva/" + auto.id()))
                .andExpect(flash().attributeExists("error"));
        assertTrue(reservas.listarReservas("cliente").isEmpty());
    }

    @Test @WithMockUser(username = "cliente", roles = "CLIENTE")
    void rechazaNumeroDeDevolucionDeDiezDigitosDesdeHttp() throws Exception {
        Auto auto = autos.listarDisponibles().get(0);
        LocalDate inicio = LocalDate.now().plusDays(7);
        mvc.perform(post("/reservas/crear").with(csrf())
                .param("autoId", auto.id().toString())
                .param("fechaInicio", inicio.toString()).param("fechaFin", inicio.plusDays(2).toString())
                .param("montoAnticipo", "60.00")
                .param("metodoPago", "EFECTIVO")
                .param("telefonoDevolucion", "9998887771"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/reservas/nueva/" + auto.id()))
                .andExpect(flash().attributeExists("error"));
        assertTrue(reservas.listarReservas("cliente").isEmpty());
    }

}
