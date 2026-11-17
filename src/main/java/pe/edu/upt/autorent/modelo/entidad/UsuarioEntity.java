package pe.edu.upt.autorent.modelo.entidad;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;
import pe.edu.upt.autorent.modelo.Rol;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios", uniqueConstraints = {
        @UniqueConstraint(name = "uk_usuario_username", columnNames = "username"),
        @UniqueConstraint(name = "uk_usuario_documento", columnNames = "documento"),
        @UniqueConstraint(name = "uk_usuario_correo", columnNames = "correo")
})
/** Entidad JPA de la tabla de usuarios. Contiene atributos, getters y setters para MySQL. */
public class UsuarioEntity {
    /** Constructor sin argumentos requerido por JPA. */
    public UsuarioEntity() { }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 60) private String username;
    @Column(nullable = false, length = 100) private String passwordHash;
    @Column(nullable = false, length = 80) private String nombres;
    @Column(nullable = false, length = 80) private String apellidos;
    @Column(nullable = false, length = 20) private String documento;
    @Column(nullable = false, length = 120) private String correo;
    @Column(length = 20) private String telefono;
    @Column(length = 200) private String direccion;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20) private Rol rol;
    @Column(nullable = false) private boolean activo = true;
    @Column(nullable = false) private LocalDateTime creadoEn;

    @PrePersist void prePersist() { if (creadoEn == null) creadoEn = LocalDateTime.now(); }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }
    public String getApellidos() { return apellidos; }
    public void setApellidos(String apellidos) { this.apellidos = apellidos; }
    public String getDocumento() { return documento; }
    public void setDocumento(String documento) { this.documento = documento; }
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public Rol getRol() { return rol; }
    public void setRol(Rol rol) { this.rol = rol; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }
}
