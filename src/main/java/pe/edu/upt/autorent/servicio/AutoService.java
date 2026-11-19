package pe.edu.upt.autorent.servicio;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upt.autorent.modelo.*;
import pe.edu.upt.autorent.servicio.contrato.AutoUseCase;
import pe.edu.upt.autorent.repositorio.AutoRepositoryPort;
import pe.edu.upt.autorent.repositorio.AutoImagenRepositoryPort;
import pe.edu.upt.autorent.repositorio.MantenimientoRepositoryPort;
import pe.edu.upt.autorent.repositorio.ReservaRepositoryPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.net.URI;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/**
 * Servicio de negocio de vehículos: registro y edición, fotos, cambio de estado y mantenimiento.
 * Implementa el puerto de entrada AutoUseCase y usa solo puertos de salida (repositorio).
 */

@Service
@Transactional
public class AutoService implements AutoUseCase {
    private final AutoRepositoryPort autos;
    private final MantenimientoRepositoryPort mantenimientos;
    private final ReservaRepositoryPort reservas;
    private final AutoImagenRepositoryPort imagenes;
    private final Clock reloj;

    public AutoService(AutoRepositoryPort autos, MantenimientoRepositoryPort mantenimientos,
                       ReservaRepositoryPort reservas, AutoImagenRepositoryPort imagenes, Clock reloj) {
        this.autos = autos; this.mantenimientos = mantenimientos; this.reservas = reservas; this.imagenes = imagenes;
        this.reloj = reloj;
    }

    // Consultas de solo lectura (readOnly = true): no modifican la base de datos.
    @Override @Transactional(readOnly = true) public List<Auto> listarTodos() { return autos.findAll(); }
    @Override @Transactional(readOnly = true) public List<Auto> listarDisponibles() { return autos.findByEstado(EstadoAuto.DISPONIBLE); }

    /**
     * Autos que se pueden reservar: los disponibles y los que están en el taller (se reservan para
     * después del mantenimiento). Los inactivos no se ofrecen.
     */
    @Override @Transactional(readOnly = true) public List<Auto> listarReservables() {
        return autos.findAll().stream().filter(a -> a.estado() != EstadoAuto.INACTIVO).toList();
    }

    /**
     * Días en que el auto ya está comprometido, de hoy en adelante, ordenados por fecha:
     * reservas activas, alquiler en curso (aunque esté atrasado) y mantenimientos programados o en el taller.
     */
    @Override @Transactional(readOnly = true) public List<PeriodoOcupado> fechasOcupadas(Long autoId) {
        LocalDate hoy = LocalDate.now(reloj);
        List<PeriodoOcupado> periodos = new ArrayList<>();
        for (Reserva r : reservas.findByAutoId(autoId)) {
            if (!EstadoReserva.BLOQUEAN_DISPONIBILIDAD.contains(r.estado())) continue;
            if (r.estado() != EstadoReserva.EN_CURSO && r.fechaFin().isBefore(hoy)) continue;
            periodos.add(PeriodoOcupado.deReserva(r));
        }
        for (Mantenimiento m : mantenimientos.findByAutoId(autoId)) {
            if (m.estado().ocupaElAuto() && !m.ultimoDiaOcupado(hoy).isBefore(hoy)) periodos.add(PeriodoOcupado.deMantenimiento(m, hoy));
        }
        periodos.sort(Comparator.comparing(PeriodoOcupado::inicio));
        return periodos;
    }

    /** Ocupación de toda la flota, para la lista del administrador. */
    @Override @Transactional(readOnly = true) public Map<Long, List<PeriodoOcupado>> ocupacionDeLaFlota() {
        Map<Long, List<PeriodoOcupado>> ocupacion = new LinkedHashMap<>();
        for (Auto a : autos.findAll()) ocupacion.put(a.id(), fechasOcupadas(a.id()));
        return ocupacion;
    }

