# Despliegue en producción (Docker + Caddy + Aiven + Vercel)

Esta guía explica cómo está armado el despliegue y cómo repetirlo desde cero. Los microservicios se empaquetan en imágenes Docker y se levantan con `docker-compose.prod.yml` en un servidor Linux. **Caddy** recibe el tráfico de internet, saca el certificado HTTPS y lo reparte. La base de datos es externa (**Aiven MySQL**) y el frontend vive en **Vercel**. `package-service` queda desactivado (el front no lo usa); hay instrucciones para activarlo dentro del compose.

```
Navegador ─► Vercel (frontend, React)
    │
    └─► https://<tu-subdominio>.duckdns.org ─► Caddy (80/443, HTTPS)
                 ├─ /ws-despescar* ─► reservation-service:8085   (WebSocket de asientos)
                 └─ todo lo demás ──► gateway-service:8087 ─► identity, flight, hotel,
                                                               reservation, payment, koi
                                                                   │
                                                                   ▼
                                                           Aiven MySQL (6 bases)
```

Solo Caddy publica puertos. Los servicios se hablan por nombre dentro de la red de Docker.

## Qué se usó (y por qué importa dónde está cada cosa)

| Pieza | Servicio | Nota |
|---|---|---|
| Frontend | Vercel (plan gratuito) | Variables `VITE_*` en el proyecto |
| Servidor | Oracle Cloud, "Always Free", Ampere A1 (ARM), Ubuntu 24.04 | 4 OCPU y 24 GB alcanzan de sobra; los 7 servicios usan unos 3 a 4 GB |
| Base de datos | Aiven MySQL (plan gratuito) | 6 bases `despescar_*` |
| Dominio | DuckDNS (subdominio gratuito) | Apunta a la IP del servidor |
| HTTPS | Let's Encrypt, automático con Caddy | Sin cuenta ni configuración |

**El servidor y la base de datos tienen que estar en regiones cercanas.** Cada consulta SQL es un viaje de ida y vuelta, y una sola petición hace varias. Con Aiven en California y el back en Argentina (200 a 400 ms por viaje) un login tardaba 5 segundos y el gateway cortaba las reservas por timeout. Con el servidor en Phoenix la latencia es de unos 20 ms y todo responde bien. La región de Oracle se elige al crear la cuenta y no se puede cambiar después.

## Archivos

| Archivo | Para qué sirve |
|---|---|
| `Dockerfile` | Imagen genérica: `--build-arg SERVICE=<carpeta de services/>`. Compila también `common-security` |
| `docker-compose.prod.yml` | Los servicios y Caddy, con las variables de cada uno |
| `deploy/Caddyfile` | Rutas de Caddy y HTTPS automático |
| `.env.example` | Plantilla del `.env` (el `.env` real no se sube a git) |
| `database/seed/seed.py` | Datos de ejemplo (aeropuertos, vuelos, hoteles, usuarios) |

## Variables de entorno

Todas tienen un valor por defecto pensado para desarrollo local, así que sin definirlas todo sigue funcionando como antes. En producción se definen en el `.env` (ver `.env.example`).

| Variable | Servicios | Por defecto | Para qué sirve |
|---|---|---|---|
| `DOMAIN` | Caddy, payment | — | Subdominio público (por ejemplo `miapp.duckdns.org`) |
| `FRONTEND_URL` | gateway, koi, payment | — | URL del frontend en Vercel, **sin barra final** (CORS y URLs de retorno de pagos) |
| `DB_HOST`, `DB_PORT` | los 6 con base | `localhost`, `3306` | Dónde está MySQL |
| `DB_USER`, `DB_PASSWORD` | los 6 con base | `root`, `despescar_dev` | Credenciales de MySQL |
| `DB_SSL_MODE` | los 6 con base | `PREFERRED` | Aiven exige `REQUIRED` |
| `DB_POOL_SIZE` | los 6 con base | `10` | Conexiones por servicio (en Aiven gratis, `3`) |
| `JPA_SHOW_SQL` | los 6 con base | `true` | Mostrar SQL en los logs (el compose lo pone en `false`) |
| `*_SERVICE_URL` | gateway, reservation, payment, koi | `localhost` + su puerto | Dirección de cada servicio (en el compose, su nombre de contenedor) |
| `CORS_ALLOWED_ORIGINS` | gateway, koi | `*` (gateway), `http://localhost:5173` (koi) | Origen del front permitido (el compose usa `FRONTEND_URL`) |
| `GATEWAY_TRUSTED_PROXIES` | gateway | vacío | IP de Caddy: el gateway solo acepta `X-Forwarded-For` de esas IPs (ver más abajo) |
| `JWT_SECRET`, `INVENTORY_SERVICE_TOKEN`, `RESERVATION_SERVICE_SYNC_TOKEN`, `PAYMENT_SERVICE_SYNC_TOKEN` | varios | sin valor | Secretos: valores propios y largos (`openssl rand -base64 48`) |
| `PAYMENT_PROVIDER` | payment | `mock` | `mock` = pago simulado desde el front; `mercadopago_orders` = tarjeta en `/pago/mercadopago` (Checkout API); `mercadopago` = Checkout Pro (redirige a Mercado Pago). Es global: un solo modo a la vez |
| `MERCADOPAGO_ACCESS_TOKEN`, `MERCADOPAGO_PUBLIC_KEY` | payment | vacío | Credenciales de Mercado Pago. Con `mercadopago_orders` el servicio **no arranca** sin las dos. La public key se entrega al front por `GET /api/payments/config` (no va en Vercel) |
| `MERCADOPAGO_WEBHOOK_SECRET` | payment | vacío | Secreto del webhook; lo genera Mercado Pago al registrarlo (ver Pagos) |
| `GOOGLE_CLIENT_ID` | identity | vacío | Client id OAuth de tipo Web (público). Vacío = el botón de Google queda deshabilitado. El front lo recibe por `GET /api/auth/google/config` |
| `GROQ_API_KEY` | koi | `sin-clave` | Sin clave KOI arranca, pero responde que el modelo no está disponible |

