package pe.edu.upt.autorent.servicio;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upt.autorent.modelo.*;
import pe.edu.upt.autorent.servicio.contrato.ReporteUseCase;
import pe.edu.upt.autorent.repositorio.DevolucionRepositoryPort;
import pe.edu.upt.autorent.repositorio.PagoRepositoryPort;
import pe.edu.upt.autorent.repositorio.ReservaRepositoryPort;
import java.math.BigDecimal;
import java.util.List;
/** Servicio de reportes: calcula los indicadores del negocio para la pantalla y el PDF. */

@Service
@Transactional(readOnly = true)
public class ReporteService implements ReporteUseCase {
    private final ReservaRepositoryPort reservas;
    private final PagoRepositoryPort pagos;
    private final DevolucionRepositoryPort devoluciones;
    public ReporteService(ReservaRepositoryPort reservas, PagoRepositoryPort pagos, DevolucionRepositoryPort devoluciones) {
        this.reservas = reservas; this.pagos = pagos; this.devoluciones = devoluciones;
    }

    /**
     * Cuenta reservas por estado y calcula: ingresos (pagos CONFIRMADOS), devoluciones completadas
     * e ingreso neto = ingresos - devoluciones. Devuelve todo en un ResumenReporte.
     */
    @Override public ResumenReporte generarResumen() {
        List<Reserva> rs = reservas.findAll();
        List<Pago> ps = pagos.findAll();
        List<Devolucion> ds = devoluciones.findAll();
        long pendientes = 0, confirmadas = 0, enCurso = 0, devolucionesPendientes = 0;
        BigDecimal ingresos = BigDecimal.ZERO;
        BigDecimal reembolsos = BigDecimal.ZERO;
        for (Reserva r : rs) {
            switch (r.estado()) {
                case PENDIENTE_PAGO, PENDIENTE_VALIDACION -> pendientes++;
                case CONFIRMADA -> confirmadas++;
                case EN_CURSO -> enCurso++;
                default -> { }
            }
        }
        for (Pago pago : ps) {
            if (pago.estado() == EstadoPago.CONFIRMADO) ingresos = ingresos.add(pago.monto());
        }
        for (Devolucion d : ds) {
            if (d.estado() == EstadoDevolucion.PENDIENTE) devolucionesPendientes++;
            if (d.estado() == EstadoDevolucion.COMPLETADA) reembolsos = reembolsos.add(d.monto());
        }
        return new ResumenReporte(rs.size(), pendientes, confirmadas, enCurso,
                ps.size(), devolucionesPendientes, ingresos, reembolsos,
                ingresos.subtract(reembolsos), rs, ps, ds);
    }
}
