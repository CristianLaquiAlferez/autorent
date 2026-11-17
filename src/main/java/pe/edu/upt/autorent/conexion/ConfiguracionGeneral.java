package pe.edu.upt.autorent.conexion;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuración general de la aplicación.
 *
 * Reloj del sistema: todas las fechas del negocio ("hoy", "ahora") se toman de este Clock con la zona
 * horaria de Perú. Así el sistema funciona igual en la PC del laboratorio y en un servidor en la nube,
 * que normalmente está en hora UTC (5 horas adelantada). Además, en las pruebas se puede usar un reloj fijo.
 *
 * @EnableScheduling activa las tareas automáticas (liberar reservas sin pago, marcar no presentados).
 */
@Configuration
@EnableScheduling
public class ConfiguracionGeneral {

    @Bean
    public Clock reloj(@Value("${autorent.zona-horaria:America/Lima}") String zona) {
        return Clock.system(ZoneId.of(zona));
    }
}
