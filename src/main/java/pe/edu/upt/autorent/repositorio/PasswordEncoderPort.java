package pe.edu.upt.autorent.repositorio;
/** Puerto de salida: contrato para cifrar y comprobar contraseñas. */

public interface PasswordEncoderPort {
    /** Genera el hash seguro de una contraseña. */
    String encode(String rawPassword);
    /** Comprueba una contraseña contra su hash. */
    boolean matches(String rawPassword, String encodedPassword);
}
