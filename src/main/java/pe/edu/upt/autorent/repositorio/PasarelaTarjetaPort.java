package pe.edu.upt.autorent.repositorio;

import java.math.BigDecimal;
import pe.edu.upt.autorent.modelo.DatosTarjeta;
import pe.edu.upt.autorent.modelo.ResultadoCobro;
import pe.edu.upt.autorent.modelo.ResultadoReembolso;

/**
 * Puerto de salida: pasarela que cobra con tarjeta Visa o Mastercard.
 * Hoy lo implementa una pasarela de prueba (PasarelaTarjetaSimuladaAdapter). Para una pasarela real
 * (Culqi, Niubiz, Izipay) bastaría con otro adaptador: el servicio no cambia.
 */
public interface PasarelaTarjetaPort {
    /** Nombre que se muestra en la pantalla de pago. */
    String nombre();
    /** Cobra el monto en soles. Si la tarjeta es rechazada devuelve aprobado = false con el motivo. */
    ResultadoCobro cobrar(DatosTarjeta tarjeta, BigDecimal montoSoles, String descripcion);
    /** Devuelve el dinero de un cobro anterior. */
    ResultadoReembolso reembolsar(String codigoTransaccion, BigDecimal montoSoles);
}
