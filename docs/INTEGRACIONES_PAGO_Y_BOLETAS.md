# Pagos en línea y boletas electrónicas (unidad 2)

AutoRent funciona **sin configurar nada**: usa una pasarela de tarjeta de prueba y emite boletas en
modo de prueba. Si se configuran las credenciales, usa los servicios reales en su modo de pruebas.
Las credenciales van en variables de entorno (o en NetBeans: *Properties* > *Run* > *Environment*),
nunca dentro del código.

## 1. Pagos

| Medio | Cómo funciona | Quién confirma |
|---|---|---|
| Yape | El cliente registra el código de operación | El trabajador, al verificar el Yape |
| Efectivo | El cliente paga en la sucursal | El trabajador, al recibir el dinero |
| Tarjeta Visa/Mastercard | Pasarela de prueba propia (valida Luhn, marca, vencimiento y CVV) | La pasarela, al instante |
| PayPal | PayPal Sandbox real: cuenta PayPal de prueba o tarjeta dentro de PayPal | PayPal, al instante |

### Tarjetas de la pasarela de prueba

| Número | Marca | Resultado |
|---|---|---|
| 4111 1111 1111 1111 | Visa | Aprobada |
| 5555 5555 5555 4444 | Mastercard | Aprobada |
| 4000 0000 0000 0002 | Visa | Rechazada por el banco |
| 4000 0000 0000 9995 | Visa | Fondos insuficientes |
| 5105 1051 0510 5100 | Mastercard | Tarjeta bloqueada |

Cualquier titular, vencimiento futuro (MM/AA) y CVV de 3 dígitos. Con CVV `000` simula un CVV incorrecto.
AutoRent **no guarda** el número de la tarjeta: solo la marca y los últimos 4 dígitos.

### Activar PayPal Sandbox

1. Entrar a **developer.paypal.com** con una cuenta PayPal (se crea gratis).
2. *Apps & Credentials* > pestaña **Sandbox** > *Create App* (tipo *Merchant*).
3. Copiar el **Client ID** y el **Secret** en las variables:
   `PAYPAL_CLIENT_ID` y `PAYPAL_CLIENT_SECRET`.
4. Para pagar en la demo: *Testing Tools* > *Sandbox Accounts* muestra una cuenta **Personal** de prueba
   (correo y contraseña). Con ella se aprueba el pago en la ventana de PayPal; no se mueve dinero real.

PayPal no cobra en soles a comercios peruanos: AutoRent convierte a dólares con `PAYPAL_TIPO_CAMBIO`
(3.75 por defecto) y verifica que PayPal haya cobrado exactamente ese monto.

### Reglas de los pagos en línea

- El pago en línea pendiente se anula si no se completa en **30 minutos**.
- Una reserva web sin anticipo pagado vence a las **24 horas** (o al llegar su fecha de inicio).
  Las que esperan la validación de un Yape no vencen solas: decide el personal.
- Un cliente puede tener como máximo **2 reservas esperando pago**.
- Si se cancela una reserva pagada con tarjeta o PayPal, el **reembolso es automático**.

## 2. Boletas electrónicas

- Cada pago confirmado (anticipo, saldo o cargo por retraso) genera su **boleta**.
- Cada devolución genera una **nota de crédito** que anula esa boleta.
- Montos con IGV incluido: `gravada = total / 1.18`, `IGV = total - gravada`.
- El PDF incluye el importe en letras, el código hash y el código QR con el formato de SUNAT.
- Si un envío falla (por ejemplo, sin internet), el comprobante queda en **ERROR** con su número y se
  reenvía desde *Comprobantes* > *Reintentar*, así no quedan saltos en el correlativo.

### Modo de prueba (sin configurar)

Serie `B001` para boletas y `BC01` para notas de crédito. El PDF dice claramente
**"DOCUMENTO DE PRUEBA - SIN VALOR TRIBUTARIO"**. RUC y razón social son ficticios.

### Activar Nubefact (envío a SUNAT en modo demo)

1. Registrarse gratis en **nubefact.com** (cuenta DEMO).
2. En el panel, opción **API (Integración)**: copiar la **RUTA** y el **TOKEN**.
   Si la opción no aparece, Nubefact la activa con un ticket en ayuda.nubefact.com.
3. Configurar `NUBEFACT_RUTA` y `NUBEFACT_TOKEN`. Si la serie de boletas de la cuenta no es `BBB1`,
   configurar también `NUBEFACT_SERIE_BOLETA` y `NUBEFACT_SERIE_NOTA` (las boletas empiezan con "B").
4. Desde ese momento las boletas se envían a Nubefact; el botón "Ver PDF" abre el PDF oficial de Nubefact.

## 3. Arquitectura (cómo encaja en el hexágono)

```
Controlador ──> PagoEnLineaUseCase ──> PagoEnLineaService ──> PasarelaTarjetaPort ──> PasarelaTarjetaSimuladaAdapter
                                                          └─> PayPalPort ──────────> PayPalSandboxAdapter (API REST)
ReservaService ──(evento PagoConfirmado)──> EventosDeNegocioListener ──> ComprobanteService
                                                                          └─> FacturacionElectronicaPort ──> NubefactAdapter
                                                                                                         └─> FacturacionSimuladaAdapter
```

Cambiar de pasarela (por ejemplo, Culqi o Niubiz) o de proveedor de facturación solo requiere un adaptador
nuevo en `dao`; los servicios no cambian. Los eventos se procesan **después** de guardar el pago, así una
caída de PayPal o de Nubefact nunca deshace un cobro ya registrado.
