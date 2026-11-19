package pe.edu.upt.autorent.controlador;

import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.upt.autorent.modelo.Auto;
import pe.edu.upt.autorent.modelo.EstadoAuto;
import pe.edu.upt.autorent.modelo.EstadoPago;
import pe.edu.upt.autorent.modelo.MetodoPago;
import pe.edu.upt.autorent.modelo.Pago;
import pe.edu.upt.autorent.modelo.ReglasAlquiler;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import pe.edu.upt.autorent.servicio.contrato.AutoUseCase;
import pe.edu.upt.autorent.servicio.contrato.ComprobanteUseCase;
import pe.edu.upt.autorent.servicio.contrato.ConsultaReservaUseCase;
import pe.edu.upt.autorent.servicio.contrato.PagoEnLineaUseCase;
import pe.edu.upt.autorent.servicio.contrato.ReservaUseCase;
import pe.edu.upt.autorent.controlador.formulario.ReservaForm;
/**
 * Controlador web del CLIENTE: crear una reserva en línea, ver "Mis reservas",
 * reintentar el anticipo, pagar en línea y cancelar. Las reglas del negocio están en los servicios.
 */

@Controller
public class ReservaController {
    private final AutoUseCase autos;
    private final ReservaUseCase reservas;
    private final PagoEnLineaUseCase pagosEnLinea;
    private final ComprobanteUseCase comprobantes;
    private final ConsultaReservaUseCase consultas;
    @Value("${autorent.yape.numero:999000000}") private String yapeNegocio;
    @Value("${autorent.yape.titular:AutoRent}") private String titularYape;

    public ReservaController(AutoUseCase autos, ReservaUseCase reservas, PagoEnLineaUseCase pagosEnLinea,
                             ComprobanteUseCase comprobantes, ConsultaReservaUseCase consultas) {
        this.autos = autos; this.reservas = reservas; this.pagosEnLinea = pagosEnLinea; this.comprobantes = comprobantes;
        this.consultas = consultas;
    }

    /** Expediente de una reserva del cliente: pagos, boletas, devoluciones e historial paso a paso. */
    @GetMapping("/mis-reservas/{id}") public String detalle(@PathVariable Long id, Authentication authentication,
                                                           Model model, RedirectAttributes flash) {
        try {
            model.addAttribute("d", consultas.detalle(id, authentication.getName()));
            model.addAttribute("volver", "/mis-reservas");
            return "reservas/detalle";
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            return "redirect:/mis-reservas";
        }
    }

    /** Métodos que puede elegir el cliente en la web (PayPal solo si está configurado). */
    private List<MetodoPago> metodosDisponibles() {
        return Arrays.stream(MetodoPago.values())
                .filter(m -> m != MetodoPago.PAYPAL || pagosEnLinea.payPalDisponible()).toList();
    }

