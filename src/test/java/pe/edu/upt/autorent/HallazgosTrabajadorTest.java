package pe.edu.upt.autorent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.upt.autorent.modelo.MetodoPago;
import pe.edu.upt.autorent.servicio.contrato.ReservaUseCase;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Hallazgos de la prueba en vivo del rol trabajador. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:autorent_hallazgos;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class HallazgosTrabajadorTest {

    @Autowired private MockMvc mvc;
    @Autowired private ReservaUseCase reservas;
    @Autowired private pe.edu.upt.autorent.servicio.contrato.AutoUseCase autos;
    @Autowired private pe.edu.upt.autorent.servicio.contrato.UsuarioUseCase usuarios;

    @Test
    void unaReservaPresencialSinClienteOSinVehiculoDaUnMensajeClaro() {
        LocalDate d = LocalDate.now().plusDays(5);
        IllegalArgumentException sinCliente = assertThrows(IllegalArgumentException.class, () -> reservas.crearReservaPresencial(
                null, 1L, d, d.plusDays(1), new BigDecimal("50"), MetodoPago.EFECTIVO, null, null, "987654321", "trabajador"));
        assertEquals("Seleccione un cliente registrado.", sinCliente.getMessage());
        IllegalArgumentException sinAuto = assertThrows(IllegalArgumentException.class, () -> reservas.crearReservaPresencial(
                3L, null, d, d.plusDays(1), new BigDecimal("50"), MetodoPago.EFECTIVO, null, null, "987654321", "trabajador"));
        assertEquals("Seleccione un vehículo.", sinAuto.getMessage());
    }

    @Test
    void entregarYRecibirExigenRegistrarElEstadoDelAuto() {
        LocalDate hoy = LocalDate.now();
        Long clienteId = usuarios.buscarPorUsername("cliente").orElseThrow().id();
        // Se usa el primer auto libre hoy (los datos de demostración ocupan algunos).
        pe.edu.upt.autorent.modelo.Reserva creada = null;
        for (var auto : autos.listarDisponibles()) {
            try {
                // Pagado con Yape y sin número de devolución: así se comprueba que el cargo final no lo exige.
                creada = reservas.crearReservaPresencial(clienteId, auto.id(), hoy, hoy.plusDays(1), auto.tarifaDiaria(),
                        MetodoPago.YAPE, "987654321", "ACTA-" + auto.id(), null, "trabajador");
                break;
            } catch (IllegalArgumentException ocupado) { /* probar con el siguiente */ }
        }
        assertNotNull(creada);
        var r = creada;
        // Si el Yape del mostrador quedó por validar, se valida; si ya quedó confirmado, no hay nada que hacer.
        reservas.listarPagosDeCliente("cliente").stream()
                .filter(p -> p.reservaId().equals(r.id()) && p.estado() == pe.edu.upt.autorent.modelo.EstadoPago.PENDIENTE)
                .findFirst().ifPresent(p -> reservas.confirmarPago(p.id(), "trabajador"));
        IllegalArgumentException sinActa = assertThrows(IllegalArgumentException.class, () -> reservas.entregarVehiculo(r.id(), "trabajador", " "));
        assertTrue(sinActa.getMessage().startsWith("Registre el estado del auto al entregarlo"));
        reservas.entregarVehiculo(r.id(), "trabajador", "45 200 km, tanque lleno, sin golpes");
        assertThrows(IllegalArgumentException.class, () -> reservas.recibirVehiculo(r.id(), "trabajador", "", null, null));
        reservas.recibirVehiculo(r.id(), "trabajador", "45 400 km, rayón en la puerta", new BigDecimal("80.00"), "Rayón en la puerta");
        // El cargo se cobra en efectivo sin pedir número de devolución: el alquiler ya terminó.
        var cargo = reservas.registrarPagoSaldo(r.id(), new BigDecimal("80.00"), MetodoPago.EFECTIVO, null, null, null, "trabajador");
        assertEquals(pe.edu.upt.autorent.modelo.EstadoPago.CONFIRMADO, cargo.estado());
    }

    @Test
    void unFormularioEnviadoAUnaRutaDeOtroRolMuestraAccesoDenegado() throws Exception {
        mvc.perform(post("/admin/autos/1/estado").param("estado", "INACTIVO").with(csrf()).with(user("trabajador").roles("TRABAJADOR")))
                .andExpect(status().isForbidden())
                .andExpect(forwardedUrl("/acceso-denegado"));
        mvc.perform(post("/acceso-denegado").with(csrf()).with(user("trabajador").roles("TRABAJADOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/acceso-denegado"));
    }

    @Test
    void siElRegistroFallaSeConservanLosDatosMenosLaContrasena() throws Exception {
        mvc.perform(post("/operaciones/clientes/crear").with(csrf()).with(user("trabajador").roles("TRABAJADOR"))
                        .param("username", "conserva.datos").param("password", "Clave1234")
                        .param("nombres", "Rosa").param("apellidos", "Quispe").param("documento", "11111111")
                        .param("correo", "rosa.quispe@gmail.com").param("telefono", "987654321").param("direccion", "Tacna"))
                .andExpect(redirectedUrl("/operaciones/clientes/nuevo"))
                .andExpect(flash().attribute("error", "El DNI no es válido (todos sus dígitos son iguales)."))
                .andExpect(flash().attribute("form", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.hasProperty("nombres", org.hamcrest.Matchers.is("Rosa")),
                        org.hamcrest.Matchers.hasProperty("correo", org.hamcrest.Matchers.is("rosa.quispe@gmail.com")),
                        org.hamcrest.Matchers.hasProperty("password", org.hamcrest.Matchers.nullValue()))));
    }

    @Test
    void elFormularioDeReservaDeUnAutoInexistenteOInactivoVuelveAlCatalogo() throws Exception {
        var cliente = user("cliente").roles("CLIENTE");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/reservas/nueva/9999").with(cliente))
                .andExpect(redirectedUrl("/autos"))
                .andExpect(flash().attribute("error", "Vehículo no encontrado."));
        mvc.perform(post("/reservas/crear").with(csrf()).with(cliente).param("metodoPago", "EFECTIVO"))
                .andExpect(redirectedUrl("/autos"))
                .andExpect(flash().attributeExists("error"));
    }
}
