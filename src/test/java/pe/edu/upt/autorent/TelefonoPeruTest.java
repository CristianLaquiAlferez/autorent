package pe.edu.upt.autorent;

import org.junit.jupiter.api.Test;
import pe.edu.upt.autorent.modelo.TelefonoPeru;
import static org.junit.jupiter.api.Assertions.*;

/** Verifica las reglas de telefonos peruanos y numeros Yape. */
class TelefonoPeruTest {
    @Test
    void contactoPermiteEntreSeisYNueveDigitos() {
        assertTrue(TelefonoPeru.esContactoValido("999123456"));
        assertTrue(TelefonoPeru.esContactoValido("234567"));
        assertFalse(TelefonoPeru.esContactoValido("9991234567"));
        assertFalse(TelefonoPeru.esContactoValido("1234"));
        assertFalse(TelefonoPeru.esContactoValido("999-123456"));
    }

    @Test
    void yapeRequiereNueveDigitosEInicioConNueve() {
        assertTrue(TelefonoPeru.esMovilValido("999123456"));
        assertFalse(TelefonoPeru.esMovilValido("899123456"));
        assertFalse(TelefonoPeru.esMovilValido("99912345"));
        assertFalse(TelefonoPeru.esMovilValido("9991234567"));
    }
}
