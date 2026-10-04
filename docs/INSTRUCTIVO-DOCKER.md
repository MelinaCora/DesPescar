# Instructivo: levantar el backend de DesPescar con Docker

Con Docker corre **solo la base de datos** (MySQL y Adminer). Los microservicios se arrancan con Maven, como siempre, y se conectan a ese MySQL. Así nadie tiene que instalar MySQL ni usar una contraseña distinta: todos usan la misma, `despescar_dev`.

> Todo lo de este documento es **para desarrollo local**. Las credenciales de ejemplo no sirven para producción.

## Qué vas a tener al final

| Qué | Dónde | Cómo se levanta |
|---|---|---|
| MySQL 8 (7 bases `despescar_*`) | `localhost:3306` | Docker |
| Adminer (ver las tablas desde el navegador) | http://localhost:8090 | Docker |
| `identity-service` | 8080 | Maven |
| `flightservice` | 8081 | Maven |
| `hotel-service` | 8083 | Maven |
| `payment-service` | 8084 | Maven |
| `reservation-service` | 8085 | Maven |
| `package-service` | 8086 | Maven |
| `gateway-service` (entrada del frontend) | 8087 | Maven |
| `koi-ia-service` (chatbot) | 8088 | Maven |

## Requisitos

- **Docker** con Compose v2 (`docker compose version` tiene que responder). La guía de instalación está en [`database/README.md`](../database/README.md).
- **Java 17 o superior** y **Maven 3.8 o superior** (`java -version`, `mvn -v`).
- **Python 3** (solo para cargar los datos de ejemplo).
- **RAM**: se recomiendan 8 GB. Con menos, arranca solo los servicios que necesites (ver "Problemas comunes").
- Puertos libres: 3306, 8080, 8081, 8083, 8084, 8085, 8086, 8087, 8088 y 8090.

## 1. Clonar el repositorio

```bash
git clone https://github.com/MelinaCora/DesPescar.git
cd DesPescar
git switch features/full-integration-docker-setup
```

## 2. Levantar MySQL y Adminer

```bash
cd database
docker compose up -d
docker compose ps
```

Esperá a que `despescar-mysql` diga **healthy**. La primera vez descarga las imágenes e inicializa la base: en un equipo lento puede tardar **hasta 3 minutos**, y `docker compose up -d` espera a que termine antes de levantar Adminer. Es normal; no lo canceles. En el primer arranque se crean solas las 7 bases de datos. Las tablas las crea Hibernate cuando arranca cada microservicio.

- No hace falta crear ningún archivo `.env`: la contraseña de `root` es `despescar_dev` por defecto, tanto en Docker como en los `application.properties`.
- Para mirar las tablas: http://localhost:8090 → Sistema **MySQL**, servidor `mysql`, usuario `root`, contraseña `despescar_dev`.

## 3. Variables de entorno

Definilas en **cada terminal** donde vayas a arrancar un servicio (o ponelas en tu `~/.bashrc`):

```bash
export JWT_SECRET='despescar-dev-secret-key-2026-must-be-long-enough'
export RESERVATION_SERVICE_SYNC_TOKEN='despescar-dev-sync-token'   # reservation-service y payment-service, el mismo valor en los dos
export GROQ_API_KEY='tu-clave'                                      # solo koi-ia-service (chatbot); sacala gratis en https://console.groq.com/home
export MERCADOPAGO_ACCESS_TOKEN='TEST-...'                          # solo para pagos reales de prueba (credencial de prueba de MercadoPago)
```

| Variable | La necesita | Notas |
|---|---|---|
| `JWT_SECRET` | identity, flight, reservation, package, payment, gateway | **Tiene que ser exactamente la misma en todos**, si no se rechazan los tokens entre servicios. `hotel-service` tiene un valor por defecto. |
| `RESERVATION_SERVICE_SYNC_TOKEN` | reservation-service y payment-service | **Imprescindible para pagar.** Es la contraseña con la que payment-service le habla a reservation-service (ver abajo). **El mismo valor en los dos.** Si queda vacía, los servicios arrancan igual, pero crear un pago falla con un error 502. |
| `GROQ_API_KEY` | koi-ia-service | Obligatoria para que arranque. Sin una clave real el chatbot no responde con IA. Cómo conseguirla y levantar el chatbot: [`INSTRUCTIVO-CHATBOT-GROQ.md`](INSTRUCTIVO-CHATBOT-GROQ.md). |
| `MERCADOPAGO_ACCESS_TOKEN` | payment-service | Opcional para arrancar. Sin una credencial de prueba de MercadoPago no se puede generar el enlace de pago. El resto de las variables `MERCADOPAGO_*` (URLs de retorno, webhook) también son opcionales. |
| `DB_PASSWORD` | todos | **Solo** si cambiaste la contraseña de MySQL. Si no la tocaste, no la definas. |

