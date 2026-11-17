package pe.edu.upt.autorent.servicio.contrato;

import pe.edu.upt.autorent.modelo.ResumenReporte;
/** Puerto de entrada: consulta de indicadores e historiales. */

public interface ReporteUseCase {
    /** Caso de uso: generar resumen. */
    ResumenReporte generarResumen();
}
