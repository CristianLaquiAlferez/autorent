package pe.edu.upt.autorent.repositorio;

import java.math.BigDecimal;
import pe.edu.upt.autorent.modelo.ResultadoCobro;
import pe.edu.upt.autorent.modelo.ResultadoReembolso;

/**
 * Puerto de salida: cobro con Yape mediante el "código de aprobación" que el cliente ve en su app de Yape.
 * Es el mismo flujo que usan las pasarelas reales (Culqi, Niubiz, Mercado Pago): número de celular + código de 6 dígitos.
 * Hoy lo implementa una pasarela de prueba; para cobrar de verdad se cambia el adaptador, no el resto del sistema.
 */
public interface PasarelaYapePort {
    /** Nombre de la pasarela para mostrarlo en la pantalla de pago. */
    String nombre();
    /** Cobra el monto al Yape del celular indicado usando su código de aprobación. */
    ResultadoCobro cobrar(String celular, String codigoAprobacion, BigDecimal montoSoles, String descripcion);
    /** Devuelve un cobro hecho por esta pasarela. */
    ResultadoReembolso reembolsar(String codigoTransaccion, BigDecimal montoSoles);
}