    /** ¿Algún mantenimiento activo del auto choca con esos días? (finExclusivo = día en que el auto quedaría libre). */
    private boolean chocaConMantenimiento(Long autoId, LocalDate inicio, LocalDate finExclusivo) {
        LocalDate hoy = LocalDate.now(reloj);
        return mantenimientos.findByAutoId(autoId).stream().anyMatch(m -> m.choca(inicio, finExclusivo, hoy));
    }
    @Override @Transactional(readOnly = true) public Auto obtener(Long id) {
        return autos.findById(id).orElseThrow(() -> new IllegalArgumentException("Vehículo no encontrado."));
    }

    /**
     * Valida y guarda un vehículo. Reglas: placa única de 5 a 15 caracteres, año entre 1990 y el
     * próximo año, tarifa mayor que cero con máximo 2 decimales. Un auto nuevo empieza DISPONIBLE;
     * al editar se conserva su estado actual.
     */
    @Override public Auto guardar(Auto auto) {
        String placa = auto.placa() == null ? "" : auto.placa().trim().toUpperCase();
        if (placa.isBlank() || auto.marca() == null || auto.marca().isBlank() || auto.modelo() == null || auto.modelo().isBlank())
            throw new IllegalArgumentException("Placa, marca y modelo son obligatorios.");
        ValidacionDatos.marcaModelo(auto.marca(), "La marca");
        ValidacionDatos.marcaModelo(auto.modelo(), "El modelo");
        ValidacionDatos.colorOpcional(auto.color());
        if (auto.categoria() == null || auto.categoria().isBlank())
            throw new IllegalArgumentException("Seleccione la categoría del vehículo.");
        if (auto.anio() < 1990 || auto.anio() > LocalDate.now(reloj).getYear() + 1)
            throw new IllegalArgumentException("El año del vehículo no es válido.");
        if (auto.tarifaDiaria() == null || auto.tarifaDiaria().compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("La tarifa diaria debe ser mayor que cero.");
        if (!placa.matches("[A-Z0-9-]{5,15}")) throw new IllegalArgumentException("La placa debe tener entre 5 y 15 letras, números o guiones.");
        if (auto.marca().trim().length() > 60 || auto.modelo().trim().length() > 60 ||
                (auto.categoria() != null && auto.categoria().trim().length() > 40) ||
                (auto.color() != null && auto.color().trim().length() > 40) ||
                (auto.descripcion() != null && auto.descripcion().trim().length() > 500))
            throw new IllegalArgumentException("Revise la longitud de los datos del vehículo.");
        if (auto.tarifaDiaria().stripTrailingZeros().scale() > 2 ||
                auto.tarifaDiaria().compareTo(new BigDecimal("99999999.99")) > 0)
            throw new IllegalArgumentException("La tarifa debe tener dos decimales como máximo y estar dentro del límite permitido.");
        if (auto.id() == null && autos.existsByPlaca(placa)) throw new IllegalArgumentException("Ya existe un vehículo con esa placa.");
        EstadoAuto estado = EstadoAuto.DISPONIBLE;
        if (auto.id() != null) {
            Auto anterior = obtener(auto.id());
            if (autos.existsByPlacaAndIdNot(placa, auto.id())) throw new IllegalArgumentException("La placa ya pertenece a otro vehículo.");
            estado = anterior.estado();
        }
        String imagenUrl = validarUrlImagen(auto.imagenUrl(), true);
        Auto limpio = new Auto(auto.id(), placa, auto.marca().trim(), auto.modelo().trim(), auto.anio(),
                auto.categoria() == null ? "General" : auto.categoria().trim(),
                auto.color() == null ? "" : auto.color().trim(), auto.tarifaDiaria(),
                auto.descripcion() == null ? "" : auto.descripcion().trim(), estado, imagenUrl);
        return autos.save(limpio);
    }

    /**
     * Guarda el vehículo y su foto. Hay cuatro casos:
     * 1) se subió un archivo, 2) se pidió quitar la foto, 3) se escribió una URL, 4) no se tocó la foto.
     * Las fotos subidas se guardan en una carpeta (ver ArchivoImagenes) y se sirven en /autos/{id}/imagen.
     */
    @Override public Auto guardarConImagen(Auto auto, byte[] archivo, String tipoContenido, boolean eliminarImagen) {
        if (auto == null) throw new IllegalArgumentException("Complete los datos del vehículo.");
        boolean hayArchivo = archivo != null && archivo.length > 0;
        if (hayArchivo) {
            String mime = detectarFormato(archivo);
            String ruta = auto.id() == null ? null : "/autos/" + auto.id() + "/imagen";
            Auto guardado = guardar(conImagen(auto, ruta));
            imagenes.guardar(guardado.id(), new ImagenAuto(Arrays.copyOf(archivo, archivo.length), mime));
            // Si el auto es nuevo, recién después de guardarlo se conoce su id para armar la ruta.
            if (ruta == null) guardado = autos.save(conImagen(guardado, "/autos/" + guardado.id() + "/imagen"));
            return guardado;
        }
        if (eliminarImagen) {
            Auto guardado = guardar(conImagen(auto, null));
            imagenes.eliminar(guardado.id());
            return guardado;
        }
        String url = validarUrlImagen(auto.imagenUrl(), false);
        if (url != null) {
            String anterior = auto.id() == null ? null : obtener(auto.id()).imagenUrl();
            Auto guardado = guardar(conImagen(auto, url));
            if (anterior != null && anterior.matches("/autos/[0-9]+/imagen")) imagenes.eliminar(guardado.id());
            return guardado;
        }
        String anterior = auto.id() == null ? null : obtener(auto.id()).imagenUrl();
        return guardar(conImagen(auto, anterior));
    }

    /** Obtiene la foto subida de un vehículo desde la carpeta de imágenes. */
    @Override @Transactional(readOnly = true) public ImagenAuto obtenerImagen(Long autoId) {
        Auto auto = obtener(autoId);
        if (!("/autos/" + autoId + "/imagen").equals(auto.imagenUrl()))
            throw new IllegalArgumentException("Imagen no encontrada.");
        return imagenes.buscar(autoId).orElseThrow(() -> new IllegalArgumentException("Imagen no encontrada."));
    }

    /** Copia del auto con otra URL de imagen (Auto es inmutable). */
    private static Auto conImagen(Auto a, String url) {
        return new Auto(a.id(), a.placa(), a.marca(), a.modelo(), a.anio(), a.categoria(),
                a.color(), a.tarifaDiaria(), a.descripcion(), a.estado(), url);
    }

    /** Acepta solo URLs http/https válidas (o la ruta interna /autos/{id}/imagen). */
    private static String validarUrlImagen(String entrada, boolean permitirInterna) {
        if (entrada == null || entrada.isBlank()) return null;
        String url = entrada.trim();
        if (url.length() > 1000) throw new IllegalArgumentException("La URL de la imagen es demasiado larga.");
        if (permitirInterna && url.matches("/autos/[0-9]+/imagen")) return url;
        try {
            URI uri = URI.create(url);
            String esquema = uri.getScheme();
            if (esquema == null || !(esquema.equalsIgnoreCase("https") || esquema.equalsIgnoreCase("http"))
                    || uri.getHost() == null || uri.getUserInfo() != null)
                throw new IllegalArgumentException("La imagen debe tener una URL http:// o https:// válida.");
            return url;
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("La imagen debe tener una URL http:// o https:// válida.");
        }
    }

    /**
     * Reconoce el formato por los primeros bytes del archivo ("firma"), no por la extensión:
     * así nadie puede subir otro tipo de archivo renombrado como .jpg. Máximo 5 MB.
     */
    private static String detectarFormato(byte[] datos) {
        if (datos.length > 5 * 1024 * 1024)
            throw new IllegalArgumentException("La foto no debe superar 5 MB.");
        if (datos.length >= 3 && (datos[0] & 255) == 255 && (datos[1] & 255) == 216 && (datos[2] & 255) == 255)
            return "image/jpeg";
        if (datos.length >= 8 && (datos[0] & 255) == 137 && datos[1] == 80 && datos[2] == 78
                && datos[3] == 71 && datos[4] == 13 && datos[5] == 10 && datos[6] == 26 && datos[7] == 10)
            return "image/png";
        if (datos.length >= 12 && datos[0] == 'R' && datos[1] == 'I' && datos[2] == 'F' && datos[3] == 'F'
                && datos[8] == 'W' && datos[9] == 'E' && datos[10] == 'B' && datos[11] == 'P')
            return "image/webp";
        throw new IllegalArgumentException("Seleccione una imagen JPG, PNG o WEBP válida (máximo 5 MB).");
    }

    /**
     * Cambia el estado del vehículo (DISPONIBLE / INACTIVO). No se permite desactivarlo si tiene
     * reservas activas, ni usar esta opción para mantenimiento (para eso hay un flujo propio).
     */
    @Override public void cambiarEstado(Long id, EstadoAuto estado) {
        Auto auto = autos.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("Vehículo no encontrado."));
        if (estado == null) throw new IllegalArgumentException("Seleccione un estado válido.");
        if (estado == EstadoAuto.MANTENIMIENTO) throw new IllegalArgumentException("Use la opción de registrar mantenimiento para enviar el vehículo al taller.");
        if (auto.estado() == EstadoAuto.MANTENIMIENTO) throw new IllegalArgumentException("Finalice el mantenimiento antes de cambiar el estado del vehículo.");
        boolean conReservaActiva = reservas.existsReservaActiva(id, EstadoReserva.BLOQUEAN_DISPONIBILIDAD);
        if (conReservaActiva && estado != EstadoAuto.DISPONIBLE)
            throw new IllegalArgumentException("No se puede desactivar un vehículo que tiene reservas activas o está alquilado.");
        autos.save(new Auto(auto.id(), auto.placa(), auto.marca(), auto.modelo(), auto.anio(), auto.categoria(),
                auto.color(), auto.tarifaDiaria(), auto.descripcion(), estado, auto.imagenUrl()));
    }

