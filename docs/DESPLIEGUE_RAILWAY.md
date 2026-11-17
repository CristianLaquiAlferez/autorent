# Publicar AutoRent en Railway

Railway ejecuta la aplicación (Spring Boot) y la base de datos MySQL en la nube, con una dirección pública
`https://...up.railway.app` que se puede abrir desde cualquier celular o PC.

**Costo:** una cuenta nueva recibe un crédito de prueba único de 5 dólares por 30 días, suficiente para la
presentación. Después baja al plan gratuito (muy limitado) o al plan Hobby (5 dólares al mes).
Revisen las condiciones vigentes en railway.com/pricing antes de empezar.

## 1. Subir el proyecto a GitHub

1. Crear un repositorio vacío en github.com (por ejemplo `autorent`), sin README, .gitignore ni licencia.
2. Subir la carpeta `AutoRent` (la que tiene `pom.xml` y `Dockerfile`). Desde NetBeans:
   clic derecho en el proyecto > *Versioning* > *Initialize Git Repository*, luego *Git* > *Commit*
   y *Remote* > *Push* a la dirección del repositorio. También sirve GitHub Desktop.
3. El archivo `.gitignore` ya evita subir la carpeta `target`.

## 2. Crear el proyecto en Railway

1. Entrar a railway.com con la cuenta de GitHub.
2. *New Project* > *Deploy from GitHub repo* > elegir `autorent`.
   Railway detecta el `Dockerfile` y compila solo.
3. En el mismo proyecto: *+ New* > *Database* > *MySQL*.

## 3. Variables del servicio AutoRent

En el servicio de la aplicación > pestaña *Variables* > *Raw Editor*, pegar (las `${{...}}` son referencias
que Railway reemplaza por los datos reales de su MySQL):

```
DB_URL=jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Lima
DB_USERNAME=${{MySQL.MYSQLUSER}}
DB_PASSWORD=${{MySQL.MYSQLPASSWORD}}
THYMELEAF_CACHE=true
AUTORENT_ZONA_HORARIA=America/Lima
PORT=8080
CLAVE_ADMIN=pongan-una-clave-propia-1
CLAVE_TRABAJADOR=pongan-una-clave-propia-2
CLAVE_CLIENTE=Cliente123*
IMAGENES_DIRECTORIO=/data/imagenes
```

- Si el servicio de base de datos tiene otro nombre (no "MySQL"), cambien `MySQL` en las referencias.
- `CLAVE_ADMIN` y `CLAVE_TRABAJADOR`: en internet cualquiera podría probar las contraseñas de ejemplo.
  La del cliente de demostración puede quedarse para la expo.
- Opcionales: `PAYPAL_CLIENT_ID`, `PAYPAL_CLIENT_SECRET`, `NUBEFACT_RUTA`, `NUBEFACT_TOKEN`
  (ver `INTEGRACIONES_PAGO_Y_BOLETAS.md`).

## 4. Volumen para las fotos de los autos

El disco de un contenedor se borra en cada despliegue. Para conservar las fotos subidas:
servicio AutoRent > *Settings* > *Volumes* > *Add Volume* con *Mount path* `/data`.

## 5. Dirección pública

Servicio AutoRent > *Settings* > *Networking* > *Generate Domain*, con el puerto **8080**
(el mismo de la variable `PORT`). Railway entrega una dirección como
`https://autorent-production.up.railway.app`. La primera vez la aplicación tarda 1 o 2 minutos en arrancar
(crea las tablas y los datos de demostración).

## 6. Comprobar

- Abrir la dirección y entrar con `admin` y la clave de `CLAVE_ADMIN`.
- En *Deployments* > *View logs* debe verse `Started AutoRentApplication`.
- Si aparece `Communications link failure`, revisar las tres variables `DB_*`.

## Detalles técnicos que ya están resueltos en el código

| Problema típico en la nube | Solución en AutoRent |
|---|---|
| El servidor está en hora UTC (5 horas adelante) | Reloj del negocio fijo en `America/Lima` (`ConfiguracionGeneral`) |
| Railway asigna el puerto con la variable `PORT` | `server.port=${PORT:8080}` |
| Railway entrega HTTPS a través de un proxy | `server.forward-headers-strategy=framework` |
| Contraseñas y claves en el código | Todo se lee de variables de entorno |
| MySQL nuevo crea columnas `ENUM` que luego no aceptan valores nuevos | Las columnas de estados se crean como `VARCHAR` |
| Fotos perdidas en cada despliegue | Carpeta configurable apuntando al volumen `/data` |

## Despliegue actual

- Repositorio: `github.com/CristianLaquiAlferez/autorent` (cada `push` a `main` vuelve a desplegar solo).
- Dirección pública: https://autorent-production-5aa64c61.up.railway.app
- Servicios en Railway: `autorent` (con volumen en `/data`) y `MySQL` (con su propio volumen).
- Las claves reales de `admin` y `trabajador` están solo en las variables de Railway, no en este repositorio.