### Por qué Caddy tiene una IP fija

El gateway limita las peticiones por IP del cliente y, para eso, lee `X-Forwarded-For` **solo si** la petición viene de una IP listada en `GATEWAY_TRUSTED_PROXIES` (comparación exacta, no admite rangos). Detrás de Caddy, sin esto todos los usuarios compartirían el mismo límite de 120 peticiones por minuto. Como la IP de un contenedor cambia, el compose fija una subred (`172.28.0.0/16`) y le da a Caddy la IP `172.28.0.10`, que es la que lleva el gateway. Si cambias esa subred, actualiza las dos cosas.

## Paso a paso

### 1. Aiven

1. Crea el servicio MySQL y anota host, puerto y usuario (`avnadmin`) del panel *Connection information*.
2. **Desactiva `mysql.sql_require_primary_key`** (*Service settings → Advanced configuration*). Aiven lo trae activado y rechaza cualquier tabla sin clave primaria. Hibernate lo registra como aviso y sigue arrancando, pero deja sin crear dos tablas intermedias: `flight_fares` (vuelos y tarifas) y `reservation_flights` (vuelos de cada reserva). Los vuelos saldrían sin tarifas y no se podrían guardar las compras.
3. Crea las 6 bases (no hace falta `despescar_package`):
   ```bash
   mysql -h <host> -P <puerto> -u avnadmin -p --ssl-mode=REQUIRED -e "
     CREATE DATABASE IF NOT EXISTS despescar_identity;
     CREATE DATABASE IF NOT EXISTS despescar_flight;
     CREATE DATABASE IF NOT EXISTS despescar_hotel;
     CREATE DATABASE IF NOT EXISTS despescar_reservation;
     CREATE DATABASE IF NOT EXISTS despescar_payment;
     CREATE DATABASE IF NOT EXISTS despescar_koiia;"
   ```
   Sin cliente `mysql` instalado, se puede usar `docker run --rm -it mysql:8.0 mysql ...` con los mismos argumentos.
4. Si el servicio tiene lista de IPs permitidas, agrega la IP del servidor.
5. Las tablas las crea cada servicio al arrancar (`ddl-auto=update`).

### 2. Servidor (Oracle Cloud)

1. **Instancia:** forma `VM.Standard.A1.Flex` (Ampere), Ubuntu 22.04 o 24.04. Si dice "Out of capacity", prueba otro *Availability Domain*, una forma más chica (2 OCPU y 12 GB alcanzan) o reintenta más tarde.
2. **Red:** subred **pública** y con IP pública asignada. Una subred privada no admite IP pública. Si la instancia quedó sin IP, se asigna en *Attached VNICs → IPv4 Addresses → Edit*.
3. **Security List de la red de Oracle:** agrega reglas de entrada TCP desde `0.0.0.0/0` para los puertos **80** y **443** (el 22 de SSH ya viene abierto).
4. **Firewall interno de Ubuntu:** las imágenes de Oracle traen `iptables` con un `REJECT` final, así que aunque abras los puertos en el panel siguen cerrados. Hay que insertar las reglas **antes** del `REJECT` y guardarlas:
   ```bash
   sudo iptables -I INPUT 5 -p tcp -m state --state NEW --dport 80  -j ACCEPT
   sudo iptables -I INPUT 6 -p tcp -m state --state NEW --dport 443 -j ACCEPT
   sudo netfilter-persistent save
   ```
   Se comprueba desde fuera con una conexión TCP a esos puertos: "rechazada" significa abierto sin nadie escuchando; que se cuelgue (timeout) significa que algo los bloquea.
