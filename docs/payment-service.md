# payment-service

← [Volver al índice](README.md)

Pagos con **MercadoPago**: crea el pago de una reserva, devuelve el enlace de pago (`checkoutUrl`), recibe la confirmación (webhook) y le avisa a [`reservation-service`](reservation-service.md). También tiene reembolsos e historial.

| | |
|---|---|
| Puerto | **8084** |
| Base de datos | `despescar_payment` |
| Por el gateway | Solo `/api/payments/**`. Los reembolsos (`/api/refunds`) y el historial (`/api/payment-history`) **no tienen ruta en el gateway**: se llaman directo al 8084. |

## Variables de entorno

| Variable | Obligatoria | Para qué |
|---|---|---|
| `JWT_SECRET` | **Sí** | Validar los tokens. El mismo valor en todos los servicios. |
| `RESERVATION_SERVICE_SYNC_TOKEN` | **Para poder pagar** | Contraseña con la que este servicio le habla a `reservation-service`. **El mismo valor que allá.** |
| `MERCADOPAGO_ACCESS_TOKEN` | Para generar el enlace de pago | Credencial de MercadoPago. Sin ella el servicio arranca, pero no puede crear el enlace. |
| `MERCADOPAGO_SUCCESS_URL`, `MERCADOPAGO_PENDING_URL`, `MERCADOPAGO_FAILURE_URL` | No | Páginas a las que MercadoPago devuelve al usuario después de pagar. |
| `MERCADOPAGO_NOTIFICATION_URL`, `MERCADOPAGO_WEBHOOK_SECRET` | No | Dirección y secreto del webhook de confirmación. |
| `RESERVATION_SERVICE_URL` | No | Dirección de reservas (por defecto `http://localhost:8085`). |
| `DB_PASSWORD` | No | Solo si cambiaste la contraseña de MySQL. |

Para pruebas conviene usar una **credencial de prueba** de MercadoPago (se obtiene en el panel de desarrolladores de tu cuenta de MercadoPago). Es personal: no la subas al repositorio ni la compartas.

## Levantarlo

