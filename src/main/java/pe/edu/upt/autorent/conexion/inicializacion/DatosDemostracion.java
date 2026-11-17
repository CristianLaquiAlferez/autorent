package pe.edu.upt.autorent.conexion.inicializacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import pe.edu.upt.autorent.modelo.*;
import pe.edu.upt.autorent.repositorio.UsuarioRepositoryPort;
import pe.edu.upt.autorent.servicio.contrato.AutoUseCase;
import pe.edu.upt.autorent.servicio.contrato.ReservaUseCase;
import pe.edu.upt.autorent.servicio.contrato.UsuarioUseCase;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Reservas de demostración para la presentación: una en cada estado del proceso
 * (finalizada, en curso, confirmada con saldo, Yape por validar y cancelada con devolución pendiente).
 * Se crean una sola vez, usando los mismos casos de uso que la web, así que tienen pagos, boletas e historial reales.
 * Se activa con autorent.demo.reservas=true (variable DATOS_DEMO); las pruebas no la activan.
 */
@Configuration
public class DatosDemostracion {

    private static final Logger log = LoggerFactory.getLogger(DatosDemostracion.class);
    private static final String TRABAJADOR = "trabajador";

    @Bean
    @Order(2)
    CommandLineRunner cargarReservasDeDemostracion(@Value("${autorent.demo.reservas:false}") boolean activo,
                                                    UsuarioRepositoryPort usuariosRepo, UsuarioUseCase usuarios,
                                                    AutoUseCase autos, ReservaUseCase reservas, Clock reloj) {
        return args -> {
            if (!activo || usuariosRepo.existsByUsername("demo.ana")) return;
            LocalDate hoy = LocalDate.now(reloj);
            // Contraseñas aleatorias: estas cuentas solo son dueñas de las reservas de ejemplo; nadie inicia sesión con ellas.
            Usuario ana = usuarios.registrarCliente("demo.ana", clave(), "Ana", "Torres Mamani",
                    "70000007", "ana.torres@autorent.demo", "987100200", "Tacna");
            Usuario jorge = usuarios.registrarCliente("demo.jorge", clave(), "Jorge", "Quispe Flores",
                    "70000008", "jorge.quispe@autorent.demo", "987300400", "Tacna");

            // 1) FINALIZADA: pagada completa, entregada y devuelta hoy.
            intentar("finalizada", autos, a -> {
                Reserva r = reservas.crearReservaPresencial(ana.id(), a.id(), hoy, hoy.plusDays(2),
                        total(a, 2), MetodoPago.EFECTIVO, null, null, ana.telefono(), TRABAJADOR);
                reservas.entregarVehiculo(r.id(), TRABAJADOR, "Tanque lleno, 45 200 km, sin daños.");
                reservas.recibirVehiculo(r.id(), TRABAJADOR, "Devuelto antes de la fecha, en buen estado.", BigDecimal.ZERO, null);
                return r;
            });
            // 2) EN CURSO: pagada completa y entregada.
            intentar("en curso", autos, a -> {
                Reserva r = reservas.crearReservaPresencial(ana.id(), a.id(), hoy, hoy.plusDays(3),
                        total(a, 3), MetodoPago.EFECTIVO, null, null, ana.telefono(), TRABAJADOR);
                reservas.entregarVehiculo(r.id(), TRABAJADOR, "Entrega en counter, tanque lleno.");
                return r;
            });
            // 3) CONFIRMADA con saldo: anticipo del 30 % en efectivo; el saldo se cobra al entregar.
            intentar("confirmada con saldo", autos, a -> reservas.crearReservaPresencial(ana.id(), a.id(),
                    hoy.plusDays(10), hoy.plusDays(13), porcentaje(total(a, 3), 30), MetodoPago.EFECTIVO,
                    null, null, ana.telefono(), TRABAJADOR));
            // 4) PENDIENTE: el cliente reservó en la web y pagó el anticipo por Yape; el trabajador debe validarlo.
            intentar("Yape por validar", autos, a -> reservas.crearReservaOnline(jorge.username(), a.id(),
                    hoy.plusDays(15), hoy.plusDays(17), porcentaje(total(a, 2), 20), MetodoPago.YAPE,
                    jorge.telefono(), "DEMO" + (System.currentTimeMillis() % 1_000_000), jorge.telefono()));
            // 5) CANCELADA: el personal la anula con motivo; queda una devolución pendiente de completar.
            intentar("cancelada", autos, a -> {
                Reserva r = reservas.crearReservaPresencial(jorge.id(), a.id(), hoy.plusDays(20), hoy.plusDays(22),
                        porcentaje(total(a, 2), 50), MetodoPago.EFECTIVO, null, null, jorge.telefono(), TRABAJADOR);
                reservas.cancelarReserva(r.id(), TRABAJADOR, "El cliente cambió la fecha de su viaje.");
                return r;
            });
        };
    }

    /** Prueba los autos disponibles uno por uno hasta que la reserva se pueda registrar (evita cruces con reservas reales). */
    private void intentar(String nombre, AutoUseCase autos, Function<Auto, Reserva> crear) {
        List<Auto> disponibles = autos.listarDisponibles();
        for (Auto a : disponibles) {
            try {
                Reserva r = crear.apply(a);
                log.info("Reserva de demostración '{}' creada: R-{} con {}", nombre, r.id(), a.placa());
                return;
            } catch (RuntimeException e) {
                log.debug("Auto {} no sirve para la reserva '{}': {}", a.placa(), nombre, e.getMessage());
            }
        }
        log.warn("No se pudo crear la reserva de demostración '{}'.", nombre);
    }

    private static BigDecimal total(Auto a, int dias) {
        return a.tarifaDiaria().multiply(BigDecimal.valueOf(dias));
    }

    private static BigDecimal porcentaje(BigDecimal total, int pct) {
        return total.multiply(BigDecimal.valueOf(pct)).divide(BigDecimal.valueOf(100), 2, RoundingMode.UP);
    }

    private static String clave() {
        return UUID.randomUUID().toString();
    }
}
