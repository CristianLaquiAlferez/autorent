package pe.edu.upt.autorent.controlador;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.BindException;
/**
 * Manejo centralizado de errores: si una excepción llega hasta aquí, en lugar de la página de error
 * técnica se muestra "error-operación.html" con un mensaje entendible para el usuario.
 */

@ControllerAdvice
public class ErrorOperacionControllerAdvice {
    /** Regla de negocio incumplida: se muestra el mensaje que lanzó el servicio. */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String errorDeNegocio(IllegalArgumentException ex, Model model) {
        model.addAttribute("mensaje", ex.getMessage());
        return "error-operacion";
    }

    /**
     * Dos operaciones chocaron en la base de datos (por ejemplo, el mismo código Yape enviado dos veces a la vez):
     * la restricción UNIQUE de MySQL lo impide y aquí se muestra un mensaje claro en vez de un error técnico.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String conflictoDeDatos(Model model) {
        model.addAttribute("mensaje", "Ese dato ya fue registrado por otra operación (por ejemplo, un código de operación repetido). Revise y vuelva a intentarlo.");
        return "error-operacion";
    }

    /** La foto supera el límite configurado en application.properties. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public String imagenDemasiadoGrande(Model model) {
        model.addAttribute("mensaje", "La imagen supera el límite de 5 MB. Seleccione una foto más pequeña.");
        return "error-operacion";
    }

    /** Formulario incompleto o con datos de tipo incorrecto (por ejemplo, letras en un campo numérico). */
    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, BindException.class, java.time.format.DateTimeParseException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String errorDeFormulario(Exception ex, Model model) {
        model.addAttribute("mensaje", "Faltan datos o alguno tiene un formato incorrecto. Revise el formulario e intente nuevamente.");
        return "error-operacion";
    }
}
