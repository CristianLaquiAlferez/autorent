package pe.edu.upt.autorent.dao.pasarela;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import pe.edu.upt.autorent.modelo.DatosTarjeta;
import pe.edu.upt.autorent.modelo.MetodoPago;
import pe.edu.upt.autorent.modelo.ResultadoCobro;
import static org.junit.jupiter.api.Assertions.*;

/** Pruebas de la pasarela de tarjeta de prueba: validaciones como una pasarela real y respuestas por tarjeta. */
class PasarelaTarjetaSimuladaAdapterTest {
    private final Clock reloj = Clock.fixed(Instant.parse("2026-11-16T15:00:00Z"), ZoneId.of("America/Lima"));
    private final PasarelaTarjetaSimuladaAdapter pasarela = new PasarelaTarjetaSimuladaAdapter(reloj);
    private static final BigDecimal MONTO = new BigDecimal("150.00");

    private ResultadoCobro cobrar(String numero, String vence, String cvv) {
        return pasarela.cobrar(new DatosTarjeta(numero, "Luis Apaza", vence, cvv), MONTO, "Prueba");
    }

    @Test void apruebaVisaYMastercardDePrueba() {
        ResultadoCobro visa = cobrar("4111 1111 1111 1111", "12/28", "123");
        assertTrue(visa.aprobado());
        assertEquals(MetodoPago.TARJETA, visa.metodo());
        assertEquals("VISA **** 1111", visa.detalle());
        assertTrue(visa.codigoTransaccion().startsWith("SIM-"));
        ResultadoCobro master = cobrar("5555-5555-5555-4444", "01/27", "456");
        assertTrue(master.aprobado());
        assertEquals("MASTERCARD **** 4444", master.detalle());
    }

    @Test void simulaRechazosDelBanco() {
        assertEquals("Tarjeta rechazada por el banco emisor.", cobrar("4000000000000002", "12/28", "123").mensaje());
        assertEquals("Fondos insuficientes.", cobrar("4000000000009995", "12/28", "123").mensaje());
        assertFalse(cobrar("5105105105105100", "12/28", "123").aprobado());
        assertFalse(cobrar("4111111111111111", "12/28", "000").aprobado());
    }

    @Test void validaNumeroMarcaVencimientoYCvv() {
        assertTrue(cobrar("4111111111111112", "12/28", "123").mensaje().contains("no es válido"));      // falla Luhn
        assertTrue(cobrar("378282246310005", "12/28", "123").mensaje().contains("Visa y Mastercard")); // American Express
        assertTrue(cobrar("4111111111111111", "10/26", "123").mensaje().contains("vencida"));
        assertTrue(cobrar("4111111111111111", "13/28", "123").mensaje().contains("MM/AA"));
        assertTrue(cobrar("4111111111111111", "12/28", "12").mensaje().contains("CVV"));
        assertTrue(cobrar("4111111111111111", "11/26", "123").aprobado());                              // vence este mes: válida
    }

    @Test void elNumeroCompletoNoApareceEnTextos() {
        DatosTarjeta t = new DatosTarjeta("4111111111111111", "Luis", "12/28", "123");
        assertFalse(t.toString().contains("411111111111"));
        assertEquals("1111", t.ultimos4());
    }

    @Test void reembolsaSoloTransaccionesPropias() {
        assertTrue(pasarela.reembolsar("SIM-ABC", MONTO).exitoso());
        assertFalse(pasarela.reembolsar("OTRA-123", MONTO).exitoso());
    }
}
