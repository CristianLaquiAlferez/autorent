package pe.edu.upt.autorent.servicio;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upt.autorent.modelo.*;
import pe.edu.upt.autorent.repositorio.*;
import pe.edu.upt.autorent.servicio.contrato.ConsultaReservaUseCase;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Consultas de trazabilidad (solo lectura).
 *
 * Responde la pregunta "¿dónde está el historial de esta operación?":
 * - Expediente de la reserva: datos, pagos (quién los registró y quién los confirmó), boletas,
 *   devoluciones con su evidencia y la bitácora paso a paso.
 * - Caja: todos los pagos de un periodo, filtrables, con totales por método y devoluciones.
 */
@Service
@Transactional(readOnly = true)
public class ConsultaReservaService implements ConsultaReservaUseCase {
    private final ReservaRepositoryPort reservas;
    private final PagoRepositoryPort pagos;
    private final DevolucionRepositoryPort devoluciones;
    private final ComprobanteRepositoryPort comprobantes;
    private final UsuarioRepositoryPort usuarios;
    private final BitacoraReservas bitacora;

    public ConsultaReservaService(ReservaRepositoryPort reservas, PagoRepositoryPort pagos,
                                  DevolucionRepositoryPort devoluciones, ComprobanteRepositoryPort comprobantes,
                                  UsuarioRepositoryPort usuarios, BitacoraReservas bitacora) {
        this.reservas = reservas; this.pagos = pagos; this.devoluciones = devoluciones;
        this.comprobantes = comprobantes; this.usuarios = usuarios; this.bitacora = bitacora;
    }

    @Override public DetalleReserva detalle(Long reservaId, String username) {
        Usuario u = usuarios.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        Reserva r = reservas.findById(reservaId).orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada."));
        if (u.rol() == Rol.CLIENTE && !r.cliente().id().equals(u.id()))
            throw new IllegalArgumentException("Esa reserva no pertenece a su cuenta.");
        List<Pago> listaPagos = pagos.findByReservaId(reservaId).stream()
                .sorted(Comparator.comparing(Pago::registradoEn)).toList();
        List<Devolucion> listaDevoluciones = devoluciones.findByReservaId(reservaId).stream()
                .sorted(Comparator.comparing(Devolucion::registradoEn)).toList();
        BigDecimal pagado = sumar(listaPagos.stream().filter(p -> p.estado() == EstadoPago.CONFIRMADO).map(Pago::monto).toList());
        BigDecimal devuelto = sumar(listaDevoluciones.stream().map(Devolucion::monto).toList());
        // En una reserva cancelada, vencida o no presentada ya no hay nada que cobrar: el saldo es cero.
        boolean cerradaSinAlquiler = r.estado() == EstadoReserva.CANCELADA || r.estado() == EstadoReserva.VENCIDA
                || r.estado() == EstadoReserva.NO_PRESENTADO;
        BigDecimal saldo = cerradaSinAlquiler ? BigDecimal.ZERO.setScale(2)
                : r.total().subtract(pagado).setScale(2, RoundingMode.HALF_UP);
        return new DetalleReserva(r, listaPagos, comprobantes.findByReservaId(reservaId), listaDevoluciones,
                bitacora.de(reservaId), pagado, devuelto, saldo);
    }

    @Override public ReporteCaja caja(LocalDate desde, LocalDate hasta, MetodoPago metodo, EstadoPago estado) {
        if (desde != null && hasta != null && hasta.isBefore(desde))
            throw new IllegalArgumentException("La fecha 'hasta' no puede ser anterior a 'desde'.");
        List<Pago> filtrados = pagos.findAll().stream()
                .filter(p -> enPeriodo(p.registradoEn(), desde, hasta))
                .filter(p -> metodo == null || p.metodo() == metodo)
                .filter(p -> estado == null || p.estado() == estado)
                .sorted(Comparator.comparing(Pago::registradoEn).reversed())
                .toList();
        Map<MetodoPago, BigDecimal> porMetodo = new EnumMap<>(MetodoPago.class);
        for (MetodoPago m : MetodoPago.values()) porMetodo.put(m, BigDecimal.ZERO.setScale(2));
        BigDecimal confirmado = BigDecimal.ZERO, pendiente = BigDecimal.ZERO, rechazado = BigDecimal.ZERO;
        for (Pago p : filtrados) {
            switch (p.estado()) {
                case CONFIRMADO -> { confirmado = confirmado.add(p.monto()); porMetodo.merge(p.metodo(), p.monto(), BigDecimal::add); }
                case PENDIENTE -> pendiente = pendiente.add(p.monto());
                case RECHAZADO -> rechazado = rechazado.add(p.monto());
            }
        }
        List<Devolucion> devs = devoluciones.findAll().stream()
                .filter(d -> enPeriodo(d.registradoEn(), desde, hasta))
                .filter(d -> metodo == null || d.metodo() == metodo)
                .toList();
        BigDecimal devuelto = sumar(devs.stream().map(Devolucion::monto).toList());
        Map<Long, Reserva> porId = new HashMap<>();
        for (Reserva r : reservas.findAll()) porId.put(r.id(), r);
        return new ReporteCaja(filtrados, porId, porMetodo, escala(confirmado), escala(pendiente), escala(rechazado),
                devs, devuelto, escala(confirmado.subtract(devuelto)));
    }

    private static boolean enPeriodo(LocalDateTime fecha, LocalDate desde, LocalDate hasta) {
        if (fecha == null) return false;
        LocalDate dia = fecha.toLocalDate();
        return (desde == null || !dia.isBefore(desde)) && (hasta == null || !dia.isAfter(hasta));
    }

    private static BigDecimal sumar(List<BigDecimal> montos) {
        return escala(montos.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static BigDecimal escala(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }
}
