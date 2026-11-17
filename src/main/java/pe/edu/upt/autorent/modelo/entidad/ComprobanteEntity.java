package pe.edu.upt.autorent.modelo.entidad;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pe.edu.upt.autorent.modelo.EstadoComprobante;
import pe.edu.upt.autorent.modelo.TipoComprobante;

/** Entidad JPA de la tabla comprobantes (boletas y notas de crédito electrónicas). */
@Entity
@Table(name = "comprobantes",
        uniqueConstraints = @UniqueConstraint(name = "uk_comprobante_serie_numero", columnNames = {"tipo", "serie", "numero"}),
        indexes = {@Index(name = "idx_comprobante_pago", columnList = "pago_id"),
                   @Index(name = "idx_comprobante_cliente", columnList = "cliente_id")})
public class ComprobanteEntity {
    /** Constructor sin argumentos requerido por JPA. */
    public ComprobanteEntity() { }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20) private TipoComprobante tipo;
    @Column(nullable = false, length = 4) private String serie;
    @Column(nullable = false) private int numero;
    @Column(nullable = false) private LocalDate fechaEmision;
    @Column(name = "pago_id", nullable = false) private Long pagoId;
    @Column(name = "reserva_id", nullable = false) private Long reservaId;
    @Column(name = "cliente_id", nullable = false) private Long clienteId;
    @Column(nullable = false, length = 15) private String clienteDocumento;
    @Column(nullable = false, length = 160) private String clienteNombre;
    @Column(length = 200) private String clienteDireccion;
    @Column(nullable = false, length = 250) private String descripcion;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal gravada;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal igv;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal total;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20) private EstadoComprobante estado;
    @Column(nullable = false, length = 30) private String proveedor;
    @Column(length = 500) private String enlacePdf;
    @Column(length = 100) private String hash;
    @Column(length = 500) private String cadenaQr;
    @Column(length = 500) private String mensaje;
    @Column(name = "referencia_id") private Long referenciaId;
    @Column(nullable = false) private LocalDateTime creadoEn;

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public TipoComprobante getTipo() { return tipo; } public void setTipo(TipoComprobante tipo) { this.tipo = tipo; }
    public String getSerie() { return serie; } public void setSerie(String serie) { this.serie = serie; }
    public int getNumero() { return numero; } public void setNumero(int numero) { this.numero = numero; }
    public LocalDate getFechaEmision() { return fechaEmision; } public void setFechaEmision(LocalDate f) { this.fechaEmision = f; }
    public Long getPagoId() { return pagoId; } public void setPagoId(Long pagoId) { this.pagoId = pagoId; }
    public Long getReservaId() { return reservaId; } public void setReservaId(Long reservaId) { this.reservaId = reservaId; }
    public Long getClienteId() { return clienteId; } public void setClienteId(Long clienteId) { this.clienteId = clienteId; }
    public String getClienteDocumento() { return clienteDocumento; } public void setClienteDocumento(String v) { this.clienteDocumento = v; }
    public String getClienteNombre() { return clienteNombre; } public void setClienteNombre(String v) { this.clienteNombre = v; }
    public String getClienteDireccion() { return clienteDireccion; } public void setClienteDireccion(String v) { this.clienteDireccion = v; }
    public String getDescripcion() { return descripcion; } public void setDescripcion(String v) { this.descripcion = v; }
    public BigDecimal getGravada() { return gravada; } public void setGravada(BigDecimal v) { this.gravada = v; }
    public BigDecimal getIgv() { return igv; } public void setIgv(BigDecimal v) { this.igv = v; }
    public BigDecimal getTotal() { return total; } public void setTotal(BigDecimal v) { this.total = v; }
    public EstadoComprobante getEstado() { return estado; } public void setEstado(EstadoComprobante v) { this.estado = v; }
    public String getProveedor() { return proveedor; } public void setProveedor(String v) { this.proveedor = v; }
    public String getEnlacePdf() { return enlacePdf; } public void setEnlacePdf(String v) { this.enlacePdf = v; }
    public String getHash() { return hash; } public void setHash(String v) { this.hash = v; }
    public String getCadenaQr() { return cadenaQr; } public void setCadenaQr(String v) { this.cadenaQr = v; }
    public String getMensaje() { return mensaje; } public void setMensaje(String v) { this.mensaje = v; }
    public Long getReferenciaId() { return referenciaId; } public void setReferenciaId(Long v) { this.referenciaId = v; }
    public LocalDateTime getCreadoEn() { return creadoEn; } public void setCreadoEn(LocalDateTime v) { this.creadoEn = v; }
}