### Qué es `RESERVATION_SERVICE_SYNC_TOKEN`

`payment-service` necesita hablar con `reservation-service` sin que haya un usuario de por medio: para pedirle los datos y el importe de la reserva al crear un pago, y para avisarle que MercadoPago confirmó el pago. Esas dos llamadas van a rutas internas (`/api/bookings/internal/...`) y se identifican con el encabezado `X-Internal-Service-Token`, cuyo valor es este token. Si no coincide en los dos servicios, reservas rechaza la llamada (403) y `POST /api/payments` responde `502 Reservation-Service rechazo la consulta de la reserva`. No tiene relación con `JWT_SECRET`, que firma los tokens de los usuarios.

Las rutas internas **no se pueden usar a través del gateway** (devuelve 403): los servicios se hablan directo por su puerto.

## 4. Arrancar los microservicios

Cada uno en **su propia terminal**, desde la raíz del repositorio. Orden recomendado (el gateway al final):

```bash
cd services/identity-service    && mvn spring-boot:run
cd services/flightservice       && mvn spring-boot:run
cd services/hotel-service       && mvn spring-boot:run
cd services/package-service     && mvn spring-boot:run
cd services/reservation-service && mvn spring-boot:run
cd services/payment-service     && mvn spring-boot:run
cd services/koi-ia-service      && mvn -Dmaven.test.skip=true spring-boot:run
cd services/gateway-service     && mvn spring-boot:run
```

- Un servicio está listo cuando el log dice `Started ...Application in ... seconds`. El primer arranque tarda varios minutos porque descarga dependencias.
- Se usa `mvn` y no `./mvnw` porque en algunos sistemas los `mvnw` no tienen permiso de ejecución y `gateway-service` y `koi-ia-service` no traen wrapper.
- `koi-ia-service` se arranca con `-Dmaven.test.skip=true` porque `KoiAiAssistantTest` está desactualizado y no compila.
- Para buscar vuelos y ver datos alcanzan `identity`, `flightservice`, `hotel`, `package` y el `gateway`. Para **reservar** hace falta además `reservation-service`, y para **pagar**, `payment-service` con las variables de la sección 3. `koi-ia` (chatbot) es opcional: ver [`INSTRUCTIVO-CHATBOT-GROQ.md`](INSTRUCTIVO-CHATBOT-GROQ.md).

## 5. Cargar datos de ejemplo

El proyecto arranca con las bases vacías: sin aeropuertos no hay nada que elegir en el buscador. Con `identity`, `flightservice`, `hotel-service` y `package-service` en marcha:

```bash
./database/seed/seed.sh
```

Crea aeropuertos (EZE, AEP, COR, MDZ, BRC, SLA, SCL, MIA, MAD), 3 aerolíneas, 2 tarifas (Light y Standard), 54 vuelos, 6 hoteles, 5 paquetes y dos usuarios. Detalles:

- **Se puede correr más de una vez**: si ya hay datos, no los duplica.
- **Las fechas son relativas a hoy**: los vuelos salen 14, 17 y 21 días después de la fecha en que corras el script. Al final te imprime esas fechas; usalas en el buscador.
- Solo existen vuelos entre los pares de aeropuertos cargados (por ejemplo EZE ↔ COR, AEP ↔ BRC, EZE ↔ MAD).

Usuarios de ejemplo:

| Correo | Contraseña | Rol |
|---|---|---|
| `admin@despescar.com` | `Admin2026!` | `SUPER_ADMIN` |
| `cliente@despescar.com` | `Cliente123!` | `USER` |

### Usuario administrador (si el script no pudo asignar el rol)

El script le da el rol `SUPER_ADMIN` al administrador con `docker exec`. Si falla, hacelo a mano y volvé a correr el script:

```bash
docker exec -e MYSQL_PWD=despescar_dev despescar-mysql mysql -uroot -e "
INSERT INTO despescar_identity.user_roles (user_id, role_id, assigned_at)
SELECT u.id, r.id, NOW(6) FROM despescar_identity.users u
JOIN despescar_identity.roles r ON r.name = 'SUPER_ADMIN'
WHERE u.email = 'admin@despescar.com';"
```

## 6. Frontend

Repositorio `TenSF25/DesPescar`, rama `features/merge-koi-gateway`:

```bash
pnpm install
pnpm dev
```

Abrilo en http://localhost:5173. Apunta por defecto al gateway en `http://localhost:8087`; si el tuyo está en otro lado, copiá `.env.example` a `.env` y cambiá `VITE_GATEWAY_URL`.

## 7. Probar que funciona

1. Entrá a http://localhost:5173/vuelos **sin iniciar sesión**: el buscador tiene que listar aeropuertos de origen y destino.
2. Buscá EZE → COR en una de las fechas que imprimió el script (en ida y vuelta, la fecha de vuelta tiene que ser otra de esas fechas).
3. Elegí un vuelo: te pide iniciar sesión (`cliente@despescar.com`) y vuelve a la compra.

