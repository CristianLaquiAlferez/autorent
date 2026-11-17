package pe.edu.upt.autorent.servicio.contrato;

import pe.edu.upt.autorent.modelo.Auto;
import pe.edu.upt.autorent.modelo.EstadoAuto;
import pe.edu.upt.autorent.modelo.ImagenAuto;
import pe.edu.upt.autorent.modelo.Mantenimiento;
import java.time.LocalDate;
import java.util.List;
/** Puerto de entrada: operaciones que pueden solicitar los controladores sobre vehículos. */

public interface AutoUseCase {
    /** Caso de uso: listar todos. */
    List<Auto> listarTodos();
    /** Caso de uso: listar disponibles. */
    List<Auto> listarDisponibles();
    /** Caso de uso: obtener. */
    Auto obtener(Long id);
    /** Caso de uso: guardar. */
    Auto guardar(Auto auto);
    /** Caso de uso: guardar con imagen. */
    Auto guardarConImagen(Auto auto, byte[] archivo, String tipoContenido, boolean eliminarImagen);
    /** Caso de uso: obtener imagen. */
    ImagenAuto obtenerImagen(Long autoId);
    /** Caso de uso: cambiar estado. */
    void cambiarEstado(Long id, EstadoAuto estado);
    /** Caso de uso: listar mantenimientos. */
    List<Mantenimiento> listarMantenimientos();
    /** Caso de uso: iniciar mantenimiento. */
    void iniciarMantenimiento(Long autoId, String descripcion, LocalDate inicio, LocalDate fin);
    /** Caso de uso: finalizar mantenimiento. */
    void finalizarMantenimiento(Long mantenimientoId);
}
