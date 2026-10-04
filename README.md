# DesPescar ✈️🏨

Plataforma de reservas de viajes construida con arquitectura de microservicios en Spring Boot.

---

## Servicios

| Servicio | Puerto | Base de datos | Descripción |
|---|---|---|---|
| `identity-service` | 8080 | `despescar_identity` | Autenticación, usuarios y roles |
| `flightservice` | 8081 | `despescar_flight` | Vuelos, aerolíneas, aeropuertos y tarifas |
| `hotel-service` | 8083 | `despescar_hotel` | Gestión de hoteles |
| `payment-service` | 8084 | `despescar_payment` | Pagos con MercadoPago, webhook, reembolsos e historial |
| `reservation-service` | 8085 | `despescar_reservation` | Reservas, mapa de asientos y selección en tiempo real (WebSocket) |
| `package-service` | 8086 | `despescar_package` | Paquetes turísticos (vuelo + hotel) |
| `gateway-service` | 8087 | — | Entrada única para el frontend: valida JWT y rol, limita peticiones |
| `koi-ia-service` | 8088 | `despescar_koiia` | Chatbot KOI (IA de Groq) para orientar y recomendar viajes |

Cada servicio tiene su guía en [`docs/`](docs/README.md), con variables de entorno, comandos y problemas comunes. Las rutas de abajo se llaman por el gateway (`http://localhost:8087`) salvo que se indique otra cosa.

---

## Flujo principal

### 1. Autenticación

```
POST /api/auth/login
```

El usuario envía email y contraseña a **identity-service** y recibe un `accessToken` (JWT, dura 15 minutos) y un `refreshToken` (7 días). El JWT incluye `sub` (email), `userId` y `role` (`SUPER_ADMIN`, `AIRLINE_ADMIN`, `HOTEL_ADMIN` o `USER`). Se envía en `Authorization: Bearer <token>`.

### 2. Consultar vuelos, hoteles y paquetes

```
GET /api/flights/search?origin=EZE&destination=COR&departureDate=2026-10-18&passengers=1   → pública
GET /api/flights, /api/flights/{id}, /api/airports, /api/airports/code/{code}              → públicas
GET /api/hotels, /api/hotels/{id}, /api/hotels/ciudad/{ciudad}                             → con sesión
GET /api/packages, /api/packages/{id}                                                      → con sesión
```

Buscar y ver vuelos no exige iniciar sesión; sí hace falta para avanzar con la compra. Un paquete combina un `flightNumber` y un `hotelId` con un precio base.

### 3. Comprar un vuelo

1. **Crear la reserva:** `POST /api/bookings/init` con `flightIds`, `cantidadPasajeros`, `paymentType` (`SINGLE_PAYMENT` o `SPLIT_PAYMENT`), `baggageIds` y, opcionalmente, `hotelId` y `packageId`. El usuario sale del token. La reserva queda `INICIADA`.
2. **Elegir asientos** por WebSocket (`ws://localhost:8085/ws-despescar`, directo al servicio). Cada asiento se retiene 15 minutos.
3. **Cargar los pasajeros:** `PUT /api/bookings/{id}/passengers`. La reserva pasa a `PENDIENTE_PAGO`.
4. **Pagar:** `POST /api/payments` con `{ "reservationId": N }`. Responde con un `checkoutUrl` de MercadoPago. El importe sale de la reserva, no del pedido.
5. **Confirmación:** MercadoPago llama al webhook y `payment-service` avisa a `reservation-service` por una ruta interna.

### 4. Estados de una reserva

```
INICIADA → PENDIENTE_PAGO → CONFIRMADA          (pagada)
         → ESPERANDO_PAGADORES → CONFIRMADA     (pago dividido: faltan pagadores)
cualquiera → CANCELADA                          (la cancela su creador: DELETE /api/bookings/{id})
cualquiera → EXPIRADA                           (pasaron 15 minutos sin completarla)
```

Un proceso automático revisa cada 60 segundos las reservas y las retenciones de asiento vencidas. Los cambios de asientos se publican por WebSocket en `/topic/flight/{flightId}`.