5. **Docker:** instalar Docker Engine y el plugin Compose desde el repositorio oficial de Docker (`docs.docker.com/engine/install/ubuntu`). Queda habilitado al arrancar.
6. **Código:** `git clone -b <rama> <url del repo> despescar`.

### 3. DuckDNS y `.env`

1. En duckdns.org crea el subdominio y pon la IP pública del servidor.
2. En el servidor, `cp .env.example .env` y completa los valores. Los secretos se generan con `openssl rand -base64 48`. **Verifica que el subdominio ya resuelve a la IP del servidor antes de levantar Caddy**: si arranca con el dominio mal apuntado, Let's Encrypt falla y, si se acumulan reintentos, puede bloquear el dominio por un rato.

### 4. Levantar

```bash
cd ~/despescar
sudo docker compose -f docker-compose.prod.yml build      # la primera vez tarda varios minutos (en ARM, más)
sudo docker compose -f docker-compose.prod.yml up -d
sudo docker compose -f docker-compose.prod.yml ps
```

Caddy obtiene el certificado solo en unos segundos (se ve en `logs caddy`: `certificate obtained successfully`). Los servicios Java tardan 1 a 2 minutos en responder. Comprobación:

```bash
curl https://<tu-subdominio>.duckdns.org/api/airports      # 200 y una lista (vacía hasta cargar el seed)
```

### 5. Datos de ejemplo (seed)

El seed llama a los servicios por HTTP y asigna el rol `SUPER_ADMIN` con un `INSERT` en la base de identidad. Se corre **en el servidor**, que está cerca de Aiven (desde otra región es muy lento). Hace falta `python3` y el cliente `mysql` (`sudo apt install mysql-client-core-8.0`). Los servicios no publican puertos, así que se usan las IP internas de los contenedores:

```bash
cd ~/despescar
ip() { sudo docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' despescar-$1-1; }
set -a; . ./.env; set +a
export IDENTITY_URL=http://$(ip identity-service):8080 \
       FLIGHT_URL=http://$(ip flightservice):8081 \
       HOTEL_URL=http://$(ip hotel-service):8083 \
       MYSQL_LOCAL=1                                     # usa DB_HOST, DB_PORT, DB_USER, DB_PASSWORD y DB_SSL_MODE del .env
python3 -u database/seed/seed.py --sin-paquetes --completar-rutas
```

- `--sin-paquetes`: carga vuelos y hoteles sin exigir `package-service`.
- `--completar-rutas`: si ya hay aeropuertos, agrega los vuelos que falten (no duplica).
- `SEED_DIAS=N`: solo los próximos N días de vuelos. Por defecto carga hasta fin de mes (mínimo 14 días), unos 4.000 vuelos; en el servidor tarda pocos minutos.
- **Los vuelos se acaban a fin de mes:** volver a correr el seed los extiende.
- **Usuarios:** por defecto el seed crea un administrador y un cliente con contraseñas de ejemplo que están en el repositorio. **En producción define contraseñas propias** antes de correrlo: `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `CLIENT_EMAIL` y `CLIENT_PASSWORD` (variables de entorno).
- El token de acceso dura 15 minutos: el seed renueva la sesión solo si vence a mitad de la carga.

### 6. Frontend en Vercel

1. Importa el repositorio del front y elige la rama de producción (si no es la rama por defecto del repo, cámbiala en *Settings → Git*).
2. *Framework Preset*: Vite. *Build Command*: `pnpm build`. *Output Directory*: `dist`.
3. Variables de entorno, **antes** del primer despliegue (Vite las incorpora al compilar; si las cambias hay que volver a desplegar):
   - `VITE_GATEWAY_URL` = `https://<tu-subdominio>.duckdns.org`
   - `VITE_WS_URL` = `wss://<tu-subdominio>.duckdns.org/ws-despescar`
4. El `vercel.json` del front reenvía todas las rutas a `index.html`; sin él, recargar `/carrito` o volver de Mercado Pago a `/pago/resultado` daría 404.
5. Con la URL que te dé Vercel, ponla en `FRONTEND_URL` del `.env` del servidor y recrea los servicios que la usan:
   ```bash
   sudo docker compose -f docker-compose.prod.yml up -d gateway-service payment-service koi-ia-service
   ```
   Sin esto el navegador bloquea las llamadas del front por CORS.

