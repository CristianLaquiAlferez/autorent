package pe.edu.upt.autorent.modelo;

import java.math.BigDecimal;
import java.util.List;
/** Agrupa los indicadores y movimientos que se muestran en reportes. */

public record ResumenReporte(long totalReservas, long reservasPendientes, long reservasConfirmadas,
                             long alquileresEnCurso, long totalPagos, long devolucionesPendientes,
                             BigDecimal ingresosConfirmados, BigDecimal devolucionesCompletadas,
                             BigDecimal ingresosNetos, List<Reserva> reservas,
                             List<Pago> pagos, List<Devolucion> devoluciones) {
}
