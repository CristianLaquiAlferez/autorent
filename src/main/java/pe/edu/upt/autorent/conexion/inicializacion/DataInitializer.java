package pe.edu.upt.autorent.conexion.inicializacion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pe.edu.upt.autorent.modelo.*;
import pe.edu.upt.autorent.repositorio.AutoRepositoryPort;
import pe.edu.upt.autorent.repositorio.PasswordEncoderPort;
import pe.edu.upt.autorent.repositorio.UsuarioRepositoryPort;
import java.math.BigDecimal;
/** Al arrancar, crea los usuarios y vehículos de demostración si todavía no existen. */

@Configuration
public class DataInitializer {
    /** CommandLineRunner: Spring lo ejecuta una vez, apenas termina de iniciar la aplicación. */
    /**
     * Las contraseñas de las cuentas de demostración se pueden cambiar con variables de entorno.
     * En un servidor público (Railway) se deben definir, porque las contraseñas por defecto están en el código.
     */
    @Bean
    CommandLineRunner cargarDatosIniciales(UsuarioRepositoryPort usuarios, AutoRepositoryPort autos,
                                            PasswordEncoderPort encoder,
                                            @Value("${autorent.demo.clave-admin:Admin123*}") String claveAdmin,
                                            @Value("${autorent.demo.clave-trabajador:Trabajador123*}") String claveTrabajador,
                                            @Value("${autorent.demo.clave-cliente:Cliente123*}") String claveCliente,
                                            @Value("${autorent.demo.clave-docente:}") String claveDocente,
                                            @Value("${autorent.demo.clave-curso-trabajador:}") String claveCursoTrabajador,
                                            @Value("${autorent.demo.clave-curso-cliente:}") String claveCursoCliente) {
        return args -> {
            crearUsuarioSiNoExiste(usuarios, encoder, "admin", claveAdmin, "Administrador", "AutoRent",
                    "70000001", "admin@autorent.demo", "999111111", "Sucursal principal", Rol.ADMIN);
            crearUsuarioSiNoExiste(usuarios, encoder, "trabajador", claveTrabajador, "Carlos", "Recepción",
                    "70000002", "trabajador@autorent.demo", "999222222", "Sucursal principal", Rol.TRABAJADOR);
            crearUsuarioSiNoExiste(usuarios, encoder, "cliente", claveCliente, "María", "Cliente Demo",
                    "70000003", "cliente@autorent.demo", "999333333", "Tacna", Rol.CLIENTE);

            // Cuentas para la presentación: solo se crean si su contraseña está definida en una variable de entorno
            // (CLAVE_DOCENTE, CLAVE_CURSO_TRABAJADOR, CLAVE_CURSO_CLIENTE). No hay contraseña por defecto en el código.
            if (!claveDocente.isBlank()) {
                crearUsuarioSiNoExiste(usuarios, encoder, "docente", claveDocente, "Docente", "Programación III",
                        "70000004", "docente@autorent.demo", "999444444", "Universidad Privada de Tacna", Rol.ADMIN);
            }
            if (!claveCursoTrabajador.isBlank()) {
                crearUsuarioSiNoExiste(usuarios, encoder, "curso.trabajador", claveCursoTrabajador, "Asesor", "Curso",
                        "70000005", "curso.trabajador@autorent.demo", "999555555", "Sucursal principal", Rol.TRABAJADOR);
            }
            if (!claveCursoCliente.isBlank()) {
                crearUsuarioSiNoExiste(usuarios, encoder, "curso.cliente", claveCursoCliente, "Cliente", "Curso",
                        "70000006", "curso.cliente@autorent.demo", "999666666", "Tacna", Rol.CLIENTE);
            }

            if (!autos.existsAny()) {
                autos.save(new Auto(null, "V1A-101", "Toyota", "Corolla", 2023, "Sedán", "Plata", new BigDecimal("150.00"), "Sedán cómodo para ciudad y carretera.", EstadoAuto.DISPONIBLE));
                autos.save(new Auto(null, "V1B-202", "Kia", "Rio", 2022, "Compacto", "Blanco", new BigDecimal("110.00"), "Vehículo económico para recorridos urbanos.", EstadoAuto.DISPONIBLE));
                autos.save(new Auto(null, "V1C-303", "Hyundai", "Accent", 2024, "Sedán", "Negro", new BigDecimal("135.00"), "Auto moderno con aire acondicionado.", EstadoAuto.DISPONIBLE));
                autos.save(new Auto(null, "V1D-404", "Suzuki", "Swift", 2023, "Hatchback", "Rojo", new BigDecimal("100.00"), "Compacto y fácil de estacionar.", EstadoAuto.DISPONIBLE));
                autos.save(new Auto(null, "V1E-505", "Toyota", "Hilux", 2022, "Camioneta", "Gris", new BigDecimal("250.00"), "Camioneta para viajes y carga ligera.", EstadoAuto.DISPONIBLE));
            }
        };
    }

    /** No duplica cuentas: si el usuario ya existe en la base de datos, no hace nada. */
    private void crearUsuarioSiNoExiste(UsuarioRepositoryPort usuarios, PasswordEncoderPort encoder,
                                        String username, String password, String nombres, String apellidos,
                                        String documento, String correo, String telefono, String direccion, Rol rol) {
        if (usuarios.findByUsername(username).isEmpty()) {
            usuarios.save(new Usuario(null, username, encoder.encode(password), nombres, apellidos,
                    documento, correo, telefono, direccion, rol, true));
        }
    }
}
