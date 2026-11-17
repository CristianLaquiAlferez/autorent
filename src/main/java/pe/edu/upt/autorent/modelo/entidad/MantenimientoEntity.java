package pe.edu.upt.autorent.modelo.entidad;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;
import pe.edu.upt.autorent.modelo.EstadoMantenimiento;
import java.time.LocalDate;
import java.time.LocalDateTime;
/** Entidad JPA de la tabla de mantenimientos. Contiene atributos, getters y setters para MySQL. */

@Entity
@Table(name = "mantenimientos")
public class MantenimientoEntity {
    /** Constructor sin argumentos requerido por JPA. */
    public MantenimientoEntity() { }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "auto_id", nullable = false)
    private AutoEntity auto;
    @Column(nullable = false, length = 500) private String descripcion;
    @Column(nullable = false) private LocalDate fechaInicio;
    private LocalDate fechaFin;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20) private EstadoMantenimiento estado;
    @Column(nullable = false) private LocalDateTime registradoEn;

    @PrePersist void prePersist() { if (registradoEn == null) registradoEn = LocalDateTime.now(); }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public AutoEntity getAuto() { return auto; }
    public void setAuto(AutoEntity auto) { this.auto = auto; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }
    public LocalDate getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }
    public EstadoMantenimiento getEstado() { return estado; }
    public void setEstado(EstadoMantenimiento estado) { this.estado = estado; }
    public LocalDateTime getRegistradoEn() { return registradoEn; }
    public void setRegistradoEn(LocalDateTime registradoEn) { this.registradoEn = registradoEn; }
}
