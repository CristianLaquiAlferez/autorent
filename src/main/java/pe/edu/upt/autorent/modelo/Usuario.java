package pe.edu.upt.autorent.modelo;
/** Modelo de negocio de usuario y rol, independiente de la base de datos. */

public record Usuario(Long id, String username, String passwordHash, String nombres,
                      String apellidos, String documento, String correo, String telefono,
                      String direccion, Rol rol, boolean activo) {
    public String nombreCompleto() {
        return (nombres == null ? "" : nombres) + " " + (apellidos == null ? "" : apellidos).trim();
    }
}
