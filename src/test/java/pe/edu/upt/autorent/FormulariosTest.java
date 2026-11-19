package pe.edu.upt.autorent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.upt.autorent.controlador.formulario.ReservaForm;
import pe.edu.upt.autorent.modelo.*;
import pe.edu.upt.autorent.servicio.contrato.AutoUseCase;
import pe.edu.upt.autorent.servicio.contrato.UsuarioUseCase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Formularios: que el navegador reciba los mismos límites que aplica el servidor (fechas, montos,
 * formatos), que un error no borre lo escrito, y la ilustración de los vehículos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:autorent_formularios;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class FormulariosTest {

    private static final AtomicInteger SEC = new AtomicInteger(5000);
    @Autowired private MockMvc mvc;
    @Autowired private AutoUseCase autos;
    @Autowired private UsuarioUseCase usuarios;

    private Auto auto(String categoria, String color, String marca) {
        return autos.guardar(new Auto(null, "FRM-" + SEC.incrementAndGet(), marca, "Modelo", 2024, categoria, color,
                new BigDecimal("80.00"), "x", EstadoAuto.DISPONIBLE));
    }

    private static String atributo(String html, String idCampo, String atributo) {
        Matcher m = Pattern.compile("<(?:input|select)[^>]*id=\"" + idCampo + "\"[^>]*>").matcher(html);
        assertTrue(m.find(), "No se encontró el campo " + idCampo);
        Matcher a = Pattern.compile("\\s" + atributo + "=\"([^\"]*)\"").matcher(m.group());
        return a.find() ? a.group(1) : null;
    }

    @Test
    void laIlustracionEsPublicaUsaElColorDelAutoYNoEjecutaTextoInyectado() throws Exception {
        Auto rojo = auto("Hatchback", "Rojo", "Suzuki");
        // Una marca con código ya no se puede guardar: el servidor la rechaza al registrar el vehículo.
        assertThrows(IllegalArgumentException.class, () -> auto("SUV", "Azul", "<script>alert(1)</script>"));
        Auto raro = auto("SUV", "Azul marino", "Kia");
        mvc.perform(get("/autos/" + rojo.id() + "/ilustracion.svg"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/svg+xml"))
                .andExpect(content().string(containsString("#c62828")))          // rojo
                .andExpect(content().string(containsString("Hatchback · Rojo")));
        mvc.perform(get("/autos/" + raro.id() + "/ilustracion.svg"))
                .andExpect(content().string(containsString("#1e5aa8")))          // "Azul marino" -> azul
                .andExpect(content().string(not(containsString("<script>"))));
        // El catálogo usa la ilustración cuando el auto no tiene foto.
        mvc.perform(get("/autos")).andExpect(content().string(containsString("/autos/" + rojo.id() + "/ilustracion.svg")));
    }

    @Test
    void elFormularioDeReservaTraeLosLimitesDeFechasYAnticipo() throws Exception {
        Auto a = auto("Sedán", "Plata", "Toyota");
        String html = mvc.perform(get("/reservas/nueva/" + a.id()).with(user("cliente").roles("CLIENTE")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(LocalDate.now().toString(), atributo(html, "fechaInicio", "min"));
        assertEquals(LocalDate.now().plusDays(180).toString(), atributo(html, "fechaInicio", "max"));
        assertEquals("fechaInicio", atributo(html, "fechaFin", "data-despues-de"));
        assertEquals("1", atributo(html, "fechaFin", "data-dias-min"));
        assertEquals("30", atributo(html, "fechaFin", "data-dias-max"));
        assertEquals("[A-Za-z0-9\\-]{4,40}", atributo(html, "codigoOperacion", "pattern"));
        assertTrue(html.contains("/js/validaciones.js"));
    }

    @Test
    void laReservaPresencialTraeLimitesYConservaLoEscritoSiHayError() throws Exception {
        Auto a = auto("Sedán", "Negro", "Hyundai");
        String html = mvc.perform(get("/operaciones/reservas/nueva").with(user("trabajador").roles("TRABAJADOR")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(LocalDate.now().toString(), atributo(html, "fechaInicio", "min"));
        assertEquals("fechaInicio", atributo(html, "fechaFin", "data-despues-de"));
        assertTrue(html.contains("data-tarifa=\"80.00\""), "Cada vehículo debe llevar su tarifa para calcular el anticipo");

        // Un error (fecha pasada) vuelve al formulario con los datos escritos, no a la lista.
        Usuario c = usuarios.registrarCliente("form" + SEC.incrementAndGet(), "Clave1234", "Ana", "Prueba",
                String.format("69%06d", SEC.get()), "form" + SEC.get() + "@prueba.pe", "995000111", "Tacna");
        var resultado = mvc.perform(post("/operaciones/reservas/crear").with(csrf()).with(user("trabajador").roles("TRABAJADOR"))
                        .param("clienteId", c.id().toString()).param("autoId", a.id().toString())
                        .param("fechaInicio", LocalDate.now().minusDays(2).toString())
                        .param("fechaFin", LocalDate.now().plusDays(1).toString())
                        .param("montoAnticipo", "80").param("metodoPago", "EFECTIVO").param("telefonoDevolucion", "987654321"))
                .andExpect(redirectedUrl("/operaciones/reservas/nueva"))
                .andExpect(flash().attributeExists("error"))
                .andExpect(flash().attributeExists("form"))
                .andReturn();
        ReservaForm conservado = (ReservaForm) resultado.getFlashMap().get("form");
        assertEquals(a.id(), conservado.getAutoId());
        assertEquals("987654321", conservado.getTelefonoDevolucion());
    }

    @Test
    void mantenimientoRegistroYAutoTraenLasMismasReglasQueElServidor() throws Exception {
        String mant = mvc.perform(get("/admin/mantenimiento").with(user("admin").roles("ADMIN")))
                .andReturn().getResponse().getContentAsString();
        assertEquals(LocalDate.now().toString(), atributo(mant, "mantInicio", "min"));
        assertTrue(mant.contains("data-despues-de=\"mantInicio\""));

        String registro = mvc.perform(get("/registro")).andReturn().getResponse().getContentAsString();
        assertEquals("[A-Za-z0-9._\\-]{4,60}", atributo(registro, "username", "pattern"));
        assertEquals("80", atributo(registro, "nombres", "maxlength"));
        assertNotNull(atributo(registro, "password", "pattern"));

        String auto = mvc.perform(get("/admin/autos/nuevo").with(user("admin").roles("ADMIN")))
                .andReturn().getResponse().getContentAsString();
        assertEquals("[A-Za-z0-9\\-]{5,15}", atributo(auto, "placa", "pattern"));
    }

    /**
     * Los navegadores actuales leen el atributo pattern en modo estricto ("v"): un guion suelto dentro de
     * corchetes invalida la regla y el navegador la ignora sin avisar. Aquí se revisan todas las plantillas.
     */
    @Test
    void ningunPatternTieneUnGuionSinEscaparDentroDeCorchetes() throws Exception {
        var plantillas = new org.springframework.core.io.support.PathMatchingResourcePatternResolver()
                .getResources("classpath:templates/**/*.html");
        assertTrue(plantillas.length > 10);
        Pattern pattern = Pattern.compile("pattern=\"([^\"]*)\"");
        for (var p : plantillas) {
            String html = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            Matcher m = pattern.matcher(html);
            while (m.find()) {
                Matcher clase = Pattern.compile("\\[([^\\]]*)\\]").matcher(m.group(1));
                while (clase.find()) {
                    String dentro = clase.group(1);
                    assertFalse(dentro.endsWith("-") && !dentro.endsWith("\\-"),
                            p.getFilename() + ": guion sin escapar en " + m.group(1));
                }
            }
        }
    }
}