    /** Formulario de reserva de un vehículo; muestra el número Yape de la empresa. */
    @GetMapping("/reservas/nueva/{autoId}") public String nueva(@PathVariable Long autoId, Model model, RedirectAttributes flash) {
        Auto auto;
        try { auto = autos.obtener(autoId); }
        catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); return "redirect:/autos"; }
        // Un vehículo inactivo no se ofrece aunque se escriba su dirección a mano.
        // Uno en el taller sí: se puede reservar para después del mantenimiento.
        if (auto.estado() == EstadoAuto.INACTIVO) {
            flash.addFlashAttribute("error", "El vehículo no está habilitado para reservas.");
            return "redirect:/autos";
        }
        // Si se vuelve aquí por un error, se conserva lo que el cliente ya había escrito.
        ReservaForm form = model.containsAttribute("form") ? (ReservaForm) model.getAttribute("form") : new ReservaForm();
        form.setAutoId(autoId);
        model.addAttribute("auto", auto); model.addAttribute("form", form);
        // Días en que este auto ya está ocupado: se muestran y el navegador no deja elegirlos.
        model.addAttribute("ocupado", autos.fechasOcupadas(autoId));
        model.addAttribute("metodos", metodosDisponibles());
        LocalDate hoy = LocalDate.now();
        model.addAttribute("fechaMinima", hoy); // no se puede reservar en fechas pasadas
        model.addAttribute("fechaMaxima", hoy.plusDays(ReglasAlquiler.MAXIMO_DIAS_ANTICIPACION));
        model.addAttribute("maximoDias", ReglasAlquiler.MAXIMO_DIAS_ALQUILER);
        model.addAttribute("yapeNegocio", yapeNegocio);
        model.addAttribute("titularYape", titularYape);
        return "reservas/nueva";
    }

    /** Envía la reserva; authentication.getName() es el cliente que inició sesión. Si eligió pago en línea, va a la pantalla de pago. */
    @PostMapping("/reservas/crear") public String crear(@ModelAttribute("form") ReservaForm f,
            Authentication authentication, RedirectAttributes flash) {
        try {
            var reserva = reservas.crearReservaOnline(authentication.getName(), f.getAutoId(), f.getFechaInicio(), f.getFechaFin(),
                    f.getMontoAnticipo(), f.getMetodoPago(), f.getTelefonoYape(), f.getCodigoOperacion(), f.getTelefonoDevolucion());
            if (f.getMetodoPago() != null && f.getMetodoPago().esEnLinea()) {
                var pago = reservas.buscarPagoEnLineaPendiente(authentication.getName(), reserva.id());
                if (pago.isPresent()) return "redirect:/pagos/" + pago.get().id() + "/pagar";
            }
            flash.addFlashAttribute("ok", "Solicitud registrada. Si pagó con Yape, el trabajador debe validar la operación; si eligió efectivo, acérquese a la sucursal dentro de las próximas 24 horas.");
            return "redirect:/mis-reservas";
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            if (f.getAutoId() == null) return "redirect:/autos";
            flash.addFlashAttribute("form", f);
            return "redirect:/reservas/nueva/" + f.getAutoId();
        }
    }

    /** "Mis reservas": reservas, pagos, devoluciones y comprobantes del cliente, con los botones que le corresponden. */
    @GetMapping("/mis-reservas") public String misReservas(Authentication authentication, Model model) {
        String usuario = authentication.getName();
        var lista = reservas.listarReservas(usuario);
        var listaPagos = reservas.listarPagosDeCliente(usuario);
        model.addAttribute("reservas", lista);
        model.addAttribute("pagos", listaPagos);
        // El servicio decide en qué reservas se puede reintentar el pago o cancelar.
        model.addAttribute("reintentables", reservas.reservasReintentables(lista, listaPagos));
        model.addAttribute("cancelables", reservas.reservasCancelables(lista, listaPagos));
        model.addAttribute("saldos", reservas.calcularSaldos(lista, listaPagos));
        // Reservas con un pago en línea a medio terminar: se muestra el botón "Pagar ahora".
        Map<Long, Long> pagosEnCurso = new HashMap<>();
        for (Pago p : listaPagos) {
            if (p.estado() == EstadoPago.PENDIENTE && p.metodo().esEnLinea()) pagosEnCurso.put(p.reservaId(), p.id());
        }
        model.addAttribute("pagosEnCurso", pagosEnCurso);
        model.addAttribute("hayPendiente", listaPagos.stream().filter(p -> p.estado() == EstadoPago.PENDIENTE)
                .map(Pago::reservaId).toList());
        model.addAttribute("metodos", metodosDisponibles());
        model.addAttribute("devoluciones", reservas.listarDevolucionesDeCliente(usuario));
        model.addAttribute("comprobantes", comprobantes.listarDeCliente(usuario));
        return "reservas/mis-reservas";
    }

    /** Reintenta el anticipo después de un rechazo (o de un pago en línea que no se completó). */
    @PostMapping("/reservas/{id}/reintentar") public String reintentar(@PathVariable Long id,
            @ModelAttribute ReservaForm f, Authentication authentication, RedirectAttributes flash) {
        try {
            reservas.reintentarAnticipo(authentication.getName(), id, f.getMontoAnticipo(), f.getMetodoPago(),
                    f.getTelefonoYape(), f.getCodigoOperacion(), f.getTelefonoDevolucion());
            if (f.getMetodoPago() != null && f.getMetodoPago().esEnLinea()) {
                var pago = reservas.buscarPagoEnLineaPendiente(authentication.getName(), id);
                if (pago.isPresent()) return "redirect:/pagos/" + pago.get().id() + "/pagar";
            }
            flash.addFlashAttribute("ok", "Nuevo anticipo registrado. Queda pendiente de validación o cobro.");
        } catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/mis-reservas";
    }

    /** El cliente paga en línea el saldo de una reserva confirmada. */
    @PostMapping("/reservas/{id}/pagar-saldo") public String pagarSaldo(@PathVariable Long id,
            Authentication authentication, RedirectAttributes flash) {
        try {
            Pago pago = pagosEnLinea.iniciarPagoSaldo(authentication.getName(), id);
            return "redirect:/pagos/" + pago.id() + "/pagar";
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            return "redirect:/mis-reservas";
        }
    }

    /** Cancela la reserva del cliente (regla de 24 horas). */
    @PostMapping("/reservas/{id}/cancelar") public String cancelar(@PathVariable Long id,
            Authentication authentication, RedirectAttributes flash) {
        try {
            reservas.cancelarReserva(id, authentication.getName(), null);
            flash.addFlashAttribute("ok", "Reserva cancelada. Los pagos con tarjeta o PayPal se devuelven automáticamente; los de Yape o efectivo los devuelve la sucursal.");
        }
        catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/mis-reservas";
    }
}
