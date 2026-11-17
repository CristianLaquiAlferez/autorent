# Guion de la exposición de AutoRent (unos 12 minutos)

## Antes de empezar (10 minutos antes)

1. Encender **MySQL** en XAMPP.
2. En NetBeans: **Clean and Build** (deben salir 95 pruebas, 0 fallos) y luego **Run**.
3. Abrir `http://localhost:8080` y comprobar que entra con `admin` / `Admin123*`.
4. Tener abiertos en NetBeans estos archivos, en este orden:
   `ReglasAlquiler`, `ReservaUseCase`, `ReservaService`, `JpaReservaAdapter`, `SecurityConfig`.
5. Tener a mano la salida de **Clean and Build** con el resultado de las pruebas.

## 1. Problema y solución (1 min) – Cristian

- En la empresa de alquiler, las reservas se anotaban a mano. Había cruces de fechas y los pagos por Yape
  no se podían verificar con facilidad.
- AutoRent es un sistema web con tres roles: **ADMIN**, **TRABAJADOR** y **CLIENTE**.
- Tecnologías: Java 17, Spring Boot, Thymeleaf, MySQL (XAMPP) y arquitectura hexagonal.

## 2. Demo en vivo (7 min)

| Paso | Cuenta | Qué mostrar | Qué decir |
|---|---|---|---|
| 1 | sin sesión | Catálogo en `/autos` | El catálogo es público; solo aparecen los vehículos DISPONIBLES. |
| 2 | `cliente` / `Cliente123*` | Reservar un auto que empiece **dentro de 3 días** (para poder cancelarlo luego), método **Tarjeta** | El sistema calcula el total y exige un anticipo de al menos el 20 %. |
| 3 | `cliente` | En la pantalla de pago, tarjeta `4000 0000 0000 9995` | La pasarela rechaza: "Fondos insuficientes". El pago sigue pendiente. |
| 4 | `cliente` | Pagar con `4111 1111 1111 1111` | Aprobado al instante: la reserva pasa a **CONFIRMADA** sin intervención del personal. |
| 5 | `cliente` | "Mis comprobantes" > **Ver PDF** | Boleta electrónica con IGV, importe en letras, hash y QR en el formato de SUNAT. |
| 6 | `cliente` | **Ver detalle e historial** de la reserva | *Respuesta a la observación de la unidad 1:* aquí está todo el proceso detrás del pago: el intento rechazado, el pago aprobado, quién lo hizo, la boleta y la línea de tiempo. Se puede imprimir. |
| 7 | `cliente` | Cancelar la reserva y volver al detalle | El reembolso a la tarjeta es automático, se emite la **nota de crédito** y todo aparece en el historial. |
| 8 | `trabajador` / `Trabajador123*` | Reserva presencial **para hoy**, anticipo en efectivo, cobrar el saldo y **Entregar** (anotar km y combustible) | No se puede entregar el auto si queda saldo. Cada cobro tiene su boleta. |
| 9 | `trabajador` | **Recibir auto** con un cargo de S/ 50 por "rayón en puerta" | El alquiler queda con deuda; el cliente no puede volver a reservar hasta pagarla. Cobrarla genera otra boleta que explica el cargo. |
| 10 | `trabajador` | Menú **Caja** | Historial de todos los pagos con quién los registró y quién los confirmó, y el total por método para cuadrar caja. |
| 11 | `admin` / `Admin123*` | Reportes > **Descargar PDF** | El PDF se genera sin librerías externas. |
| 12 | (opcional) | Abrir la dirección de Railway en un celular | El sistema está publicado en internet con su base MySQL. |

Si PayPal está configurado, en el paso 4 se puede pagar con la cuenta personal de prueba de PayPal Sandbox.
Si algo falla en vivo, decirlo con calma y pasar al siguiente paso.

## 3. Código (4 min) – Luis

1. **Arquitectura hexagonal.** Recorrer los paquetes:
   `controlador` → `servicio.contrato` (puerto de entrada) → `servicio` → `repositorio` (puerto de salida) → `dao` → MySQL.
   Idea clave: *el servicio no sabe que existe MySQL; solo conoce interfaces.*
