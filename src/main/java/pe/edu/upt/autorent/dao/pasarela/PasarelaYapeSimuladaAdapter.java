package pe.edu.upt.autorent.dao.pasarela;

import java.math.BigDecimal;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;
import pe.edu.upt.autorent.modelo.MetodoPago;
import pe.edu.upt.autorent.modelo.ResultadoCobro;
import pe.edu.upt.autorent.modelo.ResultadoReembolso;
import pe.edu.upt.autorent.repositorio.PasarelaYapePort;

/**
 * Adaptador: pasarela de PRUEBA de Yape con código de aprobación.
 * No se conecta a Yape ni mueve dinero. Valida el celular y el código como lo haría la pasarela real
 * y responde según códigos de prueba conocidos:
 *   cualquier código de 6 dígitos -> aprobado
 *   000000                         -> código de aprobación incorrecto o vencido
 *   111111                         -> saldo insuficiente en Yape
 */
@Component
public class PasarelaYapeSimuladaAdapter implements PasarelaYapePort {
    private static final String LETRAS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom aleatorio = new SecureRandom();

    @Override public String nombre() { return "Pasarela de prueba AutoRent (Yape con código de aprobación)"; }

    @Override public ResultadoCobro cobrar(String celular, String codigoAprobacion, BigDecimal montoSoles, String descripcion) {
        String numero = celular == null ? "" : celular.replaceAll("\\s", "");
        String codigo = codigoAprobacion == null ? "" : codigoAprobacion.trim();
        if (!numero.matches("9\\d{8}")) return ResultadoCobro.rechazado("El celular de Yape debe tener 9 dígitos y empezar con 9 (sin +51).");
        if (!codigo.matches("\\d{6}")) return ResultadoCobro.rechazado("El código de aprobación tiene 6 dígitos. Lo encuentra en el menú de su app de Yape.");
        if (montoSoles == null || montoSoles.signum() <= 0) return ResultadoCobro.rechazado("Monto inválido.");
        if (codigo.equals("000000")) return ResultadoCobro.rechazado("Código de aprobación incorrecto o vencido. Genere uno nuevo en su app de Yape.");
        if (codigo.equals("111111")) return ResultadoCobro.rechazado("Saldo insuficiente en Yape.");
        return new ResultadoCobro(true, "YSIM-" + codigo(12), MetodoPago.YAPE_EN_LINEA,
                "Yape *****" + numero.substring(5), "Pago aprobado.", montoSoles, "PEN");
    }

    @Override public ResultadoReembolso reembolsar(String codigoTransaccion, BigDecimal montoSoles) {
        if (codigoTransaccion == null || !codigoTransaccion.startsWith("YSIM-"))
            return new ResultadoReembolso(false, null, "La transacción no pertenece a esta pasarela.");
        return new ResultadoReembolso(true, "YSIMR-" + codigo(10), "Reembolso aprobado.");
    }

    private String codigo(int largo) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < largo; i++) sb.append(LETRAS.charAt(aleatorio.nextInt(LETRAS.length())));
        return sb.toString();
    }
}
