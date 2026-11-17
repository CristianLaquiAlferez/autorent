package pe.edu.upt.autorent.servicio.contrato;

import pe.edu.upt.autorent.modelo.Devolucion;
import pe.edu.upt.autorent.modelo.EstadoReserva;
import pe.edu.upt.autorent.modelo.EventoReserva;
import pe.edu.upt.autorent.modelo.MetodoPago;
import pe.edu.upt.autorent.modelo.Pago;
import pe.edu.upt.autorent.modelo.Reserva;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
/** Puerto de entrada: casos de uso de reservas, pagos presenciales, entregas, devoluciones y tareas automáticas. */

public interface ReservaUseCase {
    /** Caso de uso: el cliente reserva un vehículo desde la web y registra su anticipo (Yape, efectivo o pago en línea). */
    Reserva crearReservaOnline(String username, Long autoId, LocalDate inicio, LocalDate fin,
                               BigDecimal anticipo, MetodoPago metodo, String telefonoYape,
                               String codigoOperacion, String telefonoDevolucion);
    /** Caso de uso: el trabajador registra una reserva en mostrador; el anticipo queda confirmado al instante. */
    Reserva crearReservaPresencial(Long clienteId, Long autoId, LocalDate inicio, LocalDate fin,
                                   BigDecimal anticipo, MetodoPago metodo, String telefonoYape,
                                   String codigoOperacion, String telefonoDevolucion, String trabajador);
    /** Caso de uso: listar las reservas del usuario (el cliente solo ve las suyas). */
    List<Reserva> listarReservas(String username);
    /** Caso de uso: listar todas las reservas (personal de la empresa). */
    List<Reserva> listarTodas();
    /** Caso de uso: obtener una reserva por su código. */
    Reserva obtenerReserva(Long id);
    /** Caso de uso: cancelar una reserva. El cliente debe respetar la regla de 24 horas; el personal puede anularla antes de la entrega. */
    void cancelarReserva(Long reservaId, String username, String motivo);
    /** Caso de uso: confirmar un pago pendiente (por ejemplo, un Yape verificado). */
    void confirmarPago(Long pagoId, String trabajador);
    /** Caso de uso: rechazar un pago pendiente que no se pudo verificar. */
    void rechazarPago(Long pagoId, String trabajador, String motivo);
    /** Caso de uso: el cliente vuelve a enviar su anticipo después de un rechazo. */
    Reserva reintentarAnticipo(String username, Long reservaId, BigDecimal anticipo, MetodoPago metodo,
                               String telefonoYape, String codigoOperacion, String telefonoDevolucion);
    /** Caso de uso: el trabajador registra el pago del saldo restante de una reserva confirmada. */
    Pago registrarPagoSaldo(Long reservaId, BigDecimal monto, MetodoPago metodo,
                            String telefonoYape, String codigoOperacion, String telefonoDevolucion, String trabajador);
    /** Caso de uso: entregar el vehículo al cliente (inicia el alquiler). */
    void entregarVehiculo(Long reservaId, String trabajador, String observacion);
    /** Caso de uso: recibir el vehículo devuelto (finaliza el alquiler). */
    void recibirVehiculo(Long reservaId, String trabajador, String observacion, BigDecimal cargoDanios, String conceptoCargo);
    /** Caso de uso: cerrar una reserva cuya fecha pasó sin anticipo confirmado. */
    void vencerReservaSinPago(Long reservaId, String trabajador);
    /** Caso de uso: listar todos los pagos. */
    List<Pago> listarPagos();
    /** Caso de uso: listar los pagos del cliente que inició sesión. */
    List<Pago> listarPagosDeCliente(String username);
    /** Caso de uso: listar todas las devoluciones de dinero. */
    List<Devolucion> listarDevoluciones();
    /** Caso de uso: listar las devoluciones de dinero del cliente. */
    List<Devolucion> listarDevolucionesDeCliente(String username);
    /** Caso de uso: marcar como completada una devolución de dinero. */
    void completarDevolucion(Long devolucionId, String trabajador, String codigoOperacion);
    /** Bitácora (historial) de la reserva: cada paso con fecha, usuario y detalle. */
    List<EventoReserva> historialDe(Long reservaId);
    /** Lo que el cliente todavía debe de alquileres ya finalizados. */
    BigDecimal deudaDelCliente(Long clienteId);

    /** Consulta: saldo pendiente de cada reserva (total menos pagos confirmados). */
    Map<Long, BigDecimal> calcularSaldos(List<Reserva> reservas, List<Pago> pagos);
    /** Consulta: códigos de las reservas que el cliente todavía puede cancelar (regla de 24 horas). */
    Set<Long> reservasCancelables(List<Reserva> reservas);
    /** Consulta: códigos de las reservas que el personal puede anular (cualquier reserva activa antes de la entrega). */
    Set<Long> reservasAnulablesPorPersonal(List<Reserva> reservas);
    /** Consulta: códigos de las reservas en las que el cliente puede reintentar el anticipo. */
    Set<Long> reservasReintentables(List<Reserva> reservas, List<Pago> pagos);

    /** Consulta: pago en línea pendiente de una reserva del cliente (para llevarlo a la pantalla de pago). */
    Optional<Pago> buscarPagoEnLineaPendiente(String username, Long reservaId);

    /** Tarea automática: rechaza pagos en línea abandonados (el cliente no terminó de pagar en 30 minutos). */
    int liberarPagosEnLineaAbandonados();
    /** Tarea automática: vence reservas que no pagaron el anticipo a tiempo y libera el vehículo. */
    int vencerReservasSinPago();
    /** Tarea automática: marca como NO_PRESENTADO las reservas confirmadas cuyo periodo terminó sin recoger el auto. */
    int marcarNoPresentados();
}
