# reservation-service

← [Volver al índice](README.md)

Reservas de vuelos (y, opcionalmente, hotel o paquete), mapa de asientos y **selección de asientos en tiempo real** por WebSocket.

| | |
|---|---|
| Puerto | **8085** |
| Base de datos | `despescar_reservation` |
| Por el gateway | `/api/bookings/**` y `/api/extra-baggage/**` (esta última se reescribe a `/extra-baggage/**`) |
| WebSocket | `ws://localhost:8085/ws-despescar` (**directo**, no pasa por el gateway) |

## Variables de entorno

| Variable | Obligatoria | Para qué |
|---|---|---|
| `JWT_SECRET` | **Sí** | Validar los tokens. El mismo valor en todos los servicios. |
| `RESERVATION_SERVICE_SYNC_TOKEN` | **Para poder pagar** | Contraseña compartida con [`payment-service`](payment-service.md) (ver abajo). **El mismo valor en los dos.** |
| `INVENTORY_SERVICE_TOKEN` | **Para descontar asientos (y habitaciones, cuando se rehaga)** | Contraseña con la que este servicio le habla a `flightservice`. **El mismo valor en ambos.** `hotel-service` ya no lo usa. |
| `DB_PASSWORD` | No | Solo si cambiaste la contraseña de MySQL. |

## Levantarlo

