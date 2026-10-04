# flightservice

← [Volver al índice](README.md)

Aeropuertos, aerolíneas, tarifas (Light, Standard…) y vuelos, incluida la búsqueda de ida y vuelta que usa el frontend.

| | |
|---|---|
| Puerto | **8081** |
| Base de datos | `despescar_flight` |
| Por el gateway | `/api/flights/**`, `/api/airlines/**`, `/api/airports/**` y `/api/baggage-policies/**` |

## Variables de entorno

| Variable | Obligatoria | Para qué |
|---|---|---|
| `JWT_SECRET` | **Sí** | Validar los tokens. El mismo valor en todos los servicios. |
| `DB_PASSWORD` | No | Solo si cambiaste la contraseña de MySQL. |

## Levantarlo

Con MySQL arriba ([`mysql-docker.md`](mysql-docker.md)), desde la raíz del repositorio:

**Linux**
```bash
export JWT_SECRET='despescar-dev-secret-key-2026-must-be-long-enough'
cd services/flightservice
mvn spring-boot:run
```

**Windows (PowerShell)**
```powershell
$env:JWT_SECRET = 'despescar-dev-secret-key-2026-must-be-long-enough'
cd services\flightservice
mvn spring-boot:run
```

Está listo cuando el log dice `Started FlightserviceApplication`. Crea las tablas `airports`, `airlines`, `flights`, `flight_fares` y `baggage_policies`. Arranca **vacío**: para tener vuelos, cargá los [datos de ejemplo](datos-de-ejemplo.md).

## Qué es público y qué no

Consultar vuelos **no exige iniciar sesión**, tanto en este servicio como en el [gateway](gateway-service.md). El resto sí.

| Método y ruta | Acceso |
|---|---|
| `GET /api/flights`, `GET /api/flights/search`, `GET /api/flights/{id}` | **Público** |
| `GET /api/airports` y `GET /api/airports/code/{code}` | **Público** |
| `GET /api/fares` | Público en este servicio, pero **el gateway no tiene ruta** para `/api/fares` (devuelve 404 por el 8087) |
| Otras consultas (`/api/airlines`, `/api/airports/country/...`, `/api/airports/city/...`, `/api/flights/number/...`, `/airline/`, `/origin/`, `/destination/`, `/api/fares/{id}`) | Sesión iniciada |
| `POST`, `PUT` y `DELETE` en `/api/**` (crear, editar y borrar) | `SUPER_ADMIN` o `AIRLINE_ADMIN` |
| `PATCH /api/flights/number/{flightNumber}/seats?delta=N` | Uso interno de [`reservation-service`](reservation-service.md) (ajusta los asientos disponibles) |

## Buscar vuelos

`GET /api/flights/search` con estos parámetros, todos obligatorios salvo `returnDate`:

| Parámetro | Ejemplo | Nota |
|---|---|---|
| `origin` | `EZE` | Código IATA del aeropuerto |
| `destination` | `COR` | Código IATA |
| `departureDate` | `2026-10-18` | Formato `AAAA-MM-DD` |
| `returnDate` | `2026-10-21` | Opcional: si va, devuelve también vuelos de vuelta |
| `passengers` | `1` | Cantidad de pasajeros |

La respuesta trae `departureFlights`, `returnFlights` y `metadata` (con `totalResults`). Cada vuelo incluye la aerolínea, las **tarifas** disponibles (con lo que incluyen y su precio), el itinerario y el precio. **Solo hay resultados si existe un vuelo con ese origen, destino y fecha exactos.**

## Probarlo

Con los [datos de ejemplo](datos-de-ejemplo.md) cargados, hay vuelos 14 días después de la carga.

**Linux**
```bash
FECHA=$(date -d '+14 days' +%F)      # la fecha de ida
curl -s "http://localhost:8081/api/airports"                      # lista de aeropuertos
curl -s "http://localhost:8081/api/flights/search?origin=EZE&destination=COR&departureDate=$FECHA&passengers=1"
```

**Windows (PowerShell)**
```powershell
$fecha = (Get-Date).AddDays(14).ToString('yyyy-MM-dd')    # la fecha de ida
Invoke-RestMethod -Uri http://localhost:8081/api/airports
Invoke-RestMethod -Uri "http://localhost:8081/api/flights/search?origin=EZE&destination=COR&departureDate=$fecha&passengers=1"
```

Si corriste el script otro día, usá la fecha que imprimió. Para probar por el gateway, cambiá `8081` por `8087`; funciona sin token.

## Problemas comunes

| Síntoma | Causa y solución |
|---|---|
| `Could not resolve placeholder 'JWT_SECRET'` | Falta definir la variable **en esa terminal**. |
| La lista de aeropuertos viene vacía (`[]`) | No se cargaron los datos: ver [`datos-de-ejemplo.md`](datos-de-ejemplo.md). |
| La búsqueda devuelve `500` con "Required request parameter ... is not present" | Falta un parámetro obligatorio (`departureDate` o `passengers`). |
| La búsqueda devuelve 0 vuelos | No hay vuelos con ese origen, destino y fecha exactos. Probá con una fecha de las que imprimió el script. |
| `403` al crear un vuelo o aeropuerto | El usuario no es `SUPER_ADMIN` ni `AIRLINE_ADMIN`, o el rol se asignó después de iniciar sesión (volvé a iniciar sesión). |
| `Port 8081 was already in use` | **Linux:** `ss -ltnp \| grep 8081`. **Windows:** `netstat -ano \| findstr :8081`. |
