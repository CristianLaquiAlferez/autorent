package pe.edu.upt.autorent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.upt.autorent.modelo.Rol;
import pe.edu.upt.autorent.repositorio.UsuarioRepositoryPort;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;

/**
 * Cuentas para la presentación: se crean solo cuando su contraseña llega por variable de entorno.
 * Aquí se definen la del docente y la del trabajador del curso, pero no la del cliente del curso.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:autorent_presentacion;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "autorent.demo.clave-docente=PruebaDocente1",
        "autorent.demo.clave-curso-trabajador=PruebaCurso1"})
class CuentasPresentacionTest {

    @Autowired private MockMvc mvc;
    @Autowired private UsuarioRepositoryPort usuarios;

    @Test
    void seCreanSoloLasCuentasConContrasenaDefinida() throws Exception {
        assertEquals(Rol.ADMIN, usuarios.findByUsername("docente").orElseThrow().rol());
        assertEquals(Rol.TRABAJADOR, usuarios.findByUsername("curso.trabajador").orElseThrow().rol());
        assertTrue(usuarios.findByUsername("curso.cliente").isEmpty(), "sin CLAVE_CURSO_CLIENTE no debe existir la cuenta");

        mvc.perform(formLogin("/login").user("docente").password("PruebaDocente1"))
                .andExpect(authenticated().withRoles("ADMIN"));
        mvc.perform(formLogin("/login").user("curso.trabajador").password("PruebaCurso1"))
                .andExpect(authenticated().withRoles("TRABAJADOR"));
    }
}
