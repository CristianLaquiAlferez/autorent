package pe.edu.upt.autorent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.upt.autorent.modelo.*;
import pe.edu.upt.autorent.repositorio.ComprobanteRepositoryPort;
import pe.edu.upt.autorent.servicio.contrato.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Seguridad web: quién entra a cada ruta, protección CSRF, documentos ajenos,
 * inyección de HTML, cuentas desactivadas y datos mal formados en los formularios.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:autorent_seguridad;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class SeguridadWebTest {

    private static final AtomicInteger SEC = new AtomicInteger(3000);
    @Autowired private MockMvc mvc;
    @Autowired private UsuarioUseCase usuarios;
    @Autowired private AutoUseCase autos;
    @Autowired private ReservaUseCase reservas;
    @Autowired private ComprobanteRepositoryPort comprobantes;

    private Usuario cliente(String nombres) {
        int n = SEC.incrementAndGet();
        return usuarios.registrarCliente("seguro" + n, "Clave1234", nombres, "Prueba",
                String.format("70%06d", n), "seguro" + n + "@prueba.pe", "996" + String.format("%06d", n), "Tacna");
    }

    private Reserva reservaPagada(Usuario c) {
        Auto a = autos.guardar(new Auto(null, "SG-" + SEC.incrementAndGet(), "Kia", "Picanto", 2024, "City", "Gris",
                new BigDecimal("50.00"), "x", EstadoAuto.DISPONIBLE));
        return reservas.crearReservaPresencial(c.id(), a.id(), LocalDate.now().plusDays(3), LocalDate.now().plusDays(4),
                new BigDecimal("50.00"), MetodoPago.EFECTIVO, null, null, "999111222", "trabajador");
    }

    @Test
    void cadaRutaSoloLaAbreElRolQueCorresponde() throws Exception {
        // Sin sesión: lo privado manda al login.
        for (String ruta : new String[]{"/mis-reservas", "/mis-reservas/1", "/operaciones/caja", "/operaciones/reservas/1",
                "/admin/autos", "/reportes", "/comprobantes/1/pdf", "/pagos/1/pagar"})
            mvc.perform(get(ruta)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
        // Cliente: no entra a operaciones ni administración.
        for (String ruta : new String[]{"/operaciones/caja", "/operaciones/reservas", "/operaciones/devoluciones",
                "/operaciones/comprobantes", "/admin/autos", "/admin/usuarios", "/reportes"})
            mvc.perform(get(ruta).with(user("cliente").roles("CLIENTE"))).andExpect(status().isForbidden());
        // Trabajador: no entra a lo del cliente ni a administración.
        for (String ruta : new String[]{"/mis-reservas", "/mis-reservas/1", "/pagos/1/pagar", "/admin/autos", "/admin/usuarios"})
            mvc.perform(get(ruta).with(user("trabajador").roles("TRABAJADOR"))).andExpect(status().isForbidden());
        // Un cliente no puede confirmar pagos ni anular reservas aunque conozca la ruta.
        mvc.perform(post("/operaciones/pagos/1/confirmar").with(csrf()).with(user("cliente").roles("CLIENTE")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/operaciones/reservas/1/cancelar").param("motivo", "x").with(csrf()).with(user("cliente").roles("CLIENTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void sinTokenCsrfNingunFormularioSeAcepta() throws Exception {
        mvc.perform(post("/operaciones/pagos/1/confirmar").with(user("trabajador").roles("TRABAJADOR"))).andExpect(status().isForbidden());
        mvc.perform(post("/reservas/1/cancelar").with(user("cliente").roles("CLIENTE"))).andExpect(status().isForbidden());
        mvc.perform(post("/pagos/1/tarjeta").with(user("cliente").roles("CLIENTE"))).andExpect(status().isForbidden());
        mvc.perform(post("/registro").param("username", "x")).andExpect(status().isForbidden());
    }

    @Test
    void unClienteNoVeNiDescargaDocumentosDeOtro() throws Exception {
        Usuario duenio = cliente("Duenio"), ajeno = cliente("Ajeno");
        Reserva r = reservaPagada(duenio);
        Comprobante boleta = comprobantes.findByReservaId(r.id()).get(0);
        mvc.perform(get("/comprobantes/" + boleta.id() + "/pdf").with(user(duenio.username()).roles("CLIENTE")))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"));
        mvc.perform(get("/comprobantes/" + boleta.id() + "/pdf").with(user(ajeno.username()).roles("CLIENTE")))
                .andExpect(result -> assertNotEquals(200, result.getResponse().getStatus()));
        mvc.perform(get("/mis-reservas/" + r.id()).with(user(ajeno.username()).roles("CLIENTE")))
                .andExpect(status().is3xxRedirection());
        // Ids que no existen no rompen la página.
        mvc.perform(get("/mis-reservas/999999").with(user(duenio.username()).roles("CLIENTE"))).andExpect(status().is3xxRedirection());
        mvc.perform(get("/operaciones/reservas/999999").with(user("trabajador").roles("TRABAJADOR"))).andExpect(status().is3xxRedirection());
    }

    @Test
    void elTextoQueEscribeUnUsuarioNoSeEjecutaComoCodigo() throws Exception {
        Usuario c = cliente("<script>alert('x')</script>");
        Reserva r = reservaPagada(c);
        mvc.perform(get("/operaciones/reservas/" + r.id()).with(user("trabajador").roles("TRABAJADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<script>alert('x')</script>"))))
                .andExpect(content().string(containsString("&lt;script&gt;")));
    }

    @Test
    void unaCuentaDesactivadaNoPuedeIniciarSesion() throws Exception {
        Usuario c = cliente("Inactivo");
        mvc.perform(formLogin("/login").user(c.username()).password("Clave1234")).andExpect(authenticated());
        usuarios.cambiarEstado(c.id(), false);
        mvc.perform(formLogin("/login").user(c.username()).password("Clave1234")).andExpect(unauthenticated());
        mvc.perform(formLogin("/login").user("cliente").password("clave-incorrecta")).andExpect(unauthenticated());
    }

    @Test
    void datosMalFormadosMuestranUnMensajeYNoUnError500() throws Exception {
        mvc.perform(get("/operaciones/caja").param("desde", "no-es-fecha").with(user("trabajador").roles("TRABAJADOR")))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("formato incorrecto")));
        mvc.perform(get("/operaciones/caja").param("metodo", "BITCOIN").with(user("trabajador").roles("TRABAJADOR")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/operaciones/reservas/1/recibir").param("cargoAdicional", "mucho").with(csrf())
                        .with(user("trabajador").roles("TRABAJADOR")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/mis-reservas/abc").with(user("cliente").roles("CLIENTE"))).andExpect(status().isBadRequest());
    }
}