---

## Roles y permisos

| Rol | Puede hacer |
|---|---|
| `USER` | Es el **cliente**: consulta, reserva y paga (en reservas y pagos equivale a `ROLE_CLIENTE`) |
| `HOTEL_ADMIN` | Crear, editar y borrar hoteles |
| `AIRLINE_ADMIN` | Crear, editar y borrar vuelos, aerolíneas, aeropuertos y tarifas |
| `SUPER_ADMIN` | Todo lo anterior, gestionar usuarios y roles, y gestionar paquetes turísticos (es el único que puede) |

Los roles de administrador **no heredan** los permisos de cliente: reservar y pagar exige `USER`.

---

## Seguridad

- Autenticación **JWT stateless** compartida entre todos los servicios, firmada con HMAC-SHA256 y la variable `JWT_SECRET` (obligatoria, sin valor por defecto).
- Refresh tokens con expiración independiente. La cuenta se bloquea 15 minutos tras 5 intentos fallidos de login.
- El `gateway-service` valida el JWT y el rol antes de enrutar; cada servicio vuelve a validar el suyo.
- Las llamadas entre servicios usan un token compartido en el encabezado `X-Internal-Service-Token`: `RESERVATION_SERVICE_SYNC_TOKEN` (pagos ↔ reservas) e `INVENTORY_SERVICE_TOKEN` (reservas → vuelos y hoteles, para descontar asientos y habitaciones). El gateway bloquea esas rutas (`403`).
- El WebSocket de asientos valida el JWT al conectar.

## Gateway (8087)

- Entrada única del frontend, con CORS abierto (sin credenciales).
- Tiempo de espera de 5 segundos (`504` si se supera) y circuit breaker con respuesta `/fallback/unavailable`.
- Límite de 120 peticiones por minuto por IP (no cuenta `/api/auth`, `/actuator` ni `/fallback`).
- Errores unificados con `requestId` y encabezado `X-Request-Id`.
- No pasan por él: `/api/fares`, `/api/refunds`, `/api/payment-history` y el WebSocket.

Detalle en [`docs/gateway-service.md`](docs/gateway-service.md).

---

## Endpoints por servicio

Se indican las rutas **del servicio** (puerto propio). Entre paréntesis, cómo se llaman por el gateway cuando cambia.

### `identity-service` (8080) — por el gateway: `/api/auth/**` y `/api/users/**`
| Método | Endpoint | Para qué sirve |
|---|---|---|
| POST | `/auth/register` | Registrar un usuario (queda como `USER`) |
| POST | `/auth/login` | Iniciar sesión |
| POST | `/auth/refresh` | Renovar el access token |
| POST | `/auth/logout` | Cerrar sesión (revoca el refresh token) |
| GET | `/auth/me` | Correo y rol del usuario autenticado |
| GET | `/users/me` | Perfil del usuario autenticado |
| GET | `/users/me/roles` | Roles del usuario autenticado |
| GET | `/users` | Listar usuarios (`SUPER_ADMIN`) |
| GET | `/users/{id}` | Usuario por ID (`SUPER_ADMIN`) |
| GET | `/users/roles` | Listar roles (`SUPER_ADMIN`) |
| POST | `/users/{id}/roles` | Asignar un rol (`SUPER_ADMIN`) |
| DELETE | `/users/{id}/roles/{roleId}` | Quitar un rol (`SUPER_ADMIN`) |

