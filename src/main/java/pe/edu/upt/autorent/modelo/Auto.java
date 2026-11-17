package pe.edu.upt.autorent.modelo;

import java.math.BigDecimal;
/** Modelo de negocio de un vehículo. Es inmutable para evitar cambios fuera del servicio. */

public record Auto(Long id, String placa, String marca, String modelo, int anio,
                   String categoria, String color, BigDecimal tarifaDiaria,
                   String descripcion, EstadoAuto estado, String imagenUrl) {

    public Auto(Long id, String placa, String marca, String modelo, int anio,
                String categoria, String color, BigDecimal tarifaDiaria,
                String descripcion, EstadoAuto estado) {
        this(id, placa, marca, modelo, anio, categoria, color, tarifaDiaria, descripcion, estado, null);
    }
}
