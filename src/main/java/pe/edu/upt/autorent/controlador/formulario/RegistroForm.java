package pe.edu.upt.autorent.controlador.formulario;
/** Datos de entrada para registrar clientes o corregir los datos de una cuenta. */

public class RegistroForm {
    /** Formulario lleno con los datos actuales de la cuenta (para editarla). La contraseña nunca se copia. */
    public static RegistroForm de(pe.edu.upt.autorent.modelo.Usuario u) {
        RegistroForm f = new RegistroForm();
        f.setUsername(u.username()); f.setNombres(u.nombres()); f.setApellidos(u.apellidos());
        f.setDocumento(u.documento()); f.setCorreo(u.correo()); f.setTelefono(u.telefono()); f.setDireccion(u.direccion());
        return f;
    }

    private String username;
    private String password;
    private String nombres;
    private String apellidos;
    private String documento;
    private String correo;
    private String telefono;
    private String direccion;
    public String getUsername() { return username; } public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; } public void setPassword(String password) { this.password = password; }
    public String getNombres() { return nombres; } public void setNombres(String nombres) { this.nombres = nombres; }
    public String getApellidos() { return apellidos; } public void setApellidos(String apellidos) { this.apellidos = apellidos; }
    public String getDocumento() { return documento; } public void setDocumento(String documento) { this.documento = documento; }
    public String getCorreo() { return correo; } public void setCorreo(String correo) { this.correo = correo; }
    public String getTelefono() { return telefono; } public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getDireccion() { return direccion; } public void setDireccion(String direccion) { this.direccion = direccion; }
}
