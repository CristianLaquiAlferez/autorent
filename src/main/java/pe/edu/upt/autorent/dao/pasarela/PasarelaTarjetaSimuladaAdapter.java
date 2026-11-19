package pe.edu.upt.autorent.dao.pasarela;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.YearMonth;
import java.util.Map;
import org.springframework.stereotype.Component;
import pe.edu.upt.autorent.modelo.DatosTarjeta;
import pe.edu.upt.autorent.modelo.MetodoPago;
import pe.edu.upt.autorent.modelo.ResultadoCobro;
import pe.edu.upt.autorent.modelo.ResultadoReembolso;
import pe.edu.upt.autorent.repositorio.PasarelaTarjetaPort;

/**
 * Adaptador: pasarela de pagos de PRUEBA para tarjetas Visa y Mastercard.
 *
 * Valida la tarjeta como lo haría una pasarela real (algoritmo de Luhn, marca, fecha de vencimiento y CVV)
 * y responde según tarjetas de prueba conocidas, igual que los "modos de prueba" de Culqi, Stripe o Niubiz.
 * No mueve dinero real ni se conecta a internet. Nunca guarda el número de la tarjeta: solo la marca y
 * los últimos 4 dígitos para mostrarlos.
 *
 * Tarjetas de prueba:
 *   4111 1111 1111 1111 (Visa)        -> aprobada
 *   5555 5555 5555 4444 (Mastercard)  -> aprobada
 *   4000 0000 0000 0002 (Visa)        -> rechazada por el banco emisor
 *   4000 0000 0000 9995 (Visa)        -> fondos insuficientes
 *   5105 1051 0510 5100 (Mastercard)  -> tarjeta bloqueada
 *   Cualquier CVV 000                 -> código de seguridad incorrecto
 */
@Component
public class PasarelaTarjetaSimuladaAdapter implements PasarelaTarjetaPort {
    private static final Map<String, String> RECHAZOS = Map.of(
            "4000000000000002", "Tarjeta rechazada por el banco emisor.",
            "4000000000009995", "Fondos insuficientes.",
            "5105105105105100", "Tarjeta bloqueada. Comuníquese con su banco.");
    private static final String LETRAS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final SecureRandom aleatorio = new SecureRandom();
    private final Clock reloj;

    public PasarelaTarjetaSimuladaAdapter(Clock reloj) { this.reloj = reloj; }

    @Override public String nombre() { return "Pasarela de prueba AutoRent (Visa / Mastercard)"; }

    @Override public ResultadoCobro cobrar(DatosTarjeta tarjeta, BigDecimal montoSoles, String descripcion) {
        if (tarjeta == null) return ResultadoCobro.rechazado("Ingrese los datos de la tarjeta.");
        String numero = tarjeta.numeroLimpio();
        if (!numero.matches("\\d{13,19}")) return ResultadoCobro.rechazado("El número de tarjeta debe tener solo dígitos (13 a 19).");
        if (!luhnValido(numero)) return ResultadoCobro.rechazado("El número de tarjeta no es válido. Revise los dígitos.");
        String marca = marca(numero);
        if (marca == null) return ResultadoCobro.rechazado("Solo se aceptan tarjetas Visa y Mastercard.");
        if (tarjeta.titular() == null || tarjeta.titular().trim().length() < 3)
            return ResultadoCobro.rechazado("Escriba el nombre del titular como figura en la tarjeta.");
        if (!tarjeta.titular().trim().matches("[A-Za-zÁÉÍÓÚÜÑáéíóúüñ]+([ '\\-][A-Za-zÁÉÍÓÚÜÑáéíóúüñ]+)*") || tarjeta.titular().trim().length() > 60)
            return ResultadoCobro.rechazado("El nombre del titular solo admite letras, como figura en la tarjeta.");
        String errorVencimiento = validarVencimiento(tarjeta.vencimiento());
        if (errorVencimiento != null) return ResultadoCobro.rechazado(errorVencimiento);
        if (tarjeta.cvv() == null || !tarjeta.cvv().matches("\\d{3}")) return ResultadoCobro.rechazado("El CVV debe tener 3 dígitos.");
        if (tarjeta.cvv().equals("000")) return ResultadoCobro.rechazado("Código de seguridad (CVV) incorrecto.");
        if (montoSoles == null || montoSoles.signum() <= 0) return ResultadoCobro.rechazado("Monto inválido.");
        String rechazo = RECHAZOS.get(numero);
        if (rechazo != null) return ResultadoCobro.rechazado(rechazo);
        return new ResultadoCobro(true, "SIM-" + codigo(12), MetodoPago.TARJETA, marca + " **** " + tarjeta.ultimos4(),
                "Pago aprobado.", montoSoles, "PEN");
    }

    @Override public ResultadoReembolso reembolsar(String codigoTransaccion, BigDecimal montoSoles) {
        if (codigoTransaccion == null || !codigoTransaccion.startsWith("SIM-"))
            return new ResultadoReembolso(false, null, "La transacción no pertenece a esta pasarela.");
        return new ResultadoReembolso(true, "SIMR-" + codigo(10), "Reembolso aprobado.");
    }

    /** Algoritmo de Luhn: detecta errores de tipeo en el número de tarjeta. */
    static boolean luhnValido(String numero) {
        int suma = 0;
        boolean duplicar = false;
        for (int i = numero.length() - 1; i >= 0; i--) {
            int d = numero.charAt(i) - '0';
            if (duplicar) { d *= 2; if (d > 9) d -= 9; }
            suma += d;
            duplicar = !duplicar;
        }
        return suma % 10 == 0;
    }

    /** Visa empieza con 4; Mastercard con 51-55 o 2221-2720. */
    static String marca(String numero) {
        if (numero.startsWith("4") && (numero.length() == 13 || numero.length() == 16 || numero.length() == 19)) return "VISA";
        if (numero.length() == 16) {
            int dos = Integer.parseInt(numero.substring(0, 2));
            int cuatro = Integer.parseInt(numero.substring(0, 4));
            if ((dos >= 51 && dos <= 55) || (cuatro >= 2221 && cuatro <= 2720)) return "MASTERCARD";
        }
        return null;
    }

    /** Vencimiento en formato MM/AA; la tarjeta vale hasta el último día de ese mes. */
    private String validarVencimiento(String vencimiento) {
        if (vencimiento == null || !vencimiento.trim().matches("(0[1-9]|1[0-2])/\\d{2}"))
            return "La fecha de vencimiento debe tener el formato MM/AA.";
        String[] partes = vencimiento.trim().split("/");
        YearMonth vence = YearMonth.of(2000 + Integer.parseInt(partes[1]), Integer.parseInt(partes[0]));
        if (vence.isBefore(YearMonth.now(reloj))) return "La tarjeta está vencida.";
        return null;
    }

    private String codigo(int largo) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < largo; i++) sb.append(LETRAS.charAt(aleatorio.nextInt(LETRAS.length())));
        return sb.toString();
    }
}
