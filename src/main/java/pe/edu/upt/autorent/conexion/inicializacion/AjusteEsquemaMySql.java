package pe.edu.upt.autorent.conexion.inicializacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Ajuste del esquema en MySQL al arrancar.
 *
 * Las primeras versiones del proyecto dejaron que Hibernate creara las columnas de estados como ENUM de MySQL
 * (por ejemplo ENUM('EN_PROCESO','FINALIZADO')). Con spring.jpa.hibernate.ddl-auto=update, Hibernate agrega
 * tablas y columnas nuevas pero NO modifica una columna existente, así que un estado nuevo (PROGRAMADO)
 * fallaba al guardarse. Las entidades ya declaran esas columnas como VARCHAR; este paso convierte las columnas
 * ENUM que hayan quedado de antes. En H2 (pruebas) no hace nada.
 */
@Component
@Order(0)
public class AjusteEsquemaMySql implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AjusteEsquemaMySql.class);
    private final JdbcTemplate jdbc;

    public AjusteEsquemaMySql(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void run(ApplicationArguments args) {
        try {
            String motor = jdbc.execute((java.sql.Connection c) -> c.getMetaData().getDatabaseProductName());
            if (motor == null || !motor.toLowerCase().contains("mysql")) return;
            List<Map<String, Object>> columnas = jdbc.queryForList(
                    "select table_name as tabla, column_name as columna, is_nullable as nulo from information_schema.columns " +
                    "where table_schema = database() and data_type = 'enum'");
            for (Map<String, Object> c : columnas) {
                String tabla = String.valueOf(c.get("tabla"));
                String columna = String.valueOf(c.get("columna"));
                // Los nombres vienen del propio catálogo de MySQL; aun así solo se aceptan letras, números y guion bajo.
                if (!tabla.matches("[A-Za-z0-9_]+") || !columna.matches("[A-Za-z0-9_]+")) continue;
                String nulo = "NO".equalsIgnoreCase(String.valueOf(c.get("nulo"))) ? " NOT NULL" : " NULL";
                jdbc.execute("alter table `" + tabla + "` modify `" + columna + "` varchar(30)" + nulo);
                log.info("Columna {}.{} convertida de ENUM a VARCHAR(30)", tabla, columna);
            }
        } catch (RuntimeException ex) {
            log.warn("No se pudo revisar el esquema de MySQL: {}", ex.getMessage());
        }
    }
}
