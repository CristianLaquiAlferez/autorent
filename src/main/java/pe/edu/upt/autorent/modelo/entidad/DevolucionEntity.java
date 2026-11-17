package pe.edu.upt.autorent.modelo.entidad;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;
import pe.edu.upt.autorent.modelo.EstadoDevolucion;
import pe.edu.upt.autorent.modelo.MetodoPago;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/** Entidad JPA de la tabla de devoluciones. Contiene atributos, getters y setters para MySQL. */

@Entity
@Table(name = "devoluciones")
public class DevolucionEntity {
    /** Constructor sin argumentos requerido por JPA. */
    public DevolucionEntity() { }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "reserva_id", nullable = false)
    private ReservaEntity reserva;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "pago_id")
    private PagoEntity pago;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal monto;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20) private MetodoPago metodo;
    @Column(nullable = false, length = 20) private String telefonoDestino;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20) private EstadoDevolucion estado;
    @Column(nullable = false) private LocalDateTime registradoEn;
    private LocalDateTime completadoEn;
    @Column(length = 60) private String completadoPor;
    /** Evidencia de la devolución: código del Yape enviado o del reembolso en la pasarela. */
    @Column(length = 60) private String codigoOperacion;

    @PrePersist void prePersist() { if (registradoEn == null) registradoEn = LocalDateTime.now(); }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ReservaEntity getReserva() { return reserva; }
    public void setReserva(ReservaEntity reserva) { this.reserva = reserva; }
    public PagoEntity getPago() { return pago; }
    public void setPago(PagoEntity pago) { this.pago = pago; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
    public MetodoPago getMetodo() { return metodo; }
    public void setMetodo(MetodoPago metodo) { this.metodo = metodo; }
    public String getTelefonoDestino() { return telefonoDestino; }
    public void setTelefonoDestino(String telefonoDestino) { this.telefonoDestino = telefonoDestino; }
    public EstadoDevolucion getEstado() { return estado; }
    public void setEstado(EstadoDevolucion estado) { this.estado = estado; }
    public LocalDateTime getRegistradoEn() { return registradoEn; }
    public void setRegistradoEn(LocalDateTime registradoEn) { this.registradoEn = registradoEn; }
    public LocalDateTime getCompletadoEn() { return completadoEn; }
    public void setCompletadoEn(LocalDateTime completadoEn) { this.completadoEn = completadoEn; }
    public String getCodigoOperacion() { return codigoOperacion; }
    public void setCodigoOperacion(String codigoOperacion) { this.codigoOperacion = codigoOperacion; }
    public String getCompletadoPor() { return completadoPor; }
    public void setCompletadoPor(String completadoPor) { this.completadoPor = completadoPor; }
}
