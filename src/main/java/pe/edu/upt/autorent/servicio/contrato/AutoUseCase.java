package pe.edu.upt.autorent.servicio.contrato;

import pe.edu.upt.autorent.modelo.Auto;
import pe.edu.upt.autorent.modelo.EstadoAuto;
import pe.edu.upt.autorent.modelo.ImagenAuto;
import pe.edu.upt.autorent.modelo.Mantenimiento;
import pe.edu.upt.autorent.modelo.PeriodoOcupado;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
/** Puerto de entrada: operaciones que pueden solicitar los controladores sobre vehículos. */

public interface AutoUseCase {
    /** Caso de uso: listar todos. */
    List<Auto> listarTodos();
    /** Caso de uso: listar disponibles. */
    List<Auto> listarDisponibles();
    /** Autos que se pueden reservar (disponibles o en el taller, para fechas posteriores). */
    List<Auto> listarReservables();
    /** Días ya comprometidos del auto (reservas, alquiler en curso y mantenimientos), de hoy en adelante. */
    List<PeriodoOcupado> fechasOcupadas(Long autoId);
    /** Ocupación de cada auto de la flota (id del auto -> periodos). */
    Map<Long, List<PeriodoOcupado>> ocupacionDeLaFlota();
    /** Tarea automática: pasa al taller los mantenimientos programados cuyo inicio llegó. */
    int activarMantenimientosProgramados();
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
    String finalizarMantenimiento(Long mantenimientoId);
}