    @Override @Transactional(readOnly = true) public List<Mantenimiento> listarMantenimientos() { return mantenimientos.findAll(); }

    /**
     * Registra un mantenimiento con sus fechas.
     * - Si empieza hoy, el auto entra al taller (estado MANTENIMIENTO) y deja de entregarse hasta que se finalice.
     * - Si empieza después, queda PROGRAMADO: el auto se sigue alquilando fuera de esas fechas y la tarea
     *   automática lo pasa al taller el día de inicio (si en ese momento ya volvió a la sucursal).
     * No se permite si alguna reserva activa o alquiler en curso cae en esas fechas, ni si choca con otro mantenimiento.
     */
    @Override public void iniciarMantenimiento(Long autoId, String descripcion, LocalDate inicio, LocalDate fin) {
        if (autoId == null) throw new IllegalArgumentException("Seleccione el vehículo.");
        Auto auto = autos.findByIdForUpdate(autoId).orElseThrow(() -> new IllegalArgumentException("Vehículo no encontrado."));
        if (auto.estado() == EstadoAuto.INACTIVO) throw new IllegalArgumentException("El vehículo está inactivo. Actívelo antes de programar un mantenimiento.");
        ValidacionDatos.textoExplicativo(descripcion, "Describa el trabajo de mantenimiento (al menos 5 caracteres, por ejemplo: cambio de aceite).");
        LocalDate hoy = LocalDate.now(reloj);
        if (inicio == null || fin == null) throw new IllegalArgumentException("Indique la fecha de inicio y de fin del mantenimiento.");
        if (inicio.isBefore(hoy)) throw new IllegalArgumentException("El mantenimiento no puede empezar en una fecha pasada.");
        if (fin.isBefore(inicio)) throw new IllegalArgumentException("La fecha de fin del mantenimiento no puede ser anterior a la de inicio.");
        boolean empiezaHoy = inicio.isEqual(hoy);
        if (empiezaHoy && auto.estado() == EstadoAuto.MANTENIMIENTO)
            throw new IllegalArgumentException("El vehículo ya está en el taller. Finalice ese mantenimiento primero.");
        if (empiezaHoy && reservas.existsReservaActiva(autoId, List.of(EstadoReserva.EN_CURSO)))
            throw new IllegalArgumentException("No se puede enviar a mantenimiento: el vehículo está alquilado en este momento.");
        // El mantenimiento ocupa del inicio al fin, ambos incluidos: equivale al periodo [inicio, fin + 1).
        if (reservas.existsCruce(autoId, inicio, fin.plusDays(1), EstadoReserva.BLOQUEAN_DISPONIBILIDAD))
            throw new IllegalArgumentException("No se puede enviar a mantenimiento: hay reservas activas en esas fechas. Reprográmelas o elija otras fechas.");
        if (chocaConMantenimiento(autoId, inicio, fin.plusDays(1)))
            throw new IllegalArgumentException("Ya hay un mantenimiento de este vehículo en esas fechas.");
        mantenimientos.save(new Mantenimiento(null, autoId, descripcion.trim(), inicio, fin,
                empiezaHoy ? EstadoMantenimiento.EN_PROCESO : EstadoMantenimiento.PROGRAMADO, LocalDateTime.now(reloj)));
        if (empiezaHoy) cambiarEstadoInterno(auto, EstadoAuto.MANTENIMIENTO);
    }

