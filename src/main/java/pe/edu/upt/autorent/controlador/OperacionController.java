package pe.edu.upt.autorent.controlador;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.upt.autorent.modelo.EstadoPago;
import pe.edu.upt.autorent.modelo.MetodoPago;
import pe.edu.upt.autorent.servicio.contrato.ConsultaReservaUseCase;
import java.math.BigDecimal;
import java.time.Clock;
import pe.edu.upt.autorent.modelo.Rol;
import pe.edu.upt.autorent.servicio.contrato.AutoUseCase;
import pe.edu.upt.autorent.servicio.contrato.ReservaUseCase;
import pe.edu.upt.autorent.servicio.contrato.UsuarioUseCase;
import pe.edu.upt.autorent.controlador.formulario.PagoForm;
import pe.edu.upt.autorent.controlador.formulario.ReservaForm;
import pe.edu.upt.autorent.controlador.formulario.RegistroForm;
import java.time.LocalDate;
/**
 * Controlador web del personal (ADMIN y TRABAJADOR): reservas presenciales, validación de pagos,
 * entregas, recepciones y devoluciones de dinero.
 *
 * Solo recibe la petición, llama al caso de uso y elige la vista. Las reglas del negocio están en
 * ReservaService. Si el servicio rechaza una operación (IllegalArgumentException), el mensaje se
 * muestra en la pantalla con "flash" y se vuelve al listado.
 * Las rutas /operaciones/** están protegidas en SecurityConfig.
 */

@Controller
@RequestMapping("/operaciones")
public class OperacionController {
    private final ReservaUseCase reservas;
    private final AutoUseCase autos;
    private final UsuarioUseCase usuarios;
    private final ConsultaReservaUseCase consultas;
    private final Clock reloj;
    // Datos de la cuenta Yape de la empresa, leídos de application.properties.
    @Value("${autorent.yape.numero:999000000}") private String yapeNegocio;
    @Value("${autorent.yape.titular:AutoRent}") private String titularYape;
    public OperacionController(ReservaUseCase reservas, AutoUseCase autos, UsuarioUseCase usuarios,
                               ConsultaReservaUseCase consultas, Clock reloj) {
        this.reservas = reservas; this.autos = autos; this.usuarios = usuarios; this.consultas = consultas; this.reloj = reloj;
    }

    /** Expediente de una reserva: datos, pagos, boletas, devoluciones e historial paso a paso. */
    @GetMapping("/reservas/{id}") public String detalle(@PathVariable Long id, Authentication auth, Model model,
                                                       RedirectAttributes flash) {
        try {
            model.addAttribute("d", consultas.detalle(id, auth.getName()));
            model.addAttribute("volver", "/operaciones/reservas");
            return "reservas/detalle";
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            return "redirect:/operaciones/reservas";
        }
    }

    /**
     * Historial de pagos y cuadre de caja. Sin filtros muestra el día de hoy;
     * si se envían las fechas vacías muestra todo.
     */
    @GetMapping("/caja") public String caja(@RequestParam(required = false) String desde,
                                            @RequestParam(required = false) String hasta,
                                            @RequestParam(required = false) MetodoPago metodo,
                                            @RequestParam(required = false) EstadoPago estado,
                                            Model model, RedirectAttributes flash) {
        LocalDate hoy = LocalDate.now(reloj);
        LocalDate fDesde = desde == null ? hoy : (desde.isBlank() ? null : LocalDate.parse(desde));
        LocalDate fHasta = hasta == null ? hoy : (hasta.isBlank() ? null : LocalDate.parse(hasta));
        try {
            model.addAttribute("caja", consultas.caja(fDesde, fHasta, metodo, estado));
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            return "redirect:/operaciones/caja";
        }
        model.addAttribute("desde", fDesde);
        model.addAttribute("hasta", fHasta);
        model.addAttribute("metodo", metodo);
        model.addAttribute("estado", estado);
        model.addAttribute("metodos", MetodoPago.values());
        model.addAttribute("estados", EstadoPago.values());
        return "operaciones/caja";
    }

    /** Panel de reservas: lista reservas y pagos, con el saldo y las acciones disponibles de cada una. */
    @GetMapping("/reservas") public String reservas(Model model) {
        var listaReservas = reservas.listarTodas();
        var listaPagos = reservas.listarPagos();
        model.addAttribute("reservas", listaReservas);
        model.addAttribute("pagos", listaPagos);
        // El saldo y las reservas que el personal puede anular los calcula el servicio (reglas del negocio).
        model.addAttribute("saldos", reservas.calcularSaldos(listaReservas, listaPagos));
        model.addAttribute("cancelables", reservas.reservasAnulablesPorPersonal(listaReservas));
        model.addAttribute("fechaHoy", LocalDate.now(reloj));
        return "operaciones/reservas";
    }

