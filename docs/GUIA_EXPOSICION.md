# Guia breve para explicar AutoRent

**Modelo:** define los datos y estados del negocio. `Auto`, `Reserva`, `Pago` y `Usuario` son modelos inmutables; `modelo.entidad` contiene clases JPA con getters y setters para guardar en MySQL.

**Conexion:** `ConexionMySQLConfig` utiliza el `DataSource` que Spring Boot configura con `application.properties`. `conexion.seguridad` define los permisos y `conexion.inicializacion` crea datos de demostracion si faltan.

**Repositorio:** interfaces como `AutoRepositoryPort` y `ReservaRepositoryPort`. Son los contratos que necesitan los servicios, sin depender de una tecnologia de base de datos.

**DAO:** adaptadores `JpaAutoAdapter`, `JpaReservaAdapter`, etc. Implementan los contratos; `dao.springdata` ejecuta consultas JPA y `dao.archivos` almacena fotos.

**Servicio:** contiene validaciones, calculos y decisiones. `ReservaService` controla el anticipo del 20 %, los pagos, la entrega y la devolucion. `AutoService` valida los datos y las imagenes.

**Controlador:** recibe las solicitudes del navegador, prepara datos de formularios y devuelve paginas Thymeleaf. No contiene las reglas de negocio: incluso el saldo de cada reserva y que reservas se pueden cancelar los calcula `ReservaService` (`calcularSaldos`, `reservasCancelables`, `reservasReintentables`).

**Ejemplo de flujo:** el administrador llena `autos/formulario.html` -> `AutoController.guardar` -> `AutoService.guardarConImagen` -> `AutoRepositoryPort` / `AutoImagenRepositoryPort` -> adaptadores DAO -> MySQL o carpeta de fotos.

**Reportes:** `ReporteController.descargarPdf` obtiene el resumen mediante el caso de uso de reportes y llama a `servicio.pdf.ReportePdfGenerator`.

**Importante:** se conserva la base de datos `autorent_db` y el proyecto es compatible con NetBeans. Los archivos HTML estan en `src/main/resources/templates` y los estilos en `static/css/app.css`.
