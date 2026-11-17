package pe.edu.upt.autorent.controlador;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.upt.autorent.modelo.DatosTarjeta;
import pe.edu.upt.autorent.modelo.ResultadoCobro;
import pe.edu.upt.autorent.servicio.contrato.PagoEnLineaUseCase;

/**
 * Controlador web de la pantalla de pago en línea del CLIENTE (rutas /pagos/**, protegidas en SecurityConfig).
 * - Tarjeta: formulario normal (POST) que cobra en la pasarela de prueba.
 * - PayPal: el botón de PayPal llama por JavaScript a /paypal/orden y /paypal/capturar (respuestas JSON).
 */
@Controller
@RequestMapping("/pagos")
public class PagoEnLineaController {
    private final PagoEnLineaUseCase pagos;

    public PagoEnLineaController(PagoEnLineaUseCase pagos) { this.pagos = pagos; }

    /** Pantalla de pago: monto, datos de la reserva, botón de PayPal (si está configurado) y formulario de tarjeta. */
    @GetMapping("/{id}/pagar")
    public String pagar(@PathVariable Long id, Authentication auth, Model model, RedirectAttributes flash) {
        try {
            model.addAttribute("checkout", pagos.prepararCheckout(auth.getName(), id));
            return "pagos/checkout";
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            return "redirect:/mis-reservas";
        }
    }

    /** Cobro con tarjeta. Si se rechaza, vuelve a la pantalla de pago con el motivo (sin perder el pago pendiente). */
    @PostMapping("/{id}/tarjeta")
    public String tarjeta(@PathVariable Long id, @RequestParam String numero, @RequestParam String titular,
                          @RequestParam String vencimiento, @RequestParam String cvv,
                          Authentication auth, RedirectAttributes flash) {
        try {
            ResultadoCobro r = pagos.pagarConTarjeta(auth.getName(), id, new DatosTarjeta(numero, titular, vencimiento, cvv));
            if (!r.aprobado()) {
                flash.addFlashAttribute("error", "Pago rechazado: " + r.mensaje());
                return "redirect:/pagos/" + id + "/pagar";
            }
            flash.addFlashAttribute("ok", "Pago aprobado con " + r.detalle() + " (operación " + r.codigoTransaccion()
                    + "). Su boleta electrónica está en \"Mis comprobantes\".");
            return "redirect:/mis-reservas";
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            return "redirect:/mis-reservas";
        }
    }

    /** Paso 1 de PayPal: crear la orden. El botón de PayPal recibe el id y abre la ventana de aprobación. */
    @PostMapping("/{id}/paypal/orden")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> crearOrden(@PathVariable Long id, Authentication auth) {
        try {
            var orden = pagos.crearOrdenPayPal(auth.getName(), id);
            return ResponseEntity.ok(Map.of("id", orden.id()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    /** Después de PayPal el navegador llega aquí para mostrar el aviso en "Mis reservas". */
    @GetMapping("/{id}/listo")
    public String listo(@PathVariable Long id, RedirectAttributes flash) {
        flash.addFlashAttribute("ok", "Pago aprobado por PayPal. Su boleta electrónica está en \"Mis comprobantes\".");
        return "redirect:/mis-reservas";
    }

    /** Paso 2 de PayPal: el cliente aprobó; el servidor cobra y verifica el monto. */
    @PostMapping("/{id}/paypal/capturar")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> capturar(@PathVariable Long id, @RequestBody Map<String, String> cuerpo,
                                                        Authentication auth) {
        try {
            ResultadoCobro r = pagos.capturarPayPal(auth.getName(), id, cuerpo.get("orderID"));
            if (!r.aprobado()) return ResponseEntity.ok(Map.of("aprobado", false, "mensaje", r.mensaje()));
            return ResponseEntity.ok(Map.of("aprobado", true, "redireccion", "/pagos/" + id + "/listo"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("aprobado", false, "mensaje", ex.getMessage()));
        }
    }
}
