# DesPescar ✈️🏨

Plataforma de reservas de viajes construida con arquitectura de microservicios en Spring Boot.

---

## Servicios

| Servicio | Puerto | Base de datos | Descripción |
|---|---|---|---|
| `identity-service` | 8080 | `despescar_identity` | Autenticación, usuarios y roles |
| `flightservice` | 8081 | `despescar_flight` | Gestión de vuelos, aerolíneas y aeropuertos |
| `hotel-service` | 8083 | `despescar_hotel` | Gestión de hoteles |
| `payment-service` | 8084 | — | Procesamiento de pagos *(en desarrollo)* |
| `reservation-service` | 8085 | `despescar_reservation` | Reservas de vuelos, hoteles y paquetes |
| `package-service` | 8086 | `despescar_package` | Paquetes turísticos (vuelo + hotel) |
| `gateway-service` | 8087 | — | API Gateway para exponer una única entrada al frontend |

---

## Flujo principal

### 1. Autenticación

```
POST /api/auth/login
```

El usuario envía email y contraseña al **identity-service**. Si las credenciales son correctas, recibe un `accessToken` (JWT) y un `refreshToken`.

El JWT incluye:
- `sub`: email del usuario
- `role`: rol principal (`SUPER_ADMIN`, `AIRLINE_ADMIN`, `HOTEL_ADMIN`, `USER`)

Este token debe enviarse en el header `Authorization: Bearer <token>` en todas las llamadas posteriores.

---

### 2. Consulta de vuelos

```
GET /api/flights                          → listar todos
GET /api/flights/number/{flightNumber}    → buscar por número
GET /api/flights/airline/{airlineId}      → buscar por aerolínea
GET /api/flights/origin/{airportId}       → buscar por aeropuerto de origen
GET /api/flights/destination/{airportId}  → buscar por destino
```

Requiere autenticación. El campo `availableSeats` indica asientos libres en tiempo real.

---

### 3. Consulta de hoteles

```
GET /api/hotels                 → listar todos
GET /api/hotels/{id}            → buscar por ID
GET /api/hotels/ciudad/{ciudad} → buscar por ciudad
```

Requiere autenticación. El campo `habitacionesDisponibles` refleja disponibilidad real.

---

### 4. Consulta de paquetes turísticos

```
GET /api/packages               → listar paquetes activos (con filtros opcionales)
GET /api/packages/{id}          → detalle de un paquete
```

Un paquete combina un `flightNumber` y un `hotelId` con un precio base. Requiere autenticación.

---

### 5. Crear una reserva

```
POST /api/bookings
Authorization: Bearer <token>

{
  "creadorId": 1,
  "vueloCodigo": "AR1234",
  "hotelId": "uuid-del-hotel",        ← opcional
  "packageId": 5,                      ← opcional, auto-completa vuelo y hotel
  "asientos": [
    {
      "numeroAsiento": "12A",
      "usuarioId": 1,
      "pagadorId": 1
    }
  ]
}
```

**El sistema valida:**
- ✅ Que el vuelo exista y tenga estado reservable (`SCHEDULED`, `DELAYED`)
- ✅ Que haya suficientes asientos disponibles
- ✅ Que el hotel tenga habitaciones disponibles (si se incluyó)
- ✅ Que el paquete esté activo y los datos sean consistentes (si se incluyó)
- ✅ Que el asiento no esté ya reservado

**Al confirmar la reserva:**
- Se descuenta la cantidad de asientos en `flightservice`
- Se descuenta 1 habitación en `hotel-service` (si aplica)
- La reserva queda en estado `PENDIENTE` con 15 minutos para pagar

---

### 6. Ciclo de vida de una reserva

```
PENDIENTE → COMPLETADA   (todos los pasajeros pagaron)
PENDIENTE → CANCELADA    (el creador la cancela manualmente)
PENDIENTE → EXPIRADA     (pasaron los 15 minutos sin pagar)
```

Cuando una reserva pasa a `CANCELADA` o `EXPIRADA`, el inventario se restaura automáticamente en `flightservice` y `hotel-service`.

El scheduler revisa reservas expiradas cada 60 segundos.

Los cambios de estado se notifican en tiempo real via **WebSocket** (`/topic/reserva/{id}`).

---

### 7. Pagar una reserva

```
POST /api/bookings/{id}/pagar
```

Cada pasajero paga su asiento de forma independiente. Cuando todos los pasajeros pagaron, la reserva pasa a `COMPLETADA`.

> La integración real con un procesador de pagos está pendiente (`payment-service`).

---

## Roles y permisos

| Rol | Puede hacer |
|---|---|
| `USER` | Consultar vuelos, hoteles y paquetes. Crear y gestionar sus reservas |
| `HOTEL_ADMIN` | Todo lo anterior + crear/editar/eliminar hoteles |
| `AIRLINE_ADMIN` | Todo lo anterior + crear/editar/eliminar vuelos, aerolíneas y aeropuertos |
| `SUPER_ADMIN` | Todo. Único rol que puede gestionar paquetes turísticos |

---

## Seguridad

- Autenticación **JWT stateless** compartida entre todos los servicios
- Firma HMAC-SHA256 con clave configurable via variable de entorno `JWT_SECRET`
- Refresh tokens con expiración independiente
- Bloqueo de cuenta tras múltiples intentos fallidos de login
- El `gateway-service` valida JWT y rol antes de enrutar, manteniendo además la validación en cada microservicio

---

## Gateway (8087)

- Entrada única para frontend: `http://localhost:8087`
- Timeouts homogéneos para llamadas salientes (connect/read)
- Respuestas de error unificadas con `requestId`
- Request tracing con header `X-Request-Id` + métrica `despescar.gateway.requests`
- Rate limiting por IP y circuit breaker con fallback `/fallback/unavailable`

---

## Requisitos para correr localmente

- Java 17+
- Maven 3.8+
- MySQL 8+ corriendo en `localhost:3306`
- Bases de datos creadas:
  ```sql
  CREATE DATABASE despescar_identity;
  CREATE DATABASE despescar_flight;
  CREATE DATABASE despescar_hotel;
  CREATE DATABASE despescar_reservation;
  CREATE DATABASE despescar_package;
  ```

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
          └─► package-service    (8086)
```
