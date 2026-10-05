# gateway-service

← [Volver al índice](README.md)

La **puerta de entrada** del frontend: recibe todas las peticiones, valida el token, aplica los permisos por rol y las reenvía al servicio que corresponde. El frontend solo conoce esta dirección (`http://localhost:8087`).

| | |
|---|---|
| Puerto | **8087** |
| Base de datos | No usa |
| Requiere | Los servicios de destino levantados (si uno está apagado, sus rutas fallan; el resto sigue funcionando) |

## Variables de entorno

| Variable | Obligatoria | Para qué |
|---|---|---|
| `JWT_SECRET` | **Sí** | Validar los tokens. **El mismo valor que en los demás servicios.** |

## Levantarlo

**Va al final**, cuando ya están arriba los servicios que vayas a usar. Este servicio no trae `mvnw`, por eso se usa `mvn`. Desde la raíz del repositorio:

**Linux**
```bash
export JWT_SECRET='despescar-dev-secret-key-2026-must-be-long-enough'
cd services/gateway-service
mvn spring-boot:run
```

**Windows (PowerShell)**
```powershell
$env:JWT_SECRET = 'despescar-dev-secret-key-2026-must-be-long-enough'
cd services\gateway-service
mvn spring-boot:run
```

Está listo cuando el log dice `Started GatewayServiceApplication`.

### Con tiempo de espera ampliado (recomendado para el chatbot y equipos lentos)

Por defecto el gateway corta a los **5 segundos** y responde `504`. Una respuesta del chatbot puede tardar hasta unos 10 segundos, y en un equipo con poca memoria cualquier servicio puede demorar más. Para desarrollo, subí **los dos límites** a 30 segundos (el comando es el mismo en Linux y en Windows; las comillas son necesarias en PowerShell):

```bash
mvn "-Dspring-boot.run.arguments=--resilience4j.timelimiter.instances.gatewayCircuitBreaker.timeoutDuration=30s --spring.cloud.gateway.httpclient.response-timeout=30s" spring-boot:run
```

Hacen falta ambos: uno es el del circuit breaker y el otro el de la respuesta del cliente HTTP. Probado con un servidor de prueba que tarda 8 segundos: pasa con `200`; con el límite de 5 segundos cortaba con `504`. Los valores valen solo para esa ejecución; si reiniciás el gateway sin ellos, vuelve a 5 segundos.

## A dónde envía cada ruta

| Ruta del gateway | Servicio | Puerto |
|---|---|---|
| `/api/auth/**` (se reescribe a `/auth/**`) | [identity-service](identity-service.md) | 8080 |
| `/api/users/**` (se reescribe a `/users/**`) | [identity-service](identity-service.md) | 8080 |
| `/api/flights/**`, `/api/airlines/**`, `/api/airports/**`, `/api/fares/**` | [flightservice](flightservice.md) | 8081 |
| `/hoteles/**` y `/api/hotels/**` (esta se reescribe a `/hoteles/**`: `RewritePath=/api/hotels(?<segment>/?.*)` → `/hoteles${segment}`, por ejemplo `/api/hotels/destinos` llega como `/hoteles/destinos`) | [hotel-service](hotel-service.md) | 8083 |
| `/api/payments/**` | [payment-service](payment-service.md) | 8084 |
| `/api/bookings/**` y `/api/extra-baggage/**` (esta se reescribe a `/extra-baggage/**`) | [reservation-service](reservation-service.md) | 8085 |
| `/api/packages/**` | [package-service](package-service.md) | 8086 |
| `/api/koi/**` | [koi-ia-service](koi-ia-service.md) | 8088 |

**No pasan por el gateway:** `/api/refunds`, `/api/payment-history` y el WebSocket de asientos (`ws://localhost:8085/ws-despescar`, que el frontend abre directo).

## Quién puede qué

**Sin token (públicas):**
- `/api/auth/**` (registro, login, renovar).
- `/api/koi/**` (el chatbot).
- `POST /api/payments/mercadopago/webhook` (lo llama MercadoPago).
- Consulta de vuelos: `GET /api/flights`, `GET /api/flights/search`, `GET /api/flights/{id}`, `GET /api/airports`, `GET /api/airports/code/{code}` y `GET /api/fares`. Antes el gateway exigía sesión incluso para esto; ahora se puede buscar y ver vuelos sin iniciar sesión, y recién se pide iniciar sesión al avanzar con la compra.
- Consulta de hoteles: `GET /api/hotels` (búsqueda con `destino`, `checkIn`, `checkOut` y `huespedes`), `GET /api/hotels/destinos` y `GET /api/hotels/{id}`. Solo `GET`; el alta (`POST /api/hotels`) sigue exigiendo rol.
- `/actuator/**` y `/fallback/**`.

