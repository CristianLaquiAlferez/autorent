package pe.edu.upt.autorent.servicio;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upt.autorent.modelo.Rol;
import pe.edu.upt.autorent.modelo.TelefonoPeru;
import pe.edu.upt.autorent.modelo.Usuario;
import pe.edu.upt.autorent.servicio.contrato.UsuarioUseCase;
import pe.edu.upt.autorent.modelo.EstadoReserva;
import pe.edu.upt.autorent.modelo.Reserva;
import pe.edu.upt.autorent.repositorio.PagoRepositoryPort;
import pe.edu.upt.autorent.repositorio.PasswordEncoderPort;
import pe.edu.upt.autorent.repositorio.ReservaRepositoryPort;
import pe.edu.upt.autorent.repositorio.UsuarioRepositoryPort;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
/**
 * Servicio de negocio de usuarios: registro de clientes y trabajadores, consultas y activación.
 * Las contraseñas nunca se guardan en texto plano: se cifran con BCrypt a través de PasswordEncoderPort.
 */

@Service
// READ COMMITTED: después de tomar un candado, cada consulta ve lo que otras transacciones ya guardaron.
// Con el modo por defecto de MySQL (REPEATABLE READ) la verificación de cruces leía una "foto" vieja
// y varias reservas simultáneas del mismo auto pasaban a la vez.
@Transactional(isolation = Isolation.READ_COMMITTED)
public class UsuarioService implements UsuarioUseCase {
    // Reglas de formato: DNI de 8 dígitos y correo con el formato nombre@dominio.ext
    private static final Pattern DNI = Pattern.compile("\\d{8}");
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private final UsuarioRepositoryPort usuarios;
    private final PasswordEncoderPort encoder;
    private final ReservaRepositoryPort reservas;
    private final PagoRepositoryPort pagos;

    public UsuarioService(UsuarioRepositoryPort usuarios, PasswordEncoderPort encoder,
                          ReservaRepositoryPort reservas, PagoRepositoryPort pagos) {
        this.usuarios = usuarios; this.encoder = encoder; this.reservas = reservas; this.pagos = pagos;
    }

    /** Registra un cliente (desde "Crear cuenta" o desde el mostrador). */
    @Override public Usuario registrarCliente(String username, String password, String nombres, String apellidos,
                                               String documento, String correo, String telefono, String direccion) {
        return crear(username, password, nombres, apellidos, documento, correo, telefono, direccion, Rol.CLIENTE);
    }

    /** El administrador registra a un trabajador de la empresa. */
    @Override public Usuario crearTrabajador(String username, String password, String nombres, String apellidos,
                                              String documento, String correo, String telefono, String direccion) {
        return crear(username, password, nombres, apellidos, documento, correo, telefono, direccion, Rol.TRABAJADOR);
    }

    /**
     * Lógica común de registro: limpia los datos, valida formato y tamaño, comprueba que usuario,
     * DNI y correo no estén repetidos, cifra la contraseña y guarda la cuenta activa.
     */
    private Usuario crear(String username, String password, String nombres, String apellidos,
                          String documento, String correo, String telefono, String direccion, Rol rol) {
        username = limpiar(username).toLowerCase();
        nombres = limpiar(nombres); apellidos = limpiar(apellidos); documento = limpiar(documento);
        correo = limpiar(correo).toLowerCase(); telefono = limpiar(telefono); direccion = limpiar(direccion);
        if (username.length() < 4 || username.length() > 60) throw new IllegalArgumentException("El usuario debe tener entre 4 y 60 caracteres.");
        if (!username.matches("[a-z0-9._-]{4,60}")) throw new IllegalArgumentException("El usuario solo admite letras, números, punto, guion y guion bajo.");
        if (nombres.length() > 80 || apellidos.length() > 80 || correo.length() > 120 || direccion.length() > 200 ||
                password != null && password.length() > 72)
            throw new IllegalArgumentException("Uno de los campos supera el tamaño permitido.");
        if (password == null || password.length() < 8) throw new IllegalArgumentException("La contraseña debe tener como mínimo 8 caracteres.");
        if (!password.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ].*") || !password.matches(".*\\d.*"))
            throw new IllegalArgumentException("La contraseña debe combinar letras y números.");
        if (nombres.isBlank() || apellidos.isBlank()) throw new IllegalArgumentException("Ingrese nombres y apellidos.");
        if (!DNI.matcher(documento).matches()) throw new IllegalArgumentException("El documento debe tener 8 dígitos.");
        if (!EMAIL.matcher(correo).matches()) throw new IllegalArgumentException("Ingrese un correo válido.");
        if (!telefono.isBlank() && !TelefonoPeru.esContactoValido(telefono))
            throw new IllegalArgumentException("El teléfono de contacto debe tener entre 6 y 9 dígitos numéricos, sin +51.");
        if (usuarios.existsByUsername(username)) throw new IllegalArgumentException("Ese nombre de usuario ya está registrado.");
        if (usuarios.existsByDocumento(documento)) throw new IllegalArgumentException("Ese documento ya está registrado.");
        if (usuarios.existsByCorreo(correo)) throw new IllegalArgumentException("Ese correo ya está registrado.");
        Usuario nuevo = new Usuario(null, username, encoder.encode(password), nombres, apellidos,
                documento, correo, telefono, direccion, rol, true);
        return usuarios.save(nuevo);
    }

    @Override @Transactional(readOnly = true) public List<Usuario> listarUsuarios() { return usuarios.findAll(); }
    @Override @Transactional(readOnly = true) public List<Usuario> listarPorRol(Rol rol) { return usuarios.findByRol(rol); }
    @Override @Transactional(readOnly = true) public Optional<Usuario> buscarPorUsername(String username) { return usuarios.findByUsername(username); }
    @Override @Transactional(readOnly = true) public Usuario obtener(Long id) {
        return usuarios.findById(id).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
    }
    /** Activa o desactiva una cuenta. Siempre debe quedar al menos un administrador activo. */
    @Override public void cambiarEstado(Long id, boolean activo) {
        Usuario u = obtener(id);
        if (u.rol() == Rol.ADMIN && !activo) {
            long adminsActivos = usuarios.findByRol(Rol.ADMIN).stream().filter(Usuario::activo).count();
            if (adminsActivos <= 1) throw new IllegalArgumentException("No se puede desactivar al último administrador activo.");
        }
        // Un cliente con reservas en marcha o con deuda no se puede desactivar: quedarían reservas y cobros sin dueño activo.
        if (u.rol() == Rol.CLIENTE && !activo) {
            for (Reserva r : reservas.findByClienteId(u.id())) {
                if (r.estado().esActiva() || r.estado() == EstadoReserva.EN_CURSO)
                    throw new IllegalArgumentException("El cliente tiene la reserva N.° " + r.id() + " en estado "
                            + r.estado().name().replace('_', ' ') + ". Ciérrela o cancélela antes de desactivar la cuenta.");
                if (r.estado() == EstadoReserva.FINALIZADA && r.total().compareTo(pagos.totalConfirmado(r.id())) > 0)
                    throw new IllegalArgumentException("El cliente tiene deuda pendiente en la reserva N.° " + r.id()
                            + ". Cobre el saldo antes de desactivar la cuenta.");
            }
        }
        usuarios.save(new Usuario(u.id(), u.username(), u.passwordHash(), u.nombres(), u.apellidos(),
                u.documento(), u.correo(), u.telefono(), u.direccion(), u.rol(), activo));
    }

    /** Convierte null en texto vacío y quita espacios sobrantes. */
    private String limpiar(String valor) { return valor == null ? "" : valor.trim(); }
}