> **Primero:** instalá el [módulo común](README.md#antes-de-levantar-los-servicios-instalar-el-módulo-común) (`mvn -q -f services/common-security/pom.xml install`, una sola vez).

Con MySQL arriba ([`mysql-docker.md`](mysql-docker.md)), desde la raíz del repositorio:

**Linux**
```bash
export JWT_SECRET='despescar-dev-secret-key-2026-must-be-long-enough'
export RESERVATION_SERVICE_SYNC_TOKEN='despescar-dev-sync-token'
export MERCADOPAGO_ACCESS_TOKEN='tu-credencial-de-prueba'     # opcional
cd services/payment-service
mvn spring-boot:run
```

**Windows (PowerShell)**
```powershell
$env:JWT_SECRET = 'despescar-dev-secret-key-2026-must-be-long-enough'
$env:RESERVATION_SERVICE_SYNC_TOKEN = 'despescar-dev-sync-token'
$env:MERCADOPAGO_ACCESS_TOKEN = 'tu-credencial-de-prueba'     # opcional
cd services\payment-service
mvn spring-boot:run
```

Está listo cuando el log dice `Started PaymentServiceApplication`.

## Cómo funciona un pago

1. El frontend llama a `POST /api/payments` con `{ "reservationId": N }` y el token del cliente. El usuario sale del token.
2. Este servicio le pide a `reservation-service` los datos de la reserva (ruta interna, con `RESERVATION_SERVICE_SYNC_TOKEN`). **El importe y la moneda salen de la reserva**, no del pedido.
3. Crea la preferencia en MercadoPago y responde con el pago, incluido el `checkoutUrl`.
4. El frontend redirige al usuario a ese enlace. Solo lo hace si es `https` y pertenece a MercadoPago.
5. Cuando MercadoPago confirma, llama al webhook `POST /api/payments/mercadopago/webhook`, y este servicio marca la reserva como pagada en `reservation-service`.

## Endpoints y permisos

| Método y ruta | Acceso |
|---|---|
| `POST /api/payments` (cuerpo `{ "reservationId": N }`) | Cliente (`USER`) |
| `GET /api/payments/{paymentId}`, `GET /api/payments/user/{userId}`, `GET /api/payments/reservation/{reservationId}`, `DELETE /api/payments/{paymentId}` | Cliente |
| `POST /api/payments/mercadopago/webhook` | **Público** (lo llama MercadoPago) |
| `POST /api/refunds`, `GET /api/refunds/{id}`, `GET /api/refunds/payment/{id}`, `GET /api/refunds/user/{id}` | Cliente. Solo directo al 8084. |
| `GET /api/payment-history/payment/{paymentId}` | Cliente. Solo directo al 8084. |

## Qué errores esperar

| Respuesta de `POST /api/payments` | Qué significa |
|---|---|
| `422` "La reserva no define una moneda única para calcular el pago" | La reserva todavía no tiene pasajeros ni tarifa asignados (falta el paso `PUT /api/bookings/{id}/passengers`). |
| `502` "Reservation-Service rechazo la consulta de la reserva" | `RESERVATION_SERVICE_SYNC_TOKEN` vacía o distinta entre pagos y reservas. |
| `500` "An unexpected error occurred" | Falló la llamada a MercadoPago, por ejemplo sin `MERCADOPAGO_ACCESS_TOKEN` o con una credencial inválida. El detalle está en el log del servicio (`MPApiException`). |
| `401` / `403` | Falta el token, venció (15 minutos) o el usuario no es cliente. |

## Probarlo

El pago necesita una reserva con pasajeros cargados, algo que se hace desde el frontend (hay que elegir asientos). Con el `bookingId` de una reserva ya preparada:

**Linux**
```bash
TOKEN=$(curl -s -X POST http://localhost:8087/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"cliente@despescar.com","password":"Cliente123!"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")

curl -s -X POST http://localhost:8087/api/payments -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"reservationId": 1}'
```

**Windows (PowerShell)**
```powershell
$t = Invoke-RestMethod -Method Post -Uri http://localhost:8087/api/auth/login -ContentType 'application/json' `
  -Body '{"email":"cliente@despescar.com","password":"Cliente123!"}'

Invoke-RestMethod -Method Post -Uri http://localhost:8087/api/payments -ContentType 'application/json' `
  -Headers @{ Authorization = "Bearer $($t.accessToken)" } -Body '{"reservationId": 1}'
```

Con todo bien configurado, la respuesta incluye el `checkoutUrl`. Con una reserva recién creada (sin pasajeros) esperá el `422` de arriba, que ya indica que la consulta interna a reservas funciona.

> **Estado de la prueba:** el recorrido llega hasta la llamada a MercadoPago y se comprobó con éxito el tramo interno (consulta a reservas con el token compartido). El pago **de punta a punta** (credencial de prueba, pago real y webhook) **está pendiente de probar**. MercadoPago no puede llamar a `localhost`: para recibir el webhook en local hace falta una dirección pública que apunte a tu equipo, y sin eso el pago se puede hacer pero la reserva no se marca como pagada sola.

## Checkout API y pago grupal (no incluidos)

La rama `features/payment-service-update` reemplaza `POST /api/payments` por un esquema de pago grupal por fracciones en `/api/v1/payments` (Checkout API). **No está incluido** en esta rama de integración: elimina el endpoint que usa el frontend actual, no tiene autenticación (`permitAll`), no está enrutado en el gateway y ninguna rama del frontend lo usa. Se integrará cuando el equipo defina su contrato.

## Problemas comunes

| Síntoma | Causa y solución |
|---|---|
| `Could not find artifact com.despescar:common-security` | Falta instalar el [módulo común](README.md#antes-de-levantar-los-servicios-instalar-el-módulo-común). |
| `Could not resolve placeholder 'JWT_SECRET'` | Falta definir la variable **en esa terminal**. |
| `502` al pagar | Ver la tabla de errores: el token compartido no coincide con el de `reservation-service`. |
| `500` al pagar | Falta o es inválida la credencial de MercadoPago (`MERCADOPAGO_ACCESS_TOKEN`). |
| La reserva no queda pagada después de pagar | El webhook no llega: en local necesita una dirección pública (`MERCADOPAGO_NOTIFICATION_URL`). |
| `Port 8084 was already in use` | **Linux:** `ss -ltnp \| grep 8084`. **Windows:** `netstat -ano \| findstr :8084`. |