    /**
     * Tarea automática: los mantenimientos programados cuyo día de inicio llegó pasan al taller.
     * Si el auto todavía no volvió de un alquiler, se espera a la siguiente revisión.
     */
    @Override public int activarMantenimientosProgramados() {
        LocalDate hoy = LocalDate.now(reloj);
        int activados = 0;
        for (Mantenimiento m : mantenimientos.findAll()) {
            if (m.estado() != EstadoMantenimiento.PROGRAMADO || m.fechaInicio().isAfter(hoy)) continue;
            if (reservas.existsReservaActiva(m.autoId(), List.of(EstadoReserva.EN_CURSO))) continue;
            Auto a = autos.findByIdForUpdate(m.autoId()).orElse(null);
            if (a == null || a.estado() == EstadoAuto.INACTIVO) continue;
            mantenimientos.save(new Mantenimiento(m.id(), m.autoId(), m.descripcion(), m.fechaInicio(), m.fechaFin(),
                    EstadoMantenimiento.EN_PROCESO, m.registradoEn()));
            cambiarEstadoInterno(a, EstadoAuto.MANTENIMIENTO);
            activados++;
        }
        return activados;
    }

    private void cambiarEstadoInterno(Auto a, EstadoAuto estado) {
        autos.save(new Auto(a.id(), a.placa(), a.marca(), a.modelo(), a.anio(), a.categoria(),
                a.color(), a.tarifaDiaria(), a.descripcion(), estado, a.imagenUrl()));
    }