## Operación

```bash
cd ~/despescar
sudo docker compose -f docker-compose.prod.yml ps                       # estado
sudo docker compose -f docker-compose.prod.yml logs -f gateway-service  # logs de un servicio
sudo docker compose -f docker-compose.prod.yml restart reservation-service

# Actualizar el código
git pull
sudo docker compose -f docker-compose.prod.yml up -d --build
```

- Todos los servicios tienen `restart: unless-stopped` y Docker arranca con el servidor, así que se levantan solos tras un reinicio.
- No borres el volumen `caddy_data`: guarda los certificados.
- Cambiar una variable del `.env` requiere `up -d <servicio>` para que se recree; `restart` no la relee.

## Pagos

Hay tres modos, elegidos con `PAYMENT_PROVIDER` (un solo modo a la vez para todo el sitio):

| Modo | Qué hace |
|---|---|
| `mock` (por defecto) | El pago se aprueba o rechaza desde una página simulada del front. No se cobra nada |
| `mercadopago_orders` | Tarjeta en una página propia (`/pago/mercadopago`) con el formulario oficial de Mercado Pago (Checkout API). Los datos de la tarjeta nunca pasan por el servidor |
| `mercadopago` | Checkout Pro: redirige a Mercado Pago |

### Activar `mercadopago_orders` con credenciales de prueba

Con credenciales de **prueba** no se mueve dinero real: se paga con tarjetas de prueba. La cuenta tiene que ser de Argentina (el sitio cobra solo en ARS).

1. **Cambiar el tipo de las columnas de `payments` (una sola vez, solo si la base ya existía).** Hibernate crea las columnas como `ENUM` de MySQL y `ddl-auto=update` no cambia el tipo de las que ya existen. Sin este paso, crear un pago con el proveedor nuevo falla con `Data truncated`. Una base creada desde cero con esta versión ya viene bien.
   ```sql
   ALTER TABLE despescar_payment.payments
     MODIFY provider       VARCHAR(40) NOT NULL,
     MODIFY status         VARCHAR(40) NOT NULL,
     MODIFY payment_method VARCHAR(40) NULL;
   ```
   (Revisa antes con `SHOW CREATE TABLE payments` cuáles son nulables.)
2. **Credenciales en el `.env` del servidor:** `MERCADOPAGO_ACCESS_TOKEN` y `MERCADOPAGO_PUBLIC_KEY` (de la misma aplicación de prueba) y `PAYMENT_PROVIDER=mercadopago_orders`. Nunca se suben a git.
3. **Recrear el servicio:** `sudo docker compose -f docker-compose.prod.yml up -d payment-service`. Si las credenciales faltan o no corresponden, el servicio no arranca y se ve en `logs payment-service`.
4. **Webhook (recomendado):** en el panel de Mercado Pago, *Webhooks → Configurar notificaciones*, con el evento **Orders** y la URL `https://<tu-subdominio>.duckdns.org/api/payments/mercadopago/webhook`. Al guardar se genera el secreto, que va en `MERCADOPAGO_WEBHOOK_SECRET`. Sin webhook, un pago aprobado se resuelve igual en la misma llamada, pero uno que queda "en proceso" solo se resuelve con la conciliación desde `/pago/resultado`.

### Cómo se prueba

En `/pago/mercadopago`:
- **Tarjeta:** Mastercard `5031 7557 3453 0604`, código `123`, vencimiento `11/30`, DNI `12345678`. Hay más en la lista oficial de tarjetas de prueba de Mercado Pago.
- **Nombre del titular:** decide el resultado. `APRO` aprobado, `FUND` fondos insuficientes, `CALL` rechazo con validación, `SECU` código inválido, `OTHE` rechazo general y `CONT` queda pendiente.
- **Correo del pagador:** con credenciales de prueba tiene que ser el de la **cuenta de prueba compradora** (termina en `@testuser.com`). Con otro correo, Mercado Pago responde `invalid_email_for_sandbox`.

Los reembolsos (cancelar una reserva, un pago en grupo o una reserva que el sistema rechaza) van a `POST /v1/orders/{id}/refund`, total o parcial.

### Volver al pago simulado

`PAYMENT_PROVIDER=mock` y recrear `payment-service`. Los pagos pendientes creados con otro proveedor no se pueden completar con el nuevo: hay que recrear el carrito.

## Ingreso con Google

