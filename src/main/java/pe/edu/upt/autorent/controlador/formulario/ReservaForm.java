package pe.edu.upt.autorent.controlador.formulario;

import pe.edu.upt.autorent.modelo.MetodoPago;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
/** Datos que recibe el formulario de reserva. */

public class ReservaForm {
    private Long autoId;
    private Long clienteId;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaInicio;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaFin;
    private BigDecimal montoAnticipo;
    private MetodoPago metodoPago;
    private String telefonoYape;
    private String codigoOperacion;
    private String telefonoDevolucion;
    public Long getAutoId() { return autoId; } public void setAutoId(Long autoId) { this.autoId = autoId; }
    public Long getClienteId() { return clienteId; } public void setClienteId(Long clienteId) { this.clienteId = clienteId; }
    public LocalDate getFechaInicio() { return fechaInicio; } public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }
    public LocalDate getFechaFin() { return fechaFin; } public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }
    public BigDecimal getMontoAnticipo() { return montoAnticipo; } public void setMontoAnticipo(BigDecimal montoAnticipo) { this.montoAnticipo = montoAnticipo; }
    public MetodoPago getMetodoPago() { return metodoPago; } public void setMetodoPago(MetodoPago metodoPago) { this.metodoPago = metodoPago; }
    public String getTelefonoYape() { return telefonoYape; } public void setTelefonoYape(String telefonoYape) { this.telefonoYape = telefonoYape; }
    public String getCodigoOperacion() { return codigoOperacion; } public void setCodigoOperacion(String codigoOperacion) { this.codigoOperacion = codigoOperacion; }
    public String getTelefonoDevolucion() { return telefonoDevolucion; } public void setTelefonoDevolucion(String telefonoDevolucion) { this.telefonoDevolucion = telefonoDevolucion; }
}
