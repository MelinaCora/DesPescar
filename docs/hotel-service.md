# hotel-service

← [Volver al índice](README.md)

Catálogo de hoteles: nombre, ciudad, estrellas, precio por noche y habitaciones disponibles.

| | |
|---|---|
| Puerto | **8083** |
| Base de datos | `despescar_hotel` |
| Por el gateway | `/hoteles/**` y `/api/hotels/**` (esta última se reescribe a `/hoteles/**`) |

## Variables de entorno

| Variable | Obligatoria | Para qué |
|---|---|---|
| `JWT_SECRET` | **Sí** | Validar los tokens. El mismo valor en todos los servicios. Ya no tiene valor por defecto. |
| `INVENTORY_SERVICE_TOKEN` | **Para que reservas descuente habitaciones** | Contraseña compartida con [`reservation-service`](reservation-service.md) (y `flightservice`). **El mismo valor en todos.** Vacía, el ajuste de habitaciones se rechaza con `401`. |
| `DB_PASSWORD` | No | Solo si cambiaste la contraseña de MySQL. |

## Levantarlo

> **Primero:** instalá el [módulo común](README.md#antes-de-levantar-los-servicios-instalar-el-módulo-común) (`mvn -q -f services/common-security/pom.xml install`, una sola vez).

Con MySQL arriba ([`mysql-docker.md`](mysql-docker.md)), desde la raíz del repositorio:

**Linux**
```bash
export JWT_SECRET='despescar-dev-secret-key-2026-must-be-long-enough'
export INVENTORY_SERVICE_TOKEN='despescar-dev-inventory-token'
cd services/hotel-service
mvn spring-boot:run
```

**Windows (PowerShell)**
```powershell
$env:JWT_SECRET = 'despescar-dev-secret-key-2026-must-be-long-enough'
$env:INVENTORY_SERVICE_TOKEN = 'despescar-dev-inventory-token'
cd services\hotel-service
mvn spring-boot:run
```

Está listo cuando el log dice `Started HotelServiceApplication`. Crea la tabla `hoteles`. Arranca vacío: ver [datos de ejemplo](datos-de-ejemplo.md).

## Endpoints y permisos

| Método y ruta | Acceso |
|---|---|
| `GET /hoteles`, `GET /hoteles/{id}`, `GET /hoteles/ciudad/{city}` | **Sesión iniciada** (sin token responde `403`) |
| `POST /hoteles`, `PUT /hoteles/{id}`, `DELETE /hoteles/{id}` | `SUPER_ADMIN` o `HOTEL_ADMIN` |
| `PATCH /hoteles/{id}/rooms` | **Solo [`reservation-service`](reservation-service.md)**, con el encabezado `X-Internal-Service-Token` (`INVENTORY_SERVICE_TOKEN`). Un token de usuario, aunque sea de administrador, responde `401`. El gateway lo bloquea (`403`). |

Campos de un hotel: `nombre`, `ciudad`, `direccion`, `estrellas` (1 a 5), `precioPorNoche` (mayor o igual a 0), `habitacionesDisponibles` (mayor o igual a 0) y `allInclusive` (opcional).

## Probarlo

Hace falta un token. Con los usuarios de [datos de ejemplo](datos-de-ejemplo.md):

**Linux**
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"cliente@despescar.com","password":"Cliente123!"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")

curl -s http://localhost:8083/hoteles/ciudad/Mendoza -H "Authorization: Bearer $TOKEN"
```

**Windows (PowerShell)**
```powershell
$t = Invoke-RestMethod -Method Post -Uri http://localhost:8080/auth/login -ContentType 'application/json' `
  -Body '{"email":"cliente@despescar.com","password":"Cliente123!"}'

Invoke-RestMethod -Uri http://localhost:8083/hoteles/ciudad/Mendoza -Headers @{ Authorization = "Bearer $($t.accessToken)" }
```

Para crear hoteles hay que usar el token del administrador (`admin@despescar.com`).

## Quién lo usa

- **Frontend (`features/merge-koi-gateway`):** hoy **no lo llama**. Las pantallas de administración de hoteles trabajan con datos de ejemplo del propio frontend (con un `TODO` para conectarlas), y la compra de vuelos envía `hotelId` vacío.
- **Chatbot KOI:** su herramienta de hoteles consulta `GET /hoteles/ciudad/{ciudad}` **sin token**, y este servicio exige sesión. Se comprobó que sin token responde `403` y con token `200`, por lo que, tal como está el código, el chatbot no puede traer hoteles aunque este servicio esté encendido. Ver [`koi-ia-service.md`](koi-ia-service.md).

## Problemas comunes

| Síntoma | Causa y solución |
|---|---|
| `Could not find artifact com.despescar:common-security` | Falta instalar el [módulo común](README.md#antes-de-levantar-los-servicios-instalar-el-módulo-común). |
| `403` al consultar hoteles | Falta el encabezado `Authorization: Bearer <token>`, o el token venció (duran 15 minutos). |
| `403` al crear o editar | El usuario no es `SUPER_ADMIN` ni `HOTEL_ADMIN`; si cambió de rol, volver a iniciar sesión. |
| Lista vacía | No se cargaron datos: ver [`datos-de-ejemplo.md`](datos-de-ejemplo.md). |
| `Port 8083 was already in use` | **Linux:** `ss -ltnp \| grep 8083`. **Windows:** `netstat -ano \| findstr :8083`. |
