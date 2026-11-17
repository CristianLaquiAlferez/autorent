package pe.edu.upt.autorent.modelo.entidad;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;
import pe.edu.upt.autorent.modelo.EstadoAuto;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/** Entidad JPA de la tabla de autos. Contiene atributos, getters y setters para MySQL. */

@Entity
@Table(name = "autos", uniqueConstraints = @UniqueConstraint(name = "uk_auto_placa", columnNames = "placa"))
public class AutoEntity {
    /** Constructor sin argumentos requerido por JPA. */
    public AutoEntity() { }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 15) private String placa;
    @Column(nullable = false, length = 60) private String marca;
    @Column(nullable = false, length = 60) private String modelo;
    @Column(nullable = false) private int anio;
    @Column(nullable = false, length = 40) private String categoria;
    @Column(length = 40) private String color;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal tarifaDiaria;
    @Column(length = 500) private String descripcion;
    @Column(name = "imagen_url", length = 1000) private String imagenUrl;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20) private EstadoAuto estado;
    @Column(nullable = false) private LocalDateTime creadoEn;

    @PrePersist void prePersist() { if (creadoEn == null) creadoEn = LocalDateTime.now(); }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPlaca() { return placa; }
    public void setPlaca(String placa) { this.placa = placa; }
    public String getMarca() { return marca; }
    public void setMarca(String marca) { this.marca = marca; }
    public String getModelo() { return modelo; }
    public void setModelo(String modelo) { this.modelo = modelo; }
    public int getAnio() { return anio; }
    public void setAnio(int anio) { this.anio = anio; }
    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public BigDecimal getTarifaDiaria() { return tarifaDiaria; }
    public void setTarifaDiaria(BigDecimal tarifaDiaria) { this.tarifaDiaria = tarifaDiaria; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getImagenUrl() { return imagenUrl; }
    public void setImagenUrl(String imagenUrl) { this.imagenUrl = imagenUrl; }
    public EstadoAuto getEstado() { return estado; }
    public void setEstado(EstadoAuto estado) { this.estado = estado; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }
}
