package pe.edu.upt.autorent.modelo;

import org.junit.jupiter.api.Test;
import pe.edu.upt.autorent.modelo.ReglasAlquiler;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class ReglasAlquilerTest {
    @Test void calculaElVeintePorCientoDelTotal() {
        assertEquals(new BigDecimal("100.00"), ReglasAlquiler.minimoAnticipo(new BigDecimal("500.00")));
    }

    @Test void rechazaAnticipoMenorAlVeintePorCiento() {
        assertThrows(IllegalArgumentException.class,
                () -> ReglasAlquiler.validarAnticipo(new BigDecimal("99.99"), new BigDecimal("500.00")));
    }

    @Test void permiteAnticipoIgualAlTotal() {
        assertDoesNotThrow(() -> ReglasAlquiler.validarAnticipo(new BigDecimal("500.00"), new BigDecimal("500.00")));
    }

    @Test void calculaDiasEntreFechas() {
        assertEquals(3, ReglasAlquiler.calcularDias(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 4)));
    }

    @Test void rechazaFechaFinAnteriorOIgual() {
        assertThrows(IllegalArgumentException.class,
                () -> ReglasAlquiler.calcularDias(LocalDate.of(2026, 12, 4), LocalDate.of(2026, 12, 4)));
    }

    @Test void cancelacionSoloAntesDelLimiteDeVeinticuatroHoras() {
        LocalDate inicio = LocalDate.of(2026, 12, 10);
        assertTrue(ReglasAlquiler.sePuedeCancelar(inicio, LocalDateTime.of(2026, 12, 8, 23, 59)));
        assertTrue(ReglasAlquiler.sePuedeCancelar(inicio, LocalDateTime.of(2026, 12, 9, 0, 0)));
        assertFalse(ReglasAlquiler.sePuedeCancelar(inicio, LocalDateTime.of(2026, 12, 9, 0, 1)));
    }

    @Test void rechazaAlquileresDeMasDeTreintaDias() {
        LocalDate hoy = LocalDate.of(2026, 12, 1);
        assertEquals(30, ReglasAlquiler.validarPeriodo(hoy, hoy.plusDays(30), hoy));
        assertThrows(IllegalArgumentException.class, () -> ReglasAlquiler.validarPeriodo(hoy, hoy.plusDays(31), hoy));
    }

    @Test void rechazaReservasConMasDeSeisMesesDeAnticipacion() {
        LocalDate hoy = LocalDate.of(2026, 12, 1);
        assertDoesNotThrow(() -> ReglasAlquiler.validarPeriodo(hoy.plusDays(180), hoy.plusDays(182), hoy));
        assertThrows(IllegalArgumentException.class, () -> ReglasAlquiler.validarPeriodo(hoy.plusDays(181), hoy.plusDays(183), hoy));
        assertThrows(IllegalArgumentException.class, () -> ReglasAlquiler.validarPeriodo(hoy.minusDays(1), hoy.plusDays(2), hoy));
    }

    @Test void cuentaDiasDeRetrasoSoloSiDevuelveTarde() {
        LocalDate fin = LocalDate.of(2026, 12, 10);
        assertEquals(0, ReglasAlquiler.diasDeRetraso(fin, fin));
        assertEquals(0, ReglasAlquiler.diasDeRetraso(fin, fin.minusDays(2)));
        assertEquals(3, ReglasAlquiler.diasDeRetraso(fin, fin.plusDays(3)));
    }
}
