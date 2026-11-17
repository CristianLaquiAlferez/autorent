package pe.edu.upt.autorent.modelo.entidad;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;
import pe.edu.upt.autorent.modelo.EstadoReserva;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
/** Entidad JPA de la tabla de reservas. Contiene atributos, getters y setters para MySQL. */

@Entity
@Table(name = "reservas", indexes = @Index(name = "idx_reserva_auto_estado", columnList = "auto_id,estado"))
public class ReservaEntity {
    /** Constructor sin argumentos requerido por JPA. */
    public ReservaEntity() { }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "auto_id", nullable = false)
    private AutoEntity auto;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cliente_id", nullable = false)
    private UsuarioEntity cliente;
    @Column(nullable = false) private LocalDate fechaInicio;
    @Column(nullable = false) private LocalDate fechaFin;
    @Column(nullable = false) private int dias;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal tarifaDiaria;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal total;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 30) private EstadoReserva estado;
    @Column(length = 20) private String telefonoDevolucion;
    @Column(length = 500) private String observacion;
    @Column(nullable = false) private LocalDateTime creadoEn;

    @PrePersist void prePersist() { if (creadoEn == null) creadoEn = LocalDateTime.now(); }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public AutoEntity getAuto() { return auto; }
    public void setAuto(AutoEntity auto) { this.auto = auto; }
    public UsuarioEntity getCliente() { return cliente; }
    public void setCliente(UsuarioEntity cliente) { this.cliente = cliente; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }
    public LocalDate getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }
    public int getDias() { return dias; }
    public void setDias(int dias) { this.dias = dias; }
    public BigDecimal getTarifaDiaria() { return tarifaDiaria; }
    public void setTarifaDiaria(BigDecimal tarifaDiaria) { this.tarifaDiaria = tarifaDiaria; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public EstadoReserva getEstado() { return estado; }
    public void setEstado(EstadoReserva estado) { this.estado = estado; }
    public String getTelefonoDevolucion() { return telefonoDevolucion; }
    public void setTelefonoDevolucion(String telefonoDevolucion) { this.telefonoDevolucion = telefonoDevolucion; }
    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }
}