    /**
     * Cierra el mantenimiento. Uno en el taller termina hoy y el vehículo vuelve a estar DISPONIBLE;
     * uno programado que todavía no empezó se cancela y libera sus fechas. Devuelve el mensaje para el usuario.
     */
    @Override public String finalizarMantenimiento(Long mantenimientoId) {
        Mantenimiento m = mantenimientos.findById(mantenimientoId)
                .orElseThrow(() -> new IllegalArgumentException("Mantenimiento no encontrado."));
        if (m.estado() == EstadoMantenimiento.PROGRAMADO) {
            mantenimientos.save(new Mantenimiento(m.id(), m.autoId(), m.descripcion() + " (cancelado antes de empezar)",
                    m.fechaInicio(), m.fechaFin(), EstadoMantenimiento.FINALIZADO, m.registradoEn()));
            return "Mantenimiento programado cancelado. Sus fechas quedan libres para reservas.";
        }
        if (m.estado() != EstadoMantenimiento.EN_PROCESO) throw new IllegalArgumentException("El mantenimiento ya fue finalizado.");
        mantenimientos.save(new Mantenimiento(m.id(), m.autoId(), m.descripcion(), m.fechaInicio(),
                LocalDate.now(reloj), EstadoMantenimiento.FINALIZADO, m.registradoEn()));
        Auto a = autos.findByIdForUpdate(m.autoId()).orElseThrow(() -> new IllegalArgumentException("Vehículo no encontrado."));
        // Si el auto ya fue desactivado mientras estaba en el taller, sigue inactivo.
        if (a.estado() == EstadoAuto.MANTENIMIENTO) cambiarEstadoInterno(a, EstadoAuto.DISPONIBLE);
        return "Mantenimiento finalizado. Vehículo disponible.";
    }
}
