package pe.edu.upt.autorent.repositorio;

import java.time.LocalDateTime;

import pe.edu.upt.autorent.modelo.Pago;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
/** Puerto de salida: contrato para registrar y consultar pagos. */

public interface PagoRepositoryPort {
    /** Guarda un registro nuevo o actualizado. */
    Pago save(Pago pago);
    /** Busca un registro por su identificador. */
    Optional<Pago> findById(Long id);
    /** Contrato de acceso a datos para find by reserva id. */
    List<Pago> findByReservaId(Long reservaId);
    /** Contrato de acceso a datos para find by cliente id. */
    List<Pago> findByClienteId(Long clienteId);
    /** Contrato de acceso a datos para total confirmado. */
    BigDecimal totalConfirmado(Long reservaId);
    /** Contrato de acceso a datos para existe pendiente. */
    boolean existePendiente(Long reservaId);
    /** Lista todos los registros del modulo. */
    List<Pago> findAll();
    /** Pagos que siguen pendientes desde antes de la fecha indicada (para liberar pagos abandonados). */
    List<Pago> findPendientesRegistradosAntesDe(LocalDateTime limite);
    /** Indica si ya se registró ese código de operación (Yape o pasarela). */
    boolean existsCodigoOperacion(String codigoOperacion);
}
