package pe.edu.upt.autorent.controlador.formulario;

import java.math.BigDecimal;
/** Datos que recibe el formulario de creacion y edicion de autos. */

public class AutoForm {
    private Long id;
    private String placa;
    private String marca;
    private String modelo;
    private int anio;
    private String categoria;
    private String color;
    private BigDecimal tarifaDiaria;
    private String descripcion;
    private String imagenUrl;
    private boolean eliminarImagen;
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public String getPlaca() { return placa; } public void setPlaca(String placa) { this.placa = placa; }
    public String getMarca() { return marca; } public void setMarca(String marca) { this.marca = marca; }
    public String getModelo() { return modelo; } public void setModelo(String modelo) { this.modelo = modelo; }
    public int getAnio() { return anio; } public void setAnio(int anio) { this.anio = anio; }
    public String getCategoria() { return categoria; } public void setCategoria(String categoria) { this.categoria = categoria; }
    public String getColor() { return color; } public void setColor(String color) { this.color = color; }
    public BigDecimal getTarifaDiaria() { return tarifaDiaria; } public void setTarifaDiaria(BigDecimal tarifaDiaria) { this.tarifaDiaria = tarifaDiaria; }
    public String getImagenUrl() { return imagenUrl; } public void setImagenUrl(String imagenUrl) { this.imagenUrl = imagenUrl; }
    public boolean isEliminarImagen() { return eliminarImagen; } public void setEliminarImagen(boolean eliminarImagen) { this.eliminarImagen = eliminarImagen; }
    public String getDescripcion() { return descripcion; } public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
}
