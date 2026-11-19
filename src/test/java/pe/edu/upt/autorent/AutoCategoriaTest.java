package pe.edu.upt.autorent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import pe.edu.upt.autorent.modelo.Auto;
import pe.edu.upt.autorent.modelo.EstadoAuto;
import pe.edu.upt.autorent.servicio.contrato.AutoUseCase;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** Hallazgo de la prueba en vivo del rol administrador: el servidor aceptaba un vehículo sin categoría. */
@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:autorent_categoria;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class AutoCategoriaTest {

    @Autowired private AutoUseCase autos;

    @Test
    void noSeGuardaUnVehiculoSinCategoria() {
        for (String categoria : new String[]{null, "", "   "}) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> autos.guardar(
                    new Auto(null, "CAT-001", "Kia", "Rio", 2024, categoria, "Rojo",
                            new BigDecimal("100.00"), "sin categoría", EstadoAuto.DISPONIBLE)));
            assertEquals("Seleccione la categoría del vehículo.", ex.getMessage());
        }
        assertNotNull(autos.guardar(new Auto(null, "CAT-002", "Kia", "Rio", 2024, "Compacto", "Rojo",
                new BigDecimal("100.00"), "con categoría", EstadoAuto.DISPONIBLE)).id());
    }
}
