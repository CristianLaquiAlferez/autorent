package pe.edu.upt.autorent.servicio.contrato;

import java.math.BigDecimal;
import pe.edu.upt.autorent.modelo.DatosTarjeta;
import pe.edu.upt.autorent.modelo.OrdenEnLinea;
import pe.edu.upt.autorent.modelo.Pago;
import pe.edu.upt.autorent.modelo.Reserva;
import pe.edu.upt.autorent.modelo.ResultadoCobro;

/** Puerto de entrada: pagos en línea del cliente (tarjeta Visa/Mastercard y PayPal) y reembolsos automáticos. */
public interface PagoEnLineaUseCase {
    /** Datos que necesita la pantalla de pago. */
    record DatosCheckout(Pago pago, Reserva reserva, boolean payPalDisponible, String payPalClientId,
                         String payPalMoneda, BigDecimal montoPayPal, String nombrePasarelaTarjeta) { }

    /** ¿Está configurado PayPal Sandbox? (si no, solo se ofrece la pasarela de tarjeta de prueba). */
    boolean payPalDisponible();
    /** Pantalla de pago: valida que el pago sea del cliente, esté pendiente y sea en línea. */
    DatosCheckout prepararCheckout(String username, Long pagoId);
    /** El cliente decide pagar en línea el saldo de una reserva confirmada; devuelve el pago pendiente creado. */
    Pago iniciarPagoSaldo(String username, Long reservaId);
    /** Cobra con tarjeta en la pasarela; si se aprueba, confirma el pago y la reserva. */
    ResultadoCobro pagarConTarjeta(String username, Long pagoId, DatosTarjeta tarjeta);
    /** Crea la orden en PayPal para que el cliente la apruebe en la ventana de PayPal. */
    OrdenEnLinea crearOrdenPayPal(String username, Long pagoId);
    /** Cobra la orden aprobada en PayPal y confirma el pago. */
    ResultadoCobro capturarPayPal(String username, Long pagoId, String ordenId);
    /** Devuelve automáticamente por la pasarela el dinero de una devolución de un pago en línea. */
    void procesarReembolsoEnLinea(Long devolucionId);
}
