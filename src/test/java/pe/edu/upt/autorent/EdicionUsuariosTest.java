package pe.edu.upt.autorent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.upt.autorent.modelo.Usuario;
import pe.edu.upt.autorent.servicio.contrato.UsuarioUseCase;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Corregir datos de cuentas: quién puede editar a quién y con qué reglas. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:autorent_edicion;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class EdicionUsuariosTest {
    private static final AtomicInteger N = new AtomicInteger(500);

    @Autowired private UsuarioUseCase usuarios;
    @Autowired private MockMvc mvc;

    private Usuario cliente() {
        int n = N.incrementAndGet();
        return usuarios.registrarCliente("edicion" + n, "Clave1234", "Rosa", "Quispe", "72" + String.format("%06d", n),
                "rosa" + n + "@gmail.com", "951" + String.format("%06d", n), "Tacna");
    }

    @Test
    void elPersonalCorrigeLosDatosDeUnClienteConLasMismasReglasQueAlRegistrarlo() {
        Usuario c = cliente();
        Usuario otro = cliente();
        Usuario editado = usuarios.actualizarDatos(c.id(), "Rosa María", "Quispe Mamani", "73123456",
                "rosa.maria@gmail.com", "952111222", "Av. Bolognesi 100", "trabajador");
        assertEquals("Rosa María", editado.nombres());
        assertEquals("73123456", editado.documento());
        assertEquals(c.username(), editado.username());
        assertEquals(c.passwordHash(), editado.passwordHash());
        // Sin repetir el DNI o el correo de otra cuenta, y con datos válidos.
        assertEquals("Ese documento ya está registrado en otra cuenta.", assertThrows(IllegalArgumentException.class,
                () -> usuarios.actualizarDatos(c.id(), "Rosa", "Quispe", otro.documento(), "rosa.x@gmail.com", "", "", "admin")).getMessage());
        assertEquals("Ese correo ya está registrado en otra cuenta.", assertThrows(IllegalArgumentException.class,
                () -> usuarios.actualizarDatos(c.id(), "Rosa", "Quispe", "73123456", otro.correo(), "", "", "admin")).getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> usuarios.actualizarDatos(c.id(), "Rosa3", "Quispe", "73123456", "rosa.maria@gmail.com", "", "", "admin"));
        assertThrows(IllegalArgumentException.class,
                () -> usuarios.actualizarDatos(c.id(), "Rosa", "Quispe", "11111111", "rosa.maria@gmail.com", "", "", "admin"));
        assertThrows(IllegalArgumentException.class,
                () -> usuarios.actualizarDatos(c.id(), "Rosa", "Quispe", "73123456", "rosa@gmial.com", "", "", "admin"));
        // Guardar sin cambiar el DNI propio no choca consigo mismo.
        assertNotNull(usuarios.actualizarDatos(c.id(), "Rosa", "Quispe", "73123456", "rosa.maria@gmail.com", "", "", "admin"));
    }

    @Test
    void elTrabajadorSoloEditaClientesYElClienteSoloSuContacto() {
        Usuario c = cliente();
        Usuario otro = cliente();
        Long idTrabajador = usuarios.buscarPorUsername("trabajador").orElseThrow().id();
        assertEquals("Solo puede corregir los datos de clientes.", assertThrows(IllegalArgumentException.class,
                () -> usuarios.actualizarDatos(usuarios.buscarPorUsername("admin").orElseThrow().id(), "Otro", "Nombre",
                        "70000001", "admin@autorent.demo", "", "", "trabajador")).getMessage());
        // El administrador sí puede corregir a un trabajador.
        Usuario t = usuarios.obtener(idTrabajador);
        assertNotNull(usuarios.actualizarDatos(idTrabajador, t.nombres(), t.apellidos(), t.documento(), t.correo(),
                "952000000", t.direccion(), "admin"));
        // El cliente no toca otra cuenta, y en la suya su nombre y DNI no cambian aunque los envíe.
        assertThrows(IllegalArgumentException.class, () -> usuarios.actualizarDatos(otro.id(), "X", "Y", "74000000",
                "nuevo@gmail.com", "", "", c.username()));
        Usuario propio = usuarios.actualizarDatos(c.id(), "Hacker", "Falso", "74999999", "rosa.nueva@gmail.com",
                "953444555", "Calle Zela 456", c.username());
        assertEquals("Rosa", propio.nombres());
        assertEquals(c.documento(), propio.documento());
        assertEquals("rosa.nueva@gmail.com", propio.correo());
        assertEquals("953444555", propio.telefono());
    }

    @Test
    void lasPantallasDeEdicionRespetanLosRoles() throws Exception {
        Usuario c = cliente();
        mvc.perform(get("/operaciones/clientes").with(user("trabajador").roles("TRABAJADOR"))).andExpect(status().isOk());
        mvc.perform(get("/operaciones/clientes/" + c.id() + "/editar").with(user("trabajador").roles("TRABAJADOR")))
                .andExpect(status().isOk()).andExpect(view().name("usuarios/editar"));
        mvc.perform(get("/admin/usuarios/" + c.id() + "/editar").with(user("trabajador").roles("TRABAJADOR")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/operaciones/clientes").with(user(c.username()).roles("CLIENTE"))).andExpect(status().isForbidden());
        mvc.perform(get("/mi-cuenta").with(user(c.username()).roles("CLIENTE"))).andExpect(status().isOk());
        mvc.perform(post("/mi-cuenta").with(csrf()).with(user(c.username()).roles("CLIENTE"))
                        .param("correo", "rosa.web@gmail.com").param("telefono", "954000111").param("direccion", "Tacna"))
                .andExpect(redirectedUrl("/mi-cuenta"))
                .andExpect(flash().attribute("ok", "Sus datos se actualizaron."));
        assertEquals("rosa.web@gmail.com", usuarios.obtener(c.id()).correo());
        // Un error vuelve al formulario con lo escrito.
        mvc.perform(post("/operaciones/clientes/" + c.id() + "/editar").with(csrf()).with(user("trabajador").roles("TRABAJADOR"))
                        .param("nombres", "Rosa").param("apellidos", "Quispe").param("documento", "123")
                        .param("correo", "rosa.web@gmail.com"))
                .andExpect(redirectedUrl("/operaciones/clientes/" + c.id() + "/editar"))
                .andExpect(flash().attributeExists("error", "form"));
    }
}