### `flightservice` (8081)
| Método | Endpoint | Para qué sirve |
|---|---|---|
| GET | `/api/flights/search` | Buscar vuelos (público) |
| POST | `/api/flights` | Crear un vuelo |
| GET | `/api/flights`, `/api/flights/{id}` | Listar y buscar por ID (públicos) |
| GET | `/api/flights/number/{flightNumber}` | Buscar por número |
| GET | `/api/flights/airline/{airlineId}` | Buscar por aerolínea |
| GET | `/api/flights/origin/{airportId}` | Buscar por aeropuerto de origen |
| GET | `/api/flights/destination/{airportId}` | Buscar por aeropuerto de destino |
| PUT / DELETE | `/api/flights/{id}` | Actualizar / eliminar |
| PATCH | `/api/flights/number/{flightNumber}/seats?delta=` | **Interno**: ajusta asientos (solo `reservation-service`) |
| POST / GET | `/api/airlines`, `/api/airlines/{id}`, `/api/airlines/code/{code}` | Crear y consultar aerolíneas |
| PUT / DELETE | `/api/airlines/{id}` | Actualizar / eliminar |
| POST / GET | `/api/airports`, `/api/airports/{id}`, `/api/airports/code/{code}`, `/country/{country}`, `/city/{city}` | Crear y consultar aeropuertos |
| PUT / DELETE | `/api/airports/{id}` | Actualizar / eliminar |
| POST / GET | `/api/fares`, `/api/fares/{id}` | Tarifas y equipaje (tabla `baggage_policies`). **Sin ruta en el gateway** |

### `hotel-service` (8083) — por el gateway: `/hoteles/**` y `/api/hotels/**`
| Método | Endpoint | Para qué sirve |
|---|---|---|
| GET | `/test` | Comprobación simple del servicio |
| POST | `/hoteles` | Crear hotel |
| GET | `/hoteles`, `/hoteles/{id}`, `/hoteles/ciudad/{city}` | Listar y buscar |
| PUT / DELETE | `/hoteles/{id}` | Actualizar / eliminar |
| PATCH | `/hoteles/{id}/rooms?delta=` | **Interno**: ajusta habitaciones (solo `reservation-service`) |

### `package-service` (8086)
| Método | Endpoint | Para qué sirve |
|---|---|---|
| POST | `/api/packages` | Crear paquete (`SUPER_ADMIN`) |
| GET | `/api/packages` | Listar con filtros opcionales |
| GET | `/api/packages/{id}` | Buscar por ID |
| PUT | `/api/packages/{id}` | Actualizar (`SUPER_ADMIN`) |
| DELETE | `/api/packages/{id}` | Desactivar (`SUPER_ADMIN`) |
| POST | `/api/packages/{id}/activate` | Reactivar (`SUPER_ADMIN`) |

### `reservation-service` (8085) — por el gateway: `/api/bookings/**` y `/api/extra-baggage/**`
| Método | Endpoint | Para qué sirve |
|---|---|---|
| GET | `/api/bookings/flights/{flightId}/seats` y `/seat-map` | Asientos y plano del vuelo (público) |
| POST | `/api/bookings/init` | Crear una reserva |
| PUT | `/api/bookings/{id}/passengers` | Cargar pasajeros y asientos |
| GET | `/api/bookings/{id}` | Obtener la reserva |
| DELETE | `/api/bookings/{id}` | Cancelar la reserva |
| POST | `/api/bookings/{id}/split-setup` | Configurar pago dividido |
| POST | `/api/bookings/{id}/pay` | Registrar el pago de un pasajero (pago dividido) |
| GET | `/api/bookings/internal/{id}` | **Interno**: datos de la reserva (solo `payment-service`) |
| POST | `/api/bookings/internal/{id}/payment-confirmed` | **Interno**: confirmar un pago (solo `payment-service`) |
| POST | `/extra-baggage/{detalleReservaId}` | Agregar equipaje extra (`/api/extra-baggage/...` por el gateway) |
| WS | `/ws-despescar` | STOMP: `/app/select-seat/{flightId}`, `/app/deselect-seat/{flightId}`, `/topic/flight/{flightId}`. **Directo, sin gateway** |

