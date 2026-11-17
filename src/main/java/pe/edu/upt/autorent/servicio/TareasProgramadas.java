package pe.edu.upt.autorent.servicio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.edu.upt.autorent.servicio.contrato.ReservaUseCase;

/**
 * Tareas automáticas que mantienen el sistema ordenado sin que nadie tenga que acordarse:
 * cada 5 minutos libera pagos en línea abandonados, vence reservas sin anticipo y marca no presentados.
 * Se pueden desactivar con autorent.tareas.habilitadas=false (por ejemplo, en las pruebas).
 */
@Component
@ConditionalOnProperty(name = "autorent.tareas.habilitadas", havingValue = "true", matchIfMissing = true)
public class TareasProgramadas {
    private static final Logger log = LoggerFactory.getLogger(TareasProgramadas.class);
    private final ReservaUseCase reservas;

    public TareasProgramadas(ReservaUseCase reservas) { this.reservas = reservas; }

    @Scheduled(initialDelayString = "${autorent.tareas.retraso-inicial-ms:60000}", fixedDelayString = "${autorent.tareas.intervalo-ms:300000}")
    public void ejecutar() {
        try {
            reservas.liberarPagosEnLineaAbandonados();
            reservas.vencerReservasSinPago();
            reservas.marcarNoPresentados();
        } catch (RuntimeException ex) {
            log.warn("Error en las tareas automáticas: {}", ex.getMessage());
        }
    }
}
