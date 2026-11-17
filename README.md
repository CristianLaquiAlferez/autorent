# AutoRent - Proyecto Spring Boot para Apache NetBeans

Sistema de alquiler de vehiculos en **Java 17 + Spring Boot 3.5.6 + Thymeleaf + MySQL**.
El codigo esta distribuido en **seis paquetes principales**, con comentarios breves para explicar las responsabilidades de cada clase y las reglas de negocio.

## Arquitectura hexagonal adaptada a seis paquetes

```
pe.edu.upt.autorent
|-- AutoRentApplication.java       Entrada principal
|-- modelo/                        Objetos del dominio y entidades JPA
|   `-- entidad/                   Entidades con atributos, getters y setters
|-- conexion/                      Configuracion y acceso a MySQL
|   |-- ConexionMySQLConfig.java
|   |-- seguridad/                 Autenticacion y permisos
|   `-- inicializacion/            Datos de demostracion si no existen
|-- repositorio/                   Puertos de salida (interfaces de persistencia)
|-- dao/                           Adaptadores de los puertos y consultas JPA
|   |-- springdata/                Interfaces JpaRepository
|   |-- archivos/                  Almacenamiento de fotografias
|   `-- seguridad/                 Implementacion de BCrypt
|-- servicio/                      Reglas y casos de uso
|   |-- contrato/                  Puertos de entrada (interfaces de casos de uso)
|   `-- pdf/                       Generador de reportes PDF
`-- controlador/                   Peticiones HTTP y vistas HTML
    `-- formulario/                Datos de formularios
```

**Recorrido de una peticion:**
`HTML -> controlador -> servicio.contrato -> servicio -> repositorio -> dao -> MySQL`.
Las interfaces de `repositorio` no conocen JPA ni SQL; los adaptadores de `dao` implementan sus contratos. `modelo` define las reglas y datos del negocio; las entidades JPA de `modelo.entidad` se convierten con `JpaMapper`.

## Abrir en Apache NetBeans

1. Extrae el ZIP y abre la carpeta **AutoRent** desde **File > Open Project**.
2. Inicia **MySQL en XAMPP**. Mantiene la base de datos existente `autorent_db`; no importes ni borres datos.
3. Comprueba `src/main/resources/application.properties`:
   - URL: `jdbc:mysql://localhost:3306/autorent_db`
   - Usuario: `root`
   - Contrasena: vacia (ajustala si tu MySQL tiene clave)
4. Ejecuta **Clean and Build** y luego **Run**.
5. Abre `http://localhost:8080`.

La configuracion `spring.jpa.hibernate.ddl-auto=update` conserva las tablas existentes y aplica cambios compatibles cuando hacen falta. Se recomienda hacer una copia de seguridad antes de cualquier actualizacion.

## Cuentas de demostracion

| Rol | Usuario | Contrasena |
|---|---|---|
| Administrador | `admin` | `Admin123*` |
| Trabajador | `trabajador` | `Trabajador123*` |
| Cliente | `cliente` | `Cliente123*` |

Los usuarios de prueba solo se crean si aun no existen. Las claves deben cambiarse antes de publicar la aplicacion.

## Funciones conservadas

- Gestion de usuarios, clientes, roles y acceso por permisos.
- Catalogo, administracion y mantenimiento de vehiculos.
- Fotos mediante URL o seleccion de archivo JPG, PNG o WEBP (hasta 5 MB).
- Reservas en linea y presenciales, anticipo minimo del 20 %, pagos en efectivo, Yape, tarjeta Visa/Mastercard (pasarela de prueba) o PayPal Sandbox.
- Boleta electronica por cada pago y nota de credito por cada devolucion (modo de prueba o Nubefact).
- Tareas automaticas: pagos en linea abandonados, reservas vencidas sin pago y clientes no presentados.
- Confirmacion de pagos, entregas, recepciones, cancelaciones y devoluciones.
- Reportes con indicadores y descarga del historial completo en PDF.
- Validacion peruana: contactos de 6 a 9 digitos y celulares/Yape de 9 digitos empezando por 9.

Las imagenes subidas se almacenan por defecto en `${user.home}/AutoRent/imagenes` y no se pierden al sustituir el proyecto, siempre que se conserve esa carpeta.

## Pruebas

Los tests JUnit se encuentran en `src/test/java` y se ejecutan con **Clean and Build**. Cubren reglas del alquiler, archivos, telefonos, PDF, arranque de Spring Boot y flujos HTTP/JPA/Security con H2 en memoria.

El proyecto tiene 97 pruebas JUnit y todas pasan con **Clean and Build** en NetBeans. El detalle está en `docs/VERIFICACION.md`.

## Unidad 2: pagos, boletas y hosting

- `docs/INTEGRACIONES_PAGO_Y_BOLETAS.md`: tarjetas de prueba, cómo activar PayPal Sandbox y Nubefact.
- `docs/DESPLIEGUE_RAILWAY.md`: publicar la aplicación y MySQL en Railway.
- `database/autorent_db.sql`: script completo de la base de datos (8 tablas).

### Trazabilidad de cada operación

- **Detalle de la reserva** (`/mis-reservas/{id}` para el cliente, `/operaciones/reservas/{id}` para el personal):
  datos del alquiler, resumen económico (total, pagado, devuelto, saldo), pagos con quién los registró y quién los
  confirmó o rechazó, boletas y notas de crédito, devoluciones con su código de operación y la línea de tiempo completa.
  Se puede imprimir o guardar como PDF.
- **Bitácora** (`historial_reservas`): cada paso queda anotado con fecha, usuario, estado anterior y nuevo, y detalle.
  Las acciones automáticas (vencimientos, pasarela, facturación) se firman como `SISTEMA`.
- **Caja** (`/operaciones/caja`): historial de pagos con filtros por fecha, método y estado, y total por método.
- **Reglas que cierran huecos:** motivo obligatorio para rechazar un pago o anular una reserva; devolución completada
  solo con código de operación; entrega y recepción con kilometraje, combustible y estado del auto; cargos por daños
  con concepto; un cliente con deuda no puede reservar ni ser desactivado; tampoco con reservas en curso.
