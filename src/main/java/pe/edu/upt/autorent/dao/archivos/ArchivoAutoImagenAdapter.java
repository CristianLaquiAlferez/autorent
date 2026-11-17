package pe.edu.upt.autorent.dao.archivos;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Primary;
import pe.edu.upt.autorent.modelo.ImagenAuto;
import pe.edu.upt.autorent.repositorio.AutoImagenRepositoryPort;
/** Adaptador DAO de fotos: guarda archivos y permite leer imágenes anteriores. */

@Repository
@Primary
public class ArchivoAutoImagenAdapter implements AutoImagenRepositoryPort {
    private final ArchivoImagenes archivos;
    private final JdbcTemplate jdbc;

    public ArchivoAutoImagenAdapter(@Value("${autorent.imagenes.directorio:}") String directorio,
                                    JdbcTemplate jdbc) {
        Path ruta = directorio.isBlank()
                ? Path.of(System.getProperty("user.home"), "AutoRent", "imagenes")
                : Path.of(directorio);
        this.archivos = new ArchivoImagenes(ruta);
        this.jdbc = jdbc;
    }

    /** Escribe la foto en el almacenamiento local. */
    @Override public void guardar(Long autoId, ImagenAuto imagen) {
        archivos.guardar(autoId, imagen);
    }

    /** Busca primero en disco y, si existe, permite leer una imagen antigua de MySQL. */
    @Override public Optional<ImagenAuto> buscar(Long autoId) {
        Optional<ImagenAuto> local = archivos.buscar(autoId);
        if (local.isPresent()) return local;
        try {
            List<ImagenAuto> anteriores = jdbc.query(
                    "select datos, tipo_contenido from auto_imagenes where auto_id = ?",
                    (rs, fila) -> new ImagenAuto(rs.getBytes("datos"), rs.getString("tipo_contenido")),
                    autoId);
            return anteriores.stream().findFirst();
        } catch (DataAccessException ex) {
            return Optional.empty();
        }
    }

    /** Elimina la foto local asociada al vehículo. */
    @Override public void eliminar(Long autoId) {
        archivos.eliminar(autoId);
    }
}
