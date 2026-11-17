package pe.edu.upt.autorent;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
/** Punto de entrada de Spring Boot. Inicia los componentes de los seis paquetes. */

@SpringBootApplication
public class AutoRentApplication {
    public static void main(String[] args) {
        // La empresa opera en Perú: se fija la zona horaria aunque el servidor esté en otro país (UTC).
        TimeZone.setDefault(TimeZone.getTimeZone(System.getenv().getOrDefault("AUTORENT_ZONA_HORARIA", "America/Lima")));
        SpringApplication.run(AutoRentApplication.class, args);
    }
}