### Compatibilidad con el frontend

Esta versión del backend está pensada para el frontend `TenSF25/DesPescar`, rama **`features/merge-koi-gateway`**. Se revisó, leyendo el código, que estas llamadas del frontend coinciden con el backend:

| Qué hace el frontend | Llamada | Servicio |
|---|---|---|
| Registro, login y renovar token | `/api/auth/register`, `/login`, `/refresh`; `/api/users/me` | identity |
| Aeropuertos y búsqueda de vuelos | `/api/airports`, `/api/flights/search`, `/api/flights/{id}` (públicas) | flightservice |
| Mapa de asientos | `/api/bookings/flights/{id}/seats` y `/seat-map` | reservation |
| Asientos en tiempo real | WebSocket `ws://localhost:8085/ws-despescar` | reservation |
| Crear reserva | `POST /api/bookings/init` | reservation |
| Cargar pasajeros | `PUT /api/bookings/{id}/passengers` | reservation |
| Pagar | `POST /api/payments` con `{reservationId}` y redirección al `checkoutUrl` de MercadoPago | payment |
| Chatbot | `/api/koi/sessions` y `/api/koi/sessions/{id}/messages` | koi-ia |

Aclaraciones:

- El pago redirige a MercadoPago. Para completarlo en local hace falta una credencial de prueba de MercadoPago (`MERCADOPAGO_ACCESS_TOKEN`) y, para volver al sitio, las variables `MERCADOPAGO_SUCCESS_URL`, `MERCADOPAGO_PENDING_URL` y `MERCADOPAGO_FAILURE_URL`. Sin eso el resto del flujo funciona, pero no se genera el enlace de pago.
- **Pago grupal / Checkout API:** el commit `7d5fab00` de la rama `features/payment-service-update` (pago por fracciones en `/api/v1/payments`) **no está incluido**. Reemplaza `POST /api/payments`, que es el que usa este frontend, y no tiene pantalla en ninguna rama del frontend. Se integrará cuando el equipo defina su contrato.

## 8. Día a día

```bash
cd database
docker compose up -d      # prender MySQL
docker compose down       # apagarlo (los datos se conservan)
docker compose logs mysql # ver los logs
```

## 9. Empezar de cero

```bash
cd database
docker compose down -v    # BORRA todos los datos de la base
docker compose up -d
```

Después hay que volver a arrancar los servicios (para que recreen las tablas) y correr de nuevo `./database/seed/seed.sh`.

## Problemas comunes

| Síntoma | Causa y solución |
|---|---|
| `Access denied for user 'root'` | El volumen se creó antes con otra contraseña (por ejemplo con una versión anterior de este compose). Hacé `docker compose down -v` y volvé a levantar. |
| `Unknown database 'despescar_...'` | El volumen es anterior a los scripts de `database/init`. Mismo arreglo: `down -v`. |
| `Port 3306 is already in use` | Hay un MySQL instalado en tu sistema. Pararlo: `sudo systemctl stop mysql`. |
| `Port 8080 was already in use` | Otro programa usa el 8080 (Adminer ya no, quedó en el 8090). Revisalo con `ss -ltnp \| grep 8080`. |
| `Could not resolve placeholder 'JWT_SECRET'` (o `GROQ_API_KEY`) | Falta exportar la variable en esa terminal (sección 3). |
| `502 Reservation-Service rechazo la consulta de la reserva` al pagar | `RESERVATION_SERVICE_SYNC_TOKEN` vacía o distinta en `reservation-service` y `payment-service`. Exportá el mismo valor en las dos terminales y reiniciá ambos. |
| `Cannot connect to the Docker daemon` | Docker está apagado: `sudo systemctl start docker` (o abrí Docker Desktop). Para que arranque solo: `sudo systemctl enable docker`. |
| Error 504 del gateway o todo muy lento | Falta de memoria: el gateway corta a los 5 segundos. Cerrá programas, arrancá menos servicios a la vez, o subí los **dos** límites a 30 segundos: `mvn spring-boot:run -Dspring-boot.run.arguments="--resilience4j.timelimiter.instances.gatewayCircuitBreaker.timeoutDuration=30s --spring.cloud.gateway.httpclient.response-timeout=30s"` en `gateway-service`. El chatbot, por ejemplo, necesita hasta unos 10 segundos por respuesta. |
| El selector de aeropuertos sale vacío | Falta correr el seed (sección 5), o el gateway no está levantado. |
| Los servicios se caen tras reiniciar el equipo | Es normal: no son servicios del sistema. Repetí las secciones 2, 3 y 4. |

## Seguridad

La contraseña `despescar_dev`, los usuarios de ejemplo y el `JWT_SECRET` de este documento son **solo para desarrollo local**. En un despliegue real hay que definir valores propios mediante variables de entorno y nunca subirlos al repositorio.
