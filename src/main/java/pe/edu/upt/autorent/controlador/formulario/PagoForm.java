package pe.edu.upt.autorent.controlador.formulario;

import pe.edu.upt.autorent.modelo.MetodoPago;
import java.math.BigDecimal;
/** Datos de entrada de los formularios de pago. */

public class PagoForm {
    private BigDecimal monto;
    private MetodoPago metodo;
    private String telefonoYape;
    private String codigoOperacion;
    private String telefonoDevolucion;
    public BigDecimal getMonto() { return monto; } public void setMonto(BigDecimal monto) { this.monto = monto; }
    public MetodoPago getMetodo() { return metodo; } public void setMetodo(MetodoPago metodo) { this.metodo = metodo; }
    public String getTelefonoYape() { return telefonoYape; } public void setTelefonoYape(String telefonoYape) { this.telefonoYape = telefonoYape; }
    public String getCodigoOperacion() { return codigoOperacion; } public void setCodigoOperacion(String codigoOperacion) { this.codigoOperacion = codigoOperacion; }
    public String getTelefonoDevolucion() { return telefonoDevolucion; } public void setTelefonoDevolucion(String telefonoDevolucion) { this.telefonoDevolucion = telefonoDevolucion; }
}
