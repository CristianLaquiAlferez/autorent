# Verificación del proyecto

Comprobado con Apache NetBeans 25 (Clean and Build) y MySQL de XAMPP (MariaDB 10.4).

- **Pruebas JUnit:** 102 pruebas en `src/test/java`, todas correctas.

| Clase de prueba | Pruebas | Qué comprueba |
|---|---|---|
| `ReglasAlquilerTest` | 9 | Días, anticipo del 20 %, regla de 24 horas, máximo 30 días y 180 de anticipación, días de retraso |
| `ReservaServiceConsultasTest` | 4 | Saldos, reservas cancelables y reintentos de pago |
| `PasarelaTarjetaSimuladaAdapterTest` | 5 | Luhn, marca, vencimiento, CVV, rechazos simulados, número enmascarado |
| `FacturacionTest` | 4 | JSON de Nubefact (boleta y nota de crédito), cadena QR de SUNAT, monto en letras |
| `FlujosDeNegocioTest` | 13 | Pago con tarjeta + boleta, reembolso + nota de crédito, saldo en línea, seguridad entre clientes, pagos abandonados, vencimiento, no presentados, devolución tardía, anulación por el personal, mantenimiento, pantallas y PDF |
| `TrazabilidadTest` | 6 | Historial completo de un alquiler, deuda por daños que bloquea nuevas reservas, rechazo con motivo y usuario, intento de tarjeta rechazada registrado, anulación con motivo, devolución con evidencia, caja y expediente con permisos |
| `RobustezTest` | 12 | PayPal de punta a punta (aprobado, monto distinto con devolución, rechazo, orden ajena, doble captura), 10 personas reservando el mismo auto a la vez, un cliente enviando 6 reservas a la vez (límite de 2 pendientes), dos trabajadores confirmando el mismo Yape a la vez, doble clic al pagar, boletas en paralelo sin huecos, reglas en sus bordes, retraso + daños, IGV en 20 000 montos, monto en letras, simulación aleatoria con verificación de coherencia |
| `AdaptadoresExternosTest` | 11 | PayPal y Nubefact contra un servidor que responde como los reales: token, dólares, captura, rechazos, credenciales malas, caídas, reembolso, nota de crédito y recuperación de una boleta ya registrada |
| `SeguridadWebTest` | 6 | Acceso por rol, CSRF, documentos ajenos, inyección de HTML, cuenta desactivada, datos mal formados sin error 500 |
| `FormulariosTest` | 5 | Límites de fechas y anticipo en cada formulario, reglas de registro y de autos iguales a las del servidor, formulario que conserva lo escrito si hay error, ilustración de vehículos, patrones válidos en navegadores actuales |
| `TelefonoPeruTest` | 2 | Celulares y teléfonos peruanos |
| `ArchivoImagenesTest` | 2 | Guardado y lectura de fotos de vehículos |
| `ReportePdfTest` | 1 | PDF de reportes |
| `AutoRentApplicationTests` | 1 | Arranque de Spring Boot |
| `AutoRentIntegracionTest` | 21 | Pantallas, seguridad por rol y flujos con Yape y efectivo (HTTP + JPA sobre H2) |

- `FlujosDeNegocioTest` no usa `@Transactional`: cada operación se guarda de verdad, para que también se
  prueben los pasos posteriores (boleta, reembolso, nota de crédito). Usa un reloj ajustable para
  "adelantar el tiempo" y probar las tareas automáticas sin esperar.
- **Base de datos:** no se borra ni se recrea. Al arrancar, Spring Boot agrega las columnas nuevas
  (`pagos.detalle`, `pagos.procesado_por`, `pagos.observacion`, `devoluciones.codigo_operacion`) y las tablas
  `comprobantes` e `historial_reservas`. Las reservas creadas antes de esta versión no tienen bitácora; sus pagos y
  boletas sí se ven en el detalle. El script completo para una instalación nueva está en `database/autorent_db.sql`.

## Pruebas adicionales realizadas

- **Simulación aleatoria ampliada:** la simulación de `RobustezTest` se ejecutó con 20 semillas distintas
  (unas 8 000 operaciones al azar: reservas, pagos buenos y malos, Yape validados y rechazados, entregas,
  recepciones con daños, cancelaciones, anulaciones, vencimientos y no presentados). En todas se cumplieron
  las reglas de coherencia: nunca se cobra más del total ni se devuelve más de lo cobrado, cada pago confirmado
  tiene exactamente una boleta y cada devolución su nota de crédito, la numeración no tiene huecos, ningún auto
  tiene reservas cruzadas y el historial de cada reserva termina en su estado actual.
  Para repetirla: `-Dautorent.simulacion.semillas=20`.
- **Recorrido en navegador real (Chrome):** 14 pasos del guion de la exposición con cliente, trabajador y
  administrador, verificando cada pantalla, sin errores de JavaScript, más la vista en celular.
- **Código QR de las boletas:** se leyó con un lector de QR y contiene la cadena en el orden de SUNAT
  (RUC | 03 | serie | número | IGV | total | fecha | tipo doc. | DNI | hash).

## Falla de concurrencia encontrada en MySQL y corregida

La prueba de carga sobre la versión publicada (MySQL 9 en Railway) mostró que 10 solicitudes simultáneas
para el mismo auto y fechas creaban 10 reservas. Causa: MySQL usa por defecto REPEATABLE READ, que toma una
"foto" de los datos en la primera lectura de la transacción; la verificación de cruce, hecha después del
candado, leía esa foto vieja. Las pruebas no lo detectaban porque H2 usa READ COMMITTED.

Corrección:
- Los servicios que toman candados usan `@Transactional(isolation = Isolation.READ_COMMITTED)`
  (también configurado en `spring.datasource.hikari.transaction-isolation`).
- Al crear una reserva se bloquea también al cliente, para que el límite de reservas pendientes y la regla
  de deuda no se puedan saltar con solicitudes simultáneas.
- Al tomar un candado se vacía la memoria de JPA, para que todo lo leído después venga de la base de datos.

Las pruebas de concurrencia y la simulación de 20 semillas se ejecutan ahora en los dos modos
(READ COMMITTED y REPEATABLE READ) y pasan en ambos.

## Validaciones en el navegador (revisión de formularios)

Se revisaron todos los formularios para que el navegador solo deje ingresar lo que el servidor acepta
(el servidor sigue validando todo). `static/js/validaciones.js` revisa cada fecha al elegirla, porque
Safari en iPhone no respeta `min`/`max` en su calendario. Comprobado en Chrome: 21 de 21 casos
(fechas pasadas, devolución entre 1 y 30 días, anticipo entre 20 % y 100 %, registro, mantenimiento,
formulario que conserva lo escrito tras un error, ilustraciones del catálogo).
