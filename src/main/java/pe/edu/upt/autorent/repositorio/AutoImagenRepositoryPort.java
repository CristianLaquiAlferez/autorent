package pe.edu.upt.autorent.repositorio;

import java.util.Optional;
import pe.edu.upt.autorent.modelo.ImagenAuto;
/** Puerto de salida: contrato para guardar, buscar y eliminar fotos. */

public interface AutoImagenRepositoryPort {
    /** Almacena los datos recibidos por el servicio. */
    void guardar(Long autoId, ImagenAuto imagen);
    /** Busca un registro en el almacenamiento. */
    Optional<ImagenAuto> buscar(Long autoId);
    /** Elimina un registro del almacenamiento. */
    void eliminar(Long autoId);
}
