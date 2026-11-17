package pe.edu.upt.autorent.conexion;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Conexion a MySQL: Spring Boot crea el DataSource usando application.properties.
 * Este componente expone JdbcTemplate para las consultas SQL del adaptador DAO.
 * No abre conexiones manuales ni almacena contraseñas en el código.
 */
@Configuration
public class ConexionMySQLConfig {

    /** Reutiliza el pool de conexiones administrado por Spring Boot. */
    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
