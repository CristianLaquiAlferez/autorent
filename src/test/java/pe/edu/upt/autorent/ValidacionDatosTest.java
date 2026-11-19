package pe.edu.upt.autorent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import pe.edu.upt.autorent.modelo.ValidacionDatos;
import pe.edu.upt.autorent.servicio.contrato.UsuarioUseCase;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reglas de formato de los datos que escribe una persona. Cada caso "malo" es un dato que alguien podría
 * escribir por error o a propósito; cada caso "bueno" es un dato real que el sistema debe aceptar.
 */
@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:autorent_validacion;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class ValidacionDatosTest {

    @Autowired private UsuarioUseCase usuarios;

    @ParameterizedTest
    @ValueSource(strings = {"Ana", "María José", "José Luis", "D'Angelo", "Pérez-Rojas", "Ñuflo", "Güido", "Lu"})
    void nombresReales(String v) { assertDoesNotThrow(() -> ValidacionDatos.nombre(v, "nombres")); }

    @ParameterizedTest
    @ValueSource(strings = {"A", "Juan123", "123", "@@@", "Ana!", "<script>", "Ana  María", " - ", "Ana_Maria",
            "José.", "'Ana", "Ana-", "😀"})
    void nombresSinSentido(String v) { assertThrows(IllegalArgumentException.class, () -> ValidacionDatos.nombre(v, "nombres")); }

    @ParameterizedTest
    @ValueSource(strings = {"luis.apaza@gmail.com", "luis_apaza+autorent@gmail.com", "ana@hotmail.com",
            "jose@outlook.com", "maria@yahoo.es", "cliente@upt.edu.pe", "ventas@caplina.com.pe", "LUIS@GMAIL.COM", "a1@x.pe"})
    void correosReales(String v) { assertDoesNotThrow(() -> ValidacionDatos.correo(v)); }

    @ParameterizedTest
    @ValueSource(strings = {"", "luis", "luis@", "@gmail.com", "luis@gmail", "luis@@gmail.com", "luis gmail@gmail.com",
            ".luis@gmail.com", "luis.@gmail.com", "lu..is@gmail.com", "luis@gmail..com", "luis@-gmail.com",
            "luis@gmail.c", "luis@gmail.123", "luis@gmial.com", "luis@gmail.con", "luis@hotmial.com", "luis@gmail.co",
            "luis@outlok.com", "luis@yahooo.com", "luis@empresa.con", "luis#@gmail.com", "luis@gmail.com.", "luis@gmail,com"})
    void correosSinSentido(String v) { assertThrows(IllegalArgumentException.class, () -> ValidacionDatos.correo(v)); }

    @Test
    void elCorreoMalEscritoSugiereElCorrecto() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> ValidacionDatos.correo("ana@gmial.com"));
        assertTrue(ex.getMessage().contains("@gmail.com"), ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"70000007", "45678912", "01234567"})
    void dniReales(String v) { assertDoesNotThrow(() -> ValidacionDatos.dni(v)); }

    @ParameterizedTest
    @ValueSource(strings = {"", "1234567", "123456789", "1234567a", "00000000", "11111111", "99999999", "12 34567", "-1234567"})
    void dniSinSentido(String v) { assertThrows(IllegalArgumentException.class, () -> ValidacionDatos.dni(v)); }

    @ParameterizedTest
    @ValueSource(strings = {"987654321", "999111222", "526543", "2345678"})
    void telefonosReales(String v) { assertTrue(ValidacionDatos.telefonoValido(v)); }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "1234567890", "887654321", "12345678", "098765", "+51987654321", "987-654-321",
            "98765432a", "abcdefghi", "987 654 321", "999999"})
    void telefonosSinSentido(String v) { assertFalse(ValidacionDatos.telefonoValido(v)); }

    @ParameterizedTest
    @ValueSource(strings = {"Toyota", "Hyundai", "CX-5", "Model 3", "Mercedes-Benz", "Sw4"})
    void marcasYModelosReales(String v) { assertDoesNotThrow(() -> ValidacionDatos.marcaModelo(v, "La marca")); }

    @ParameterizedTest
    @ValueSource(strings = {"", "123", "@@@", "Toyota!", "-Kia", "<b>Kia</b>"})
    void marcasYModelosSinSentido(String v) { assertThrows(IllegalArgumentException.class, () -> ValidacionDatos.marcaModelo(v, "La marca")); }

    @Test
    void elMensajeDelModeloUsaElArticuloCorrecto() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> ValidacionDatos.marcaModelo("123", "El modelo"));
        assertTrue(ex.getMessage().startsWith("El modelo debe tener letras"), ex.getMessage());
    }

    @Test
    void colores() {
        assertDoesNotThrow(() -> ValidacionDatos.colorOpcional("Gris oscuro"));
        assertDoesNotThrow(() -> ValidacionDatos.colorOpcional(""));
        assertThrows(IllegalArgumentException.class, () -> ValidacionDatos.colorOpcional("Rojo123"));
        assertThrows(IllegalArgumentException.class, () -> ValidacionDatos.colorOpcional("#FF0000"));
    }

    @Test
    void motivosQueExplicanAlgo() {
        assertDoesNotThrow(() -> ValidacionDatos.textoExplicativo("El Yape no llegó", "x"));
        for (String malo : new String[]{null, "", "x", "ok", "....", "12345", "     "})
            assertThrows(IllegalArgumentException.class, () -> ValidacionDatos.textoExplicativo(malo, "x"));
    }

    @Test
    void elRegistroAplicaTodasLasReglas() {
        // Datos correctos de base; en cada intento se cambia un solo dato por uno sin sentido.
        String[][] malos = {
                {"nombres", "Juan123"}, {"apellidos", "@@@"}, {"documento", "00000000"},
                {"correo", "juan@gmail.con"}, {"telefono", "12345678"}};
        for (String[] m : malos) {
            String nombres = m[0].equals("nombres") ? m[1] : "Juan";
            String apellidos = m[0].equals("apellidos") ? m[1] : "Pérez";
            String dni = m[0].equals("documento") ? m[1] : "45678912";
            String correo = m[0].equals("correo") ? m[1] : "juan.perez@gmail.com";
            String telefono = m[0].equals("telefono") ? m[1] : "987654321";
            assertThrows(IllegalArgumentException.class, () -> usuarios.registrarCliente("juan.perez", "Clave1234",
                    nombres, apellidos, dni, correo, telefono, "Tacna"), "debió rechazar " + m[0] + " = " + m[1]);
        }
        assertNotNull(usuarios.registrarCliente("juan.perez", "Clave1234", "Juan", "Pérez", "45678912",
                "juan.perez@gmail.com", "987654321", "Tacna").id());
    }
}