    /** Formulario de reserva presencial: solo clientes activos y vehículos que se pueden reservar. */
    @GetMapping("/reservas/nueva") public String nuevaReserva(Model model) {
        // Si se vuelve aquí por un error, el formulario llega con lo que se había escrito (atributo flash).
        if (!model.containsAttribute("form")) model.addAttribute("form", new ReservaForm());
        LocalDate hoy = LocalDate.now(reloj);
        model.addAttribute("fechaMinima", hoy);
        model.addAttribute("fechaMaxima", hoy.plusDays(pe.edu.upt.autorent.modelo.ReglasAlquiler.MAXIMO_DIAS_ANTICIPACION));
        model.addAttribute("clientes", usuarios.listarPorRol(Rol.CLIENTE).stream().filter(u -> u.activo()).toList());
        model.addAttribute("autos", autos.listarReservables());
        model.addAttribute("metodos", List.of(MetodoPago.EFECTIVO, MetodoPago.YAPE)); // en mostrador no hay pasarela
        model.addAttribute("yapeNegocio", yapeNegocio);
        model.addAttribute("titularYape", titularYape);
        return "operaciones/reserva-presencial";
    }

    /** Lista de clientes: para buscar a uno y corregir sus datos. */
    @GetMapping("/clientes") public String clientes(Model model) {
        model.addAttribute("clientes", usuarios.listarPorRol(Rol.CLIENTE));
        return "operaciones/clientes";
    }

    /** Formulario para corregir los datos de un cliente (por ejemplo, un DNI mal escrito al registrarlo). */
    @GetMapping("/clientes/{id}/editar") public String editarCliente(@PathVariable Long id, Model model, RedirectAttributes flash) {
        try {
            var u = usuarios.obtener(id);
            if (u.rol() != Rol.CLIENTE) throw new IllegalArgumentException("Solo puede corregir los datos de clientes.");
            if (!model.containsAttribute("form")) model.addAttribute("form", RegistroForm.de(u));
            model.addAttribute("usuario", u);
            model.addAttribute("titulo", "Editar datos de " + u.nombres() + " " + u.apellidos());
            model.addAttribute("accion", "/operaciones/clientes/" + id + "/editar");
            model.addAttribute("volver", "/operaciones/clientes");
            model.addAttribute("soloContacto", false);
            return "usuarios/editar";
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            return "redirect:/operaciones/clientes";
        }
    }

    /** Guarda la corrección de un cliente; el servicio revisa que quien edita pueda hacerlo. */
    @PostMapping("/clientes/{id}/editar") public String guardarCliente(@PathVariable Long id, @ModelAttribute("form") RegistroForm form,
            Authentication auth, RedirectAttributes flash) {
        try {
            usuarios.actualizarDatos(id, form.getNombres(), form.getApellidos(), form.getDocumento(), form.getCorreo(),
                    form.getTelefono(), form.getDireccion(), auth.getName());
            flash.addFlashAttribute("ok", "Datos del cliente actualizados.");
            return "redirect:/operaciones/clientes";
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            flash.addFlashAttribute("form", form);
            return "redirect:/operaciones/clientes/" + id + "/editar";
        }
    }

    /** Formulario para registrar a un cliente que llega a la sucursal sin cuenta. */
    @GetMapping("/clientes/nuevo") public String nuevoCliente(Model model) {
        if (!model.containsAttribute("form")) model.addAttribute("form", new RegistroForm());
        return "operaciones/cliente-form";
    }

    /** Guarda el cliente nuevo y regresa al formulario de reserva presencial. */
    @PostMapping("/clientes/crear") public String crearCliente(@ModelAttribute("form") RegistroForm form, RedirectAttributes flash) {
        try {
            usuarios.registrarCliente(form.getUsername(), form.getPassword(), form.getNombres(), form.getApellidos(),
                    form.getDocumento(), form.getCorreo(), form.getTelefono(), form.getDireccion());
            flash.addFlashAttribute("ok", "Cliente registrado. Ya puede seleccionarlo para una reserva presencial.");
            return "redirect:/operaciones/reservas/nueva";
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            form.setPassword(null);                 // la contraseña nunca se devuelve al formulario
            flash.addFlashAttribute("form", form);  // lo demás se conserva para no tener que escribirlo de nuevo
            return "redirect:/operaciones/clientes/nuevo";
        }
    }

    /** Crea la reserva presencial; auth.getName() es el trabajador que la registra. */
    @PostMapping("/reservas/crear") public String crearReserva(@ModelAttribute("form") ReservaForm f,
            Authentication auth, RedirectAttributes flash) {
        try {
            reservas.crearReservaPresencial(f.getClienteId(), f.getAutoId(), f.getFechaInicio(), f.getFechaFin(),
                    f.getMontoAnticipo(), f.getMetodoPago(), f.getTelefonoYape(), f.getCodigoOperacion(),
                    f.getTelefonoDevolucion(), auth.getName());
            flash.addFlashAttribute("ok", f.getMetodoPago() == pe.edu.upt.autorent.modelo.MetodoPago.YAPE
                    ? "Reserva registrada. Verifique el Yape en la cuenta de la empresa y confírmelo en la tabla de pagos."
                    : "Reserva presencial creada y anticipo registrado como pagado.");
        } catch (IllegalArgumentException ex) {
            // Vuelve al formulario con los datos escritos, para no tener que llenarlo otra vez.
            flash.addFlashAttribute("error", ex.getMessage());
            flash.addFlashAttribute("form", f);
            return "redirect:/operaciones/reservas/nueva";
        }
        return "redirect:/operaciones/reservas";
    }

