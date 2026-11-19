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
}
