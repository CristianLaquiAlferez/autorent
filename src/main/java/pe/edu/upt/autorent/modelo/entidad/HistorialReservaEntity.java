package pe.edu.upt.autorent.modelo.entidad;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pe.edu.upt.autorent.modelo.AccionReserva;
import pe.edu.upt.autorent.modelo.EstadoReserva;
import java.time.LocalDateTime;

/** Entidad JPA de la tabla historial_reservas (bitácora de auditoría de cada reserva). */
@Entity
@Table(name = "historial_reservas", indexes = @Index(name = "idx_historial_reserva", columnList = "reserva_id,fecha"))
public class HistorialReservaEntity {
    /** Constructor sin argumentos requerido por JPA. */
    public HistorialReservaEntity() { }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "reserva_id", nullable = false)
    private ReservaEntity reserva;
    @Column(nullable = false) private LocalDateTime fecha;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 30) private AccionReserva accion;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(length = 30) private EstadoReserva estadoAnterior;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(length = 30) private EstadoReserva estadoNuevo;
    @Column(nullable = false, length = 60) private String usuario;
    @Column(length = 300) private String detalle;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ReservaEntity getReserva() { return reserva; }
    public void setReserva(ReservaEntity reserva) { this.reserva = reserva; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
    public AccionReserva getAccion() { return accion; }
    public void setAccion(AccionReserva accion) { this.accion = accion; }
    public EstadoReserva getEstadoAnterior() { return estadoAnterior; }
    public void setEstadoAnterior(EstadoReserva estadoAnterior) { this.estadoAnterior = estadoAnterior; }
    public EstadoReserva getEstadoNuevo() { return estadoNuevo; }
    public void setEstadoNuevo(EstadoReserva estadoNuevo) { this.estadoNuevo = estadoNuevo; }
    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }
    public String getDetalle() { return detalle; }
    public void setDetalle(String detalle) { this.detalle = detalle; }
}