    /** Confirma un pago pendiente (Yape verificado). */
    @PostMapping("/pagos/{id}/confirmar") public String confirmarPago(@PathVariable Long id,
            Authentication auth, RedirectAttributes flash) {
        try { reservas.confirmarPago(id, auth.getName()); flash.addFlashAttribute("ok", "Pago validado. La reserva quedó confirmada si alcanzó el anticipo mínimo."); }
        catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/operaciones/reservas";
    }

    /** Rechaza un pago pendiente que no se pudo verificar. */
    @PostMapping("/pagos/{id}/rechazar") public String rechazarPago(@PathVariable Long id,
            @RequestParam(required = false) String motivo, Authentication auth, RedirectAttributes flash) {
        try { reservas.rechazarPago(id, auth.getName(), motivo); flash.addFlashAttribute("ok", "Pago rechazado. El cliente podrá reintentar si era un anticipo."); }
        catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/operaciones/reservas";
    }

    /** Cancela la reserva; si había pagos confirmados se generan devoluciones pendientes. */
    @PostMapping("/reservas/{id}/cancelar") public String cancelarReserva(@PathVariable Long id,
            @RequestParam(required = false) String motivo, Authentication auth, RedirectAttributes flash) {
        try { reservas.cancelarReserva(id, auth.getName(), motivo); flash.addFlashAttribute("ok", "Reserva cancelada; devoluciones pendientes registradas."); }
        catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/operaciones/reservas";
    }

    /** Registra el pago del saldo restante. */
    @PostMapping("/reservas/{id}/pago-saldo") public String pagoSaldo(@PathVariable Long id,
            @ModelAttribute PagoForm form, Authentication auth, RedirectAttributes flash) {
        try {
            reservas.registrarPagoSaldo(id, form.getMonto(), form.getMetodo(), form.getTelefonoYape(),
                    form.getCodigoOperacion(), form.getTelefonoDevolucion(), auth.getName());
            flash.addFlashAttribute("ok", "Pago registrado. Si es Yape, valide el comprobante antes de entregar el vehículo.");
        } catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/operaciones/reservas";
    }

    /** Cierra una reserva vencida sin anticipo confirmado y libera el vehículo. */
    @PostMapping("/reservas/{id}/vencer") public String vencer(@PathVariable Long id,
            Authentication auth, RedirectAttributes flash) {
        try {
            reservas.vencerReservaSinPago(id, auth.getName());
            flash.addFlashAttribute("ok", "Reserva vencida cerrada sin pagos confirmados. El vehículo quedó liberado.");
        } catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/operaciones/reservas";
    }

    /** Entrega el vehículo al cliente: el alquiler pasa a EN_CURSO. */
    @PostMapping("/reservas/{id}/entregar") public String entregar(@PathVariable Long id,
            @RequestParam(required = false) String observacion, Authentication auth, RedirectAttributes flash) {
        try { reservas.entregarVehiculo(id, auth.getName(), observacion); flash.addFlashAttribute("ok", "Entrega registrada. El alquiler está en curso."); }
        catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/operaciones/reservas";
    }

    /** Recibe el vehículo devuelto: el alquiler pasa a FINALIZADA. */
    @PostMapping("/reservas/{id}/recibir") public String recibir(@PathVariable Long id,
            @RequestParam(required = false) String observacion,
            @RequestParam(required = false) BigDecimal cargoAdicional,
            @RequestParam(required = false) String conceptoCargo,
            Authentication auth, RedirectAttributes flash) {
        try {
            reservas.recibirVehiculo(id, auth.getName(), observacion, cargoAdicional, conceptoCargo);
            flash.addFlashAttribute("ok", "Recepción registrada. Alquiler finalizado; si quedó saldo por cargos, cóbrelo en esta pantalla.");
        }
        catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/operaciones/reservas";
    }

    /** Lista las devoluciones de dinero pendientes y completadas. */
    @GetMapping("/devoluciones") public String devoluciones(Model model) {
        model.addAttribute("devoluciones", reservas.listarDevoluciones());
        model.addAttribute("reservas", reservas.listarTodas());
        return "operaciones/devoluciones";
    }

    /** Marca una devolución de dinero como completada. */
    @PostMapping("/devoluciones/{id}/completar") public String completarDevolucion(@PathVariable Long id,
            @RequestParam(required = false) String codigoOperacion, Authentication auth, RedirectAttributes flash) {
        try { reservas.completarDevolucion(id, auth.getName(), codigoOperacion); flash.addFlashAttribute("ok", "Devolución marcada como completada."); }
        catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/operaciones/devoluciones";
    }
}
