package pe.edu.upt.autorent;

import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.edu.upt.autorent.modelo.ImagenAuto;
import pe.edu.upt.autorent.dao.archivos.ArchivoImagenes;
import static org.junit.jupiter.api.Assertions.*;

class ArchivoImagenesTest {
    @TempDir Path directorio;

    @Test void guardaYRecuperaImagenDespuesDeReiniciar() {
        byte[] imagen = {(byte) 255, (byte) 216, (byte) 255, 1, 2, 3};
        ArchivoImagenes almacenamiento = new ArchivoImagenes(directorio);
        almacenamiento.guardar(50L, new ImagenAuto(imagen, "image/jpeg"));
        ImagenAuto recuperada = new ArchivoImagenes(directorio).buscar(50L).orElseThrow();
        assertTrue(Arrays.equals(imagen, recuperada.datos()));
        assertEquals("image/jpeg", recuperada.tipoContenido());
    }

    @Test void reemplazaYEliminaImagen() {
        ArchivoImagenes almacenamiento = new ArchivoImagenes(directorio);
        almacenamiento.guardar(51L, new ImagenAuto(new byte[]{1, 2}, "image/png"));
        almacenamiento.guardar(51L, new ImagenAuto(new byte[]{3, 4}, "image/webp"));
        assertEquals("image/webp", almacenamiento.buscar(51L).orElseThrow().tipoContenido());
        almacenamiento.eliminar(51L);
        assertTrue(almacenamiento.buscar(51L).isEmpty());
    }
}
