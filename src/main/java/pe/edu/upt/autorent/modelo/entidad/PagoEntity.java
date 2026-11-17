package pe.edu.upt.autorent.modelo.entidad;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;
import pe.edu.upt.autorent.modelo.EstadoPago;
import pe.edu.upt.autorent.modelo.MetodoPago;
import pe.edu.upt.autorent.modelo.TipoPago;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/** Entidad JPA de la tabla de pagos. Contiene atributos, getters y setters para MySQL. */

@Entity
@Table(name = "pagos", indexes = @Index(name = "idx_pago_reserva_estado", columnList = "reserva_id,estado"))
public class PagoEntity {
    /** Constructor sin argumentos requerido por JPA. */
    public PagoEntity() { }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "reserva_id", nullable = false)
    private ReservaEntity reserva;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal monto;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20) private MetodoPago metodo;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20) private TipoPago tipo;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20) private EstadoPago estado;
    @Column(length = 20) private String telefonoOrigen;
    @Column(length = 40, unique = true) private String codigoOperacion;
    @Column(nullable = false) private LocalDateTime registradoEn;
    /** Fecha en que el pago se confirmó o rechazó (se conserva el nombre de columna de la versión anterior). */
    @Column(name = "confirmado_en") private LocalDateTime procesadoEn;
    /** Quién registró el pago (cliente o trabajador). */
    @Column(length = 60) private String registradoPor;
    /** Quién confirmó o rechazó el pago (trabajador, cliente vía pasarela o SISTEMA). */
    @Column(length = 60) private String procesadoPor;
    /** Motivo del rechazo o comentario. */
    @Column(length = 200) private String observacion;
    /** Datos visibles del pago en línea, por ejemplo "VISA **** 1111". Nunca el número completo. */
    @Column(length = 120) private String detalle;

    @PrePersist void prePersist() { if (registradoEn == null) registradoEn = LocalDateTime.now(); }
    public String getDetalle() { return detalle; }
    public void setDetalle(String detalle) { this.detalle = detalle; }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ReservaEntity getReserva() { return reserva; }
    public void setReserva(ReservaEntity reserva) { this.reserva = reserva; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
    public MetodoPago getMetodo() { return metodo; }
    public void setMetodo(MetodoPago metodo) { this.metodo = metodo; }
    public TipoPago getTipo() { return tipo; }
    public void setTipo(TipoPago tipo) { this.tipo = tipo; }
    public EstadoPago getEstado() { return estado; }
    public void setEstado(EstadoPago estado) { this.estado = estado; }
    public String getTelefonoOrigen() { return telefonoOrigen; }
    public void setTelefonoOrigen(String telefonoOrigen) { this.telefonoOrigen = telefonoOrigen; }
    public String getCodigoOperacion() { return codigoOperacion; }
    public void setCodigoOperacion(String codigoOperacion) { this.codigoOperacion = codigoOperacion; }
    public LocalDateTime getRegistradoEn() { return registradoEn; }
    public void setRegistradoEn(LocalDateTime registradoEn) { this.registradoEn = registradoEn; }
    public LocalDateTime getProcesadoEn() { return procesadoEn; }
    public void setProcesadoEn(LocalDateTime procesadoEn) { this.procesadoEn = procesadoEn; }
    public String getProcesadoPor() { return procesadoPor; }
    public void setProcesadoPor(String procesadoPor) { this.procesadoPor = procesadoPor; }
    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }
    public String getRegistradoPor() { return registradoPor; }
    public void setRegistradoPor(String registradoPor) { this.registradoPor = registradoPor; }
}