> **Primero:** instalá el [módulo común](README.md#antes-de-levantar-los-servicios-instalar-el-módulo-común) (`mvn -q -f services/common-security/pom.xml install`, una sola vez).

Con MySQL arriba ([`mysql-docker.md`](mysql-docker.md)), desde la raíz del repositorio:

**Linux**
```bash
export JWT_SECRET='despescar-dev-secret-key-2026-must-be-long-enough'
export RESERVATION_SERVICE_SYNC_TOKEN='despescar-dev-sync-token'
export INVENTORY_SERVICE_TOKEN='despescar-dev-inventory-token'
cd services/reservation-service
mvn spring-boot:run
```

**Windows (PowerShell)**
```powershell
$env:JWT_SECRET = 'despescar-dev-secret-key-2026-must-be-long-enough'
$env:RESERVATION_SERVICE_SYNC_TOKEN = 'despescar-dev-sync-token'
$env:INVENTORY_SERVICE_TOKEN = 'despescar-dev-inventory-token'
cd services\reservation-service
mvn spring-boot:run
```

Está listo cuando el log dice `Started ReservationServiceApplication`. Crea las tablas `reservations`, `reservation_details`, `reservation_flights`, `pasajeros`, `seats` y `equipaje_extra`. Los asientos de un vuelo se generan automáticamente la primera vez que se consultan.

## El flujo de una compra

1. **Crear la reserva:** `POST /api/bookings/init` → queda `INICIADA`.
2. **Elegir asientos** en el mapa (WebSocket, ver abajo): cada asiento se retiene para quien lo eligió.
3. **Cargar los pasajeros:** `PUT /api/bookings/{id}/passengers` → la reserva pasa a `PENDIENTE_PAGO`.
4. **Pagar:** `POST /api/payments` en [`payment-service`](payment-service.md). Cuando MercadoPago confirma el pago, pagos le avisa a este servicio por una ruta interna.

Si no se completa a tiempo, la reserva pasa a `EXPIRADA`.

**Inventario:** los asientos de `flightservice` y la habitación de `hotel-service` se descuentan **cuando el último pagador confirma el pago** (la reserva pasa a `CONFIRMADA`), no al crearla. Si el creador cancela una reserva ya `CONFIRMADA`, se devuelven. Una reserva que expira o se cancela antes de confirmarse no toca el inventario. Hace falta el mismo `INVENTORY_SERVICE_TOKEN` en los tres servicios.

**Tiempos:** una reserva dura **15 minutos** desde que se crea, y un asiento elegido se retiene **15 minutos**. Cada minuto un proceso automático libera lo vencido.

## Endpoints y permisos

Todo lo que modifica requiere el rol de **cliente** (`USER` en el login; para este servicio es `ROLE_CLIENTE`). Según el código, un administrador con solo `SUPER_ADMIN` no puede crear reservas.

| Método y ruta | Acceso |
|---|---|
| `GET /api/bookings/flights/{flightId}/seats` y `.../seat-map` | **Público** (lista y plano de asientos) |
| `POST /api/bookings/init` | Cliente |
| `PUT /api/bookings/{id}/passengers` | Cliente |
| `GET /api/bookings/{id}` y `DELETE /api/bookings/{id}` (cancelar) | Cliente |
| `POST /api/bookings/{id}/split-setup` y `POST /api/bookings/{id}/pay` | Cliente (pago dividido, `paymentType = SPLIT_PAYMENT`: existe en el backend y el frontend actual no lo usa) |
| `POST /extra-baggage/{detalleReservaId}` | Equipaje extra (el frontend actual no lo usa) |
| `GET /api/bookings/internal/{id}` y `POST /api/bookings/internal/{id}/payment-confirmed` | **Solo `payment-service`** (ver abajo) |

`POST /api/bookings/init` recibe `flightIds` (lista de ids de vuelo), `cantidadPasajeros`, `paymentType` (`SINGLE_PAYMENT` o `SPLIT_PAYMENT`), `baggageIds` (ids de las tarifas elegidas), y opcionalmente `hotelId` y `packageId`. El usuario sale del token: **no** se envía en el cuerpo. Responde `201` con `bookingId`.

`PUT /api/bookings/{id}/passengers` recibe `pasajeros`, una lista con `nombreCompleto`, `dniPasaporte`, `asientoIda`, `asientoVuelta`, `tarifaId`, `tarifaNombre` y `precioTarifa`. Si algún asiento no está retenido por ese usuario, responde `409 ASIENTO_NO_BLOQUEADO`.

## La comunicación interna con pagos

`payment-service` necesita dos cosas de este servicio sin que haya una persona de por medio: consultar los datos y el importe de una reserva, y avisar que el pago se confirmó. Esas dos llamadas van a las rutas `internal` y se identifican con el encabezado `X-Internal-Service-Token`, cuyo valor es `RESERVATION_SERVICE_SYNC_TOKEN`.

- Si la variable está vacía o es distinta en los dos servicios, este servicio rechaza la llamada (`403`) y `POST /api/payments` responde `502 Reservation-Service rechazo la consulta de la reserva`.
- Con el token correcto, `GET /api/bookings/internal/{id}` devuelve la reserva.
- Estas rutas **no se pueden usar a través del gateway**: devuelve `403`, porque solo deben alcanzarse entre servicios.
- No tiene relación con `JWT_SECRET`, que firma los tokens de los usuarios.

## Asientos en tiempo real (WebSocket)

El frontend se conecta por STOMP a `ws://localhost:8085/ws-despescar`, enviando `Authorization: Bearer <token>` en el `CONNECT`, y:

- Se suscribe a `/topic/flight/{flightId}`: ahí llegan los cambios de asientos de ese vuelo, para todas las personas que lo estén mirando.
- Se suscribe a `/user/queue/errores`: ahí llegan los errores (por ejemplo, un asiento ya ocupado).
- Para elegir un asiento publica en `/app/select-seat/{flightId}` el mensaje `{ "seatUuid": "...", "userId": ... }`; para soltarlo, en `/app/deselect-seat/{flightId}`.

Al elegir un asiento queda `RESERVADO_TEMPORAL` a nombre del usuario por 15 minutos y se avisa a todos. Si vence, vuelve a `DISPONIBLE`. La dirección del WebSocket sale de `VITE_WS_URL` en el frontend (ver [`frontend.md`](frontend.md)).

**Cómo probarlo:** es lo más práctico desde el navegador. Entrá al frontend con dos usuarios distintos (por ejemplo, una ventana normal y una privada), abrí el mapa de asientos del mismo vuelo, y elegí un asiento en una: en la otra tiene que aparecer ocupado al instante. En las herramientas del navegador, pestaña **Network → WS**, se ve la conexión; en **Console** (modo desarrollo) se imprimen los mensajes STOMP.

**Seguridad de la conexión:** el `CONNECT` valida el token (firma, vencimiento y `userId`) y rechaza la conexión si falta o es inválido; sin conexión autenticada no se puede publicar. El usuario que retiene o suelta un asiento sale **del token**, no del mensaje: el campo `userId` que el cliente envíe se ignora.

## Probar la creación de una reserva

Con el [gateway](gateway-service.md) y [`flightservice`](flightservice.md) arriba y los [datos de ejemplo](datos-de-ejemplo.md) cargados:

**Linux**
```bash
FECHA=$(date -d '+14 days' +%F)
TOKEN=$(curl -s -X POST http://localhost:8087/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"cliente@despescar.com","password":"Cliente123!"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")

read VUELO TARIFA < <(curl -s "http://localhost:8087/api/flights/search?origin=EZE&destination=COR&departureDate=$FECHA&passengers=1" \
  | python3 -c "import sys,json; v=json.load(sys.stdin)['departureFlights'][0]; print(v['id'], v['fares'][0]['id'])")

curl -s -X POST http://localhost:8087/api/bookings/init -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"flightIds\":[\"$VUELO\"],\"cantidadPasajeros\":1,\"paymentType\":\"SINGLE_PAYMENT\",\"hotelId\":null,\"baggageIds\":[\"$TARIFA\"],\"packageId\":null}"
```

**Windows (PowerShell)**
```powershell
$fecha = (Get-Date).AddDays(14).ToString('yyyy-MM-dd')
$t = Invoke-RestMethod -Method Post -Uri http://localhost:8087/api/auth/login -ContentType 'application/json' `
  -Body '{"email":"cliente@despescar.com","password":"Cliente123!"}'

$v = (Invoke-RestMethod -Uri "http://localhost:8087/api/flights/search?origin=EZE&destination=COR&departureDate=$fecha&passengers=1").departureFlights[0]

$body = @{ flightIds = @($v.id); cantidadPasajeros = 1; paymentType = 'SINGLE_PAYMENT'; hotelId = $null; baggageIds = @($v.fares[0].id); packageId = $null } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8087/api/bookings/init -Headers @{ Authorization = "Bearer $($t.accessToken)" } -ContentType 'application/json' -Body $body
```

Una respuesta correcta es `{ "bookingId": N, "paymentType": "SINGLE_PAYMENT", "status": "INICIADA" }`. Los pasos siguientes (asientos y pasajeros) se hacen desde el frontend.

## Problemas comunes

| Síntoma | Causa y solución |
|---|---|
| `Could not find artifact com.despescar:common-security` | Falta instalar el [módulo común](README.md#antes-de-levantar-los-servicios-instalar-el-módulo-común). |
| `401` o `403` al crear la reserva | Falta el token, venció (15 minutos), o el usuario no es cliente (`USER`). |
| `409 ASIENTO_NO_BLOQUEADO` al cargar pasajeros | El asiento no está retenido por ese usuario: hay que elegirlo primero en el mapa, y la retención dura 15 minutos. |
| `502 Reservation-Service rechazo la consulta de la reserva` al pagar | `RESERVATION_SERVICE_SYNC_TOKEN` vacía o distinta entre reservas y pagos. Definí el mismo valor en las dos terminales y reiniciá ambos. |
| El mapa de asientos no cambia en tiempo real | Servicio apagado, o `VITE_WS_URL` mal configurada en el frontend. Revisá la pestaña **Network → WS**. |
| La reserva pasa a `EXPIRADA` | Pasaron los 15 minutos sin completarla. Hay que crear otra. |
| `Port 8085 was already in use` | **Linux:** `ss -ltnp \| grep 8085`. **Windows:** `netstat -ano \| findstr :8085`. |