**Bloqueadas siempre (`403`):** `/api/bookings/internal/**` (de `payment-service` a `reservation-service`) y los `PATCH` de ajuste de inventario `/api/flights/number/{n}/seats`, `/api/hotels/{id}/rooms` y `/hoteles/{id}/rooms` (de `reservation-service` a vuelos y hoteles). Solo se usan entre servicios y no deben alcanzarse desde afuera.

**Con token Bearer válido** (sin token o con token inválido responde `401`): todo el resto de `/api/**` y `/hoteles/**` (salvo las lecturas públicas de arriba). Además, para estas rutas se exige un rol:

| Ruta | Rol |
|---|---|
| `/api/users/**` (salvo `/api/users/me` y `/api/users/me/roles`) | `SUPER_ADMIN` |
| `/api/packages/**` con `POST`, `PUT` o `DELETE` | `SUPER_ADMIN` |
| `/api/flights`, `/api/airlines`, `/api/airports`, `/api/fares` con `POST`, `PUT` o `DELETE` | `SUPER_ADMIN` o `AIRLINE_ADMIN` |
| `/api/hotels` y `/hoteles` con `POST`, `PUT` o `DELETE` | `SUPER_ADMIN` o `HOTEL_ADMIN` |
| Cualquier otra ruta con token | Cualquier usuario con sesión |

Cada servicio vuelve a validar el token y sus propios permisos: el gateway es la primera barrera, no la única.

## Otras reglas

- **CORS:** acepta cualquier origen, sin credenciales (`allowedOriginPatterns: "*"`, `allowCredentials: false`). Sirve para el frontend en `localhost:5173` o `5174`.
- **Límite de peticiones:** **120 por minuto por dirección IP** (si llega el encabezado `X-Forwarded-For`, usa la primera). Si se supera, responde `429`. La respuesta trae `X-RateLimit-Remaining`. No cuentan `/api/auth`, `/actuator` ni `/fallback`.
- **Servicio caído o lento:** si el servicio de destino está apagado el gateway devuelve un error `5xx`; si tarda más del tiempo de espera, `504`.
- **Estado:** `GET /actuator/health` (con detalle). También están expuestos `info`, `gateway`, `metrics` y `httpexchanges`.

## Probarlo

Con el gateway y [`flightservice`](flightservice.md) arriba:

**Linux**
```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8087/api/airports               # 200: público
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8087/api/bookings/internal/1     # 403: bloqueada
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8087/api/users                   # 401: pide token
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8087/api/airports        # 401: crear exige rol
curl -s http://localhost:8087/actuator/health
```

**Windows (PowerShell)**
```powershell
function Status($metodo, $url) {
  try { (Invoke-WebRequest -Method $metodo -Uri $url -UseBasicParsing).StatusCode }
  catch { [int]$_.Exception.Response.StatusCode }
}
Status GET  http://localhost:8087/api/airports              # 200: público
Status GET  http://localhost:8087/api/bookings/internal/1    # 403: bloqueada
Status GET  http://localhost:8087/api/users                  # 401: pide token
Status POST http://localhost:8087/api/airports               # 401: crear exige rol
Invoke-RestMethod http://localhost:8087/actuator/health
```

## Problemas comunes

| Síntoma | Causa y solución |
|---|---|
| `Could not resolve placeholder 'JWT_SECRET'` | Falta definir la variable **en esa terminal**. |
| `504` | El servicio tardó más que el límite. Levantá el gateway con el tiempo ampliado (arriba) o liberá memoria. |
| `500` o `5xx` en una ruta | El servicio de destino está apagado o todavía arrancando. Mirá su puerto en la tabla de rutas. |
| `401` con un token que antes funcionaba | Venció (duran 15 minutos): iniciá sesión de nuevo. O el `JWT_SECRET` del gateway no coincide con el de `identity-service`. |
| `403` en una ruta que debería ser pública | Es `/api/bookings/internal/**`, bloqueada a propósito. |
| `429` | Más de 120 peticiones en un minuto desde la misma IP. Esperá un minuto. |
| El frontend no ve los datos / error de CORS en el navegador | Gateway apagado o en otro puerto. El frontend usa `VITE_GATEWAY_URL` (por defecto `http://localhost:8087`). |
| `Port 8087 was already in use` | **Linux:** `ss -ltnp \| grep 8087`. **Windows:** `netstat -ano \| findstr :8087`. |