### `payment-service` (8084) — por el gateway: `/api/payments/**`
| Método | Endpoint | Para qué sirve |
|---|---|---|
| POST | `/api/payments` | Crear el pago de una reserva (devuelve `checkoutUrl`) |
| GET | `/api/payments/{paymentId}` | Obtener un pago |
| GET | `/api/payments/user/{userId}` | Pagos del usuario (solo los propios) |
| GET | `/api/payments/reservation/{reservationId}` | Pagos de una reserva |
| DELETE | `/api/payments/{paymentId}` | Cancelar un pago pendiente |
| POST | `/api/payments/mercadopago/webhook` | Confirmación de MercadoPago (público, con firma) |
| POST / GET | `/api/refunds`, `/api/refunds/{id}`, `/payment/{id}`, `/user/{id}` | Reembolsos. **Solo directo al 8084** |
| GET | `/api/payment-history/payment/{paymentId}` | Historial de un pago. **Solo directo al 8084** |

### `koi-ia-service` (8088) — por el gateway: `/api/koi/**` (pública)
| Método | Endpoint | Para qué sirve |
|---|---|---|
| POST | `/api/koi/sessions` | Crear una conversación |
| GET | `/api/koi/sessions/{sessionId}` | Ver el estado de la conversación |
| POST | `/api/koi/sessions/{sessionId}/messages` | Enviar un mensaje y recibir la respuesta |

KOI usa un modelo de [Groq](https://groq.com) (`openai/gpt-oss-20b`) y consulta los demás servicios para sus respuestas. Necesita `GROQ_API_KEY`: sin ella el servicio no arranca. Cómo obtenerla, límites y limitaciones conocidas en [`docs/koi-ia-service.md`](docs/koi-ia-service.md).

---

## Requisitos para correr localmente

- Java 17+
- Maven 3.8+
- Docker con Compose v2: levanta MySQL (con las 7 bases `despescar_*` ya creadas) y Adminer, con una contraseña común de desarrollo
- Python 3 (solo para cargar datos de ejemplo)

Funciona en **Linux** y en **Windows** (PowerShell). **Cada servicio tiene su guía** con las variables de entorno, los comandos para ambos sistemas y los problemas comunes: empezá por [`docs/README.md`](docs/README.md).

| Guía | Trata de |
|---|---|
| [`docs/mysql-docker.md`](docs/mysql-docker.md) | MySQL y Adminer con Docker |
| [`docs/datos-de-ejemplo.md`](docs/datos-de-ejemplo.md) | Datos y usuarios de prueba |
| [`docs/identity-service.md`](docs/identity-service.md), [`flightservice`](docs/flightservice.md), [`hotel-service`](docs/hotel-service.md), [`payment-service`](docs/payment-service.md), [`reservation-service`](docs/reservation-service.md), [`package-service`](docs/package-service.md) | Cada microservicio |
| [`docs/gateway-service.md`](docs/gateway-service.md) | Rutas, permisos y límites del gateway |
| [`docs/koi-ia-service.md`](docs/koi-ia-service.md) | Chatbot KOI con Groq |
| [`docs/frontend.md`](docs/frontend.md) | Cómo se conecta el frontend |

Resumen mínimo:

```bash
cd database
docker compose up -d      # MySQL en localhost:3306, Adminer en http://localhost:8090
```

Después, cada servicio en su terminal (ver su guía) y, con identity, flight, hotel y package en marcha, los datos de ejemplo (`./database/seed/seed.sh` en Linux; `py -3 database\seed\seed.py` en Windows).

Si preferís usar un MySQL 8 instalado a mano, ejecutá `database/init/01_create_databases.sql` para crear las bases y definí `DB_PASSWORD` si tu contraseña de `root` no es `despescar_dev`.

Cada servicio levanta con:
```bash
cd services/<nombre-servicio>
mvn spring-boot:run
```

La documentación Swagger de cada servicio está disponible en:
```
http://localhost:<puerto>/swagger-ui/index.html
```

---

## Diagrama de interacción

```
Cliente / Frontend
  │
  └─► gateway-service (8087)
          │
          ├─► identity-service   (8080)
          ├─► flightservice      (8081)
          ├─► hotel-service      (8083)
          ├─► payment-service    (8084)
          ├─► reservation-service (8085)
          ├─► package-service    (8086)
          └─► koi-ia-service     (8088)
```
