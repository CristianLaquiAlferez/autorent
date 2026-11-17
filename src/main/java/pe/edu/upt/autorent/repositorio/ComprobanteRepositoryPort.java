package pe.edu.upt.autorent.repositorio;

import java.util.List;
import java.util.Optional;
import pe.edu.upt.autorent.modelo.Comprobante;
import pe.edu.upt.autorent.modelo.TipoComprobante;

/** Puerto de salida: guardar y consultar comprobantes electrónicos. */
public interface ComprobanteRepositoryPort {
    /** Guarda un registro nuevo o actualizado. */
    Comprobante save(Comprobante comprobante);
    /** Busca un comprobante por su identificador. */
    Optional<Comprobante> findById(Long id);
    /** Comprobante de un tipo emitido para un pago (cada pago tiene como máximo una boleta y una nota de crédito). */
    Optional<Comprobante> findByPagoIdAndTipo(Long pagoId, TipoComprobante tipo);
    /** Todos los comprobantes, del más reciente al más antiguo. */
    List<Comprobante> findAll();
    /** Comprobantes de un cliente. */
    List<Comprobante> findByClienteId(Long clienteId);
    /** Último número usado en una serie (0 si la serie aún no tiene comprobantes). */
    /** Comprobantes de una reserva (boletas y notas de crédito), del más antiguo al más reciente. */
    List<Comprobante> findByReservaId(Long reservaId);
    int ultimoNumero(TipoComprobante tipo, String serie);
}
