package pe.edu.upt.autorent.dao.archivos;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import pe.edu.upt.autorent.modelo.ImagenAuto;
/** Gestiona los archivos de imagen en una carpeta local de forma controlada. */

public final class ArchivoImagenes {
    private final Path directorio;

    public ArchivoImagenes(Path directorio) {
        this.directorio = directorio.toAbsolutePath().normalize();
    }

    public void guardar(Long autoId, ImagenAuto imagen) {
        String extension = extension(imagen.tipoContenido());
        try {
            Files.createDirectories(directorio);
            Path temporal = Files.createTempFile(directorio, "foto-", ".tmp");
            try {
                Files.write(temporal, imagen.datos());
                Path destino = ruta(autoId, extension);
                try {
                    Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException | FileAlreadyExistsException ex) {
                    Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
                }
                eliminarOtrasExtensiones(autoId, extension);
            } finally {
                Files.deleteIfExists(temporal);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo guardar la foto. Verifique el espacio y los permisos de la carpeta de imágenes.", ex);
        }
    }

    public Optional<ImagenAuto> buscar(Long autoId) {
        for (String extension : new String[]{"jpg", "png", "webp"}) {
            Path archivo = ruta(autoId, extension);
            if (Files.isRegularFile(archivo)) {
                try {
                    return Optional.of(new ImagenAuto(Files.readAllBytes(archivo), tipoContenido(extension)));
                } catch (IOException ex) {
                    throw new IllegalStateException("No se pudo abrir la foto del vehículo.", ex);
                }
            }
        }
        return Optional.empty();
    }

    public void eliminar(Long autoId) {
        try {
            eliminarOtrasExtensiones(autoId, null);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo quitar la foto del vehículo.", ex);
        }
    }

    private void eliminarOtrasExtensiones(Long autoId, String conservar) throws IOException {
        for (String extension : new String[]{"jpg", "png", "webp"}) {
            if (!extension.equals(conservar)) Files.deleteIfExists(ruta(autoId, extension));
        }
    }

    private Path ruta(Long autoId, String extension) {
        if (autoId == null || autoId <= 0) throw new IllegalArgumentException("Vehículo no válido.");
        return directorio.resolve(autoId + "." + extension);
    }

    private static String extension(String tipo) {
        return switch (tipo) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new IllegalArgumentException("Seleccione una foto JPG, PNG o WEBP válida.");
        };
    }

    private static String tipoContenido(String extension) {
        return extension.equals("jpg") ? "image/jpeg" : "image/" + extension;
    }
}