El login y el registro muestran el botón oficial de Google cuando identity-service tiene `GOOGLE_CLIENT_ID`. El front manda el ID token a `POST /api/auth/google`; el servicio lo verifica contra Google (destinatario y correo verificado) y busca al usuario por correo o lo crea con rol `USER`.

1. En Google Cloud Console, crear un *ID de cliente de OAuth* de tipo **Aplicación web**.
2. En *Orígenes de JavaScript autorizados* agregar la URL del front (`https://<tu-proyecto>.vercel.app`) y, para desarrollo, `http://localhost:5173`. Solo funciona desde los orígenes registrados: las direcciones temporales de despliegue de Vercel no sirven.
3. Pantalla de consentimiento: en modo *Pruebas* solo ingresan los correos agregados como usuarios de prueba; para el resto hay que publicarla.
4. Poner el client id en `GOOGLE_CLIENT_ID` del `.env` y recrear `identity-service`. El *client secret* no se usa en este flujo: no hace falta compartirlo ni guardarlo.

## Problemas comunes

| Síntoma | Causa y solución |
|---|---|
| Faltan las tablas `flight_fares` o `reservation_flights`; vuelos sin tarifas o las compras no se guardan | Aiven tiene `sql_require_primary_key` activo: desactívalo y reinicia flightservice y reservation-service |
| Respuestas `503` con `/fallback/unavailable` | El gateway corta a los 5 segundos. Casi siempre es latencia alta hacia la base de datos (servidor lejos de Aiven) o un servicio todavía arrancando |
| `Communications link failure` / `UnknownHostException` al arrancar | DNS o red momentáneos al conectar con Aiven; el reinicio automático lo resuelve. Si persiste, revisa que el servicio de Aiven esté encendido y la lista de IPs permitidas |
| Caddy no consigue el certificado | El subdominio no apunta a la IP, o los puertos 80/443 están cerrados (Security List de Oracle o `iptables`) |
| Cancelar una reserva segundos después de pagarla deja el reembolso "pendiente de hacer a mano" | Mercado Pago (Orders) rechaza con 422 `Post processing rejected the operation` un reembolso pedido en los primeros segundos tras el cobro, y lo acepta pasados unos 10 segundos. Es raro fuera de las pruebas; si pasa, se reintenta el reembolso manualmente desde el panel de Mercado Pago |
| Un pago rechazado devolvía 502 `no devolvio el id de la orden` | Corregido: Mercado Pago anida la orden dentro de `data` en el 402 de rechazo (`leerOrden` la toma de ahí) |
| La primera consulta del mapa de asientos de un vuelo da 503 y la siguiente funciona | Los asientos se crean en la primera consulta y, con la base remota, tarda más de los 5 s que espera el gateway. Se resuelve consultando de nuevo; para evitarlo hay que pre-generar los asientos de los vuelos o agrupar los inserts |
| Crear el pago falla con `Data truncated` | Las columnas de `payments` siguen siendo `ENUM` de MySQL: aplicar el `ALTER TABLE` de la sección Pagos |
| `payment-service` no arranca con `mercadopago_orders` | Falta `MERCADOPAGO_ACCESS_TOKEN` o `MERCADOPAGO_PUBLIC_KEY` (o no son de la misma aplicación): ver `logs payment-service` |
| `502` al pagar con `invalid_email_for_sandbox` | Con credenciales de prueba el correo del pagador debe ser el de la cuenta de prueba compradora (`@testuser.com`) |
| El botón de Google no aparece o da error de origen | `GOOGLE_CLIENT_ID` vacío, o el origen del front no figura en *Orígenes de JavaScript autorizados* de Google Cloud |
| El front carga pero el login o los vuelos fallan | CORS: `FRONTEND_URL` no coincide con la URL de Vercel (sin barra final) o no se recrearon gateway y koi |
| El límite de peticiones afecta a todos los usuarios a la vez | `GATEWAY_TRUSTED_PROXIES` no coincide con la IP de Caddy |
| El seed falla con `401` a mitad de la carga | Token vencido en una versión vieja del seed; actualiza el repo (ahora renueva el token) |
| El seed es muy lento | Se está corriendo desde un equipo lejos de Aiven; córrelo en el servidor |
| Avisos de `locale` al instalar paquetes por SSH | Inofensivos: vienen de las variables de idioma del cliente SSH |

## Seguridad

- El `.env` nunca se sube a git (está en el `.gitignore`) y debe tener permisos `600`.
- Usa secretos propios y largos. Las contraseñas de ejemplo de las guías son solo para desarrollo local.
- El acceso al servidor es solo por clave SSH. Guarda la clave privada fuera del repositorio.
- Las credenciales de Aiven, Groq y Mercado Pago son personales: no se comparten ni se suben.