2. **`ReglasAlquiler`.** Mostrar el 20 %, la regla de 24 horas y el periodo `[inicio, fin)`. Se prueban con JUnit sin base de datos.
3. **`ReservaService.construirReserva`.** Mostrar el bloqueo pesimista (`findByIdForUpdate`) y el control de cruce de fechas.
4. **`JpaReservaAdapter`.** Mostrar cómo convierte el dominio en entidad JPA (y al revés).
5. **`SecurityConfig`.** Explicar qué rol entra a qué ruta y que las contraseñas se guardan con BCrypt.
6. **Trazabilidad.** Mostrar `BitacoraReservas` y la tabla `historial_reservas`: cada acción se anota en la misma
   transacción (si la acción falla, su anotación también se deshace). Mostrar en `Pago` los campos
   `registradoPor` y `procesadoPor`.
7. **Pagos y boletas.** Mostrar `PasarelaTarjetaPort` y `FacturacionElectronicaPort`: dos adaptadores cada uno
   (prueba y real). Cambiar de proveedor no toca los servicios.
8. **Pruebas.** Mostrar la salida de Clean and Build: **95 pruebas, 0 fallos**.

## 4. Cierre (30 s)

Mostrar qué se logró (reservas sin cruces, pagos verificados, reportes) y qué falta: despliegue en un servidor y notificaciones.

## Preguntas probables

- **¿Por qué arquitectura hexagonal?** Porque las reglas del negocio no dependen de la base de datos ni de la web.
  Si cambiamos MySQL por otra base de datos, solo cambia el paquete `dao`.
- **¿Qué pasa si dos personas reservan el mismo auto al mismo tiempo?** `findByIdForUpdate` bloquea la fila
  (`SELECT ... FOR UPDATE`): la segunda persona espera y luego ve el cruce de fechas.
- **¿Cómo se valida un Yape?** El cliente registra el número y el código de operación. El trabajador lo compara
  con el Yape de la empresa y lo confirma o lo rechaza. Un mismo código no se puede registrar dos veces.
- **¿Dónde se guardan las contraseñas?** En la tabla `usuarios`, cifradas con BCrypt. No se pueden descifrar.
- **¿Por qué el controlador no calcula el saldo?** Porque es una regla del negocio. Está en
  `ReservaService.calcularSaldos`, y el controlador solo la muestra.
- **¿Cómo se genera el PDF?** `ReportePdfGenerator` escribe el formato PDF directamente: hojas, fuentes,
  rectángulos y texto. Por eso no hace falta ninguna librería.
- **¿Funciona en otras PC?** Sí: publicado en Railway con dirección https, o en la misma red con `http://IP-DE-LA-PC:8080`.
- **¿El pago es real?** Es una simulación: la pasarela de tarjeta es de prueba y PayPal está en modo Sandbox
  (su entorno oficial de pruebas). Para cobrar de verdad bastaría un adaptador de una pasarela real.
- **¿La boleta es válida ante SUNAT?** En modo de prueba no, y el PDF lo dice. Con Nubefact configurado,
  se envía a su entorno demo, que valida el formato como lo haría SUNAT.
- **¿Guardan los datos de la tarjeta?** No. Solo la marca y los últimos 4 dígitos.
- **¿Dónde está el historial de un pago o de un alquiler?** En el detalle de cada reserva (cliente y personal):
  pagos con quién los registró y quién los confirmó o rechazó (con motivo), boletas, devoluciones con su código de
  operación y la línea de tiempo completa. El personal tiene además el menú **Caja** con todos los pagos y sus totales.
- **¿Qué pasa si el auto vuelve dañado o tarde?** El retraso se cobra solo por día; los daños se registran con su
  concepto y monto. Queda deuda y el cliente no puede reservar de nuevo hasta pagarla.
- **¿Cómo se prueba que se devolvió el dinero?** La devolución no se puede marcar como completada sin el código de la
  operación (Yape o transferencia). Las de tarjeta y PayPal guardan el código del reembolso de la pasarela.
- **¿Qué pasa si se cae internet al emitir la boleta?** El pago ya quedó guardado; la boleta queda en ERROR
  con su número y se reenvía con "Reintentar".
