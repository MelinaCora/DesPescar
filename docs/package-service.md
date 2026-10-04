# package-service

← [Volver al índice](README.md)

Paquetes turísticos: un destino con un vuelo y un hotel asociados, la cantidad de noches y un precio base.

| | |
|---|---|
| Puerto | **8086** |
| Base de datos | `despescar_package` |
| Por el gateway | `/api/packages/**` |

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
cd services/package-service
mvn spring-boot:run
```

**Windows (PowerShell)**
```powershell
$env:JWT_SECRET = 'despescar-dev-secret-key-2026-must-be-long-enough'
cd services\package-service
mvn spring-boot:run
```

Está listo cuando el log dice `Started PackageServiceApplication`. Crea la tabla `tour_packages`. Arranca vacío: ver [datos de ejemplo](datos-de-ejemplo.md).

## Endpoints y permisos

| Método y ruta | Acceso |
|---|---|
| `GET /api/packages` (filtros opcionales `destination`, `active`, `minPrice`, `maxPrice`) y `GET /api/packages/{id}` | **Sesión iniciada** |
| `POST /api/packages`, `PUT /api/packages/{id}`, `DELETE /api/packages/{id}`, `POST /api/packages/{id}/activate` | Solo `SUPER_ADMIN` |

Campos de un paquete:

| Campo | Regla |
|---|---|
| `name` | Obligatorio, hasta 120 caracteres. No puede repetirse (responde `409 PACKAGE_DUPLICADO`). |
| `description` | Obligatorio, hasta 500 caracteres |
| `destination` | Obligatorio, hasta 120 caracteres |
| `flightNumber` | Opcional, hasta 50 caracteres (por ejemplo `AR1001`) |
| `hotelId` | Opcional, el UUID de un hotel |
| `durationNights` | Obligatorio, mayor que 0 |
| `basePrice` | Obligatorio, al menos 0,01 |

**`DELETE` no borra el paquete: lo desactiva** (`active = false`). Para volver a mostrarlo, `POST /api/packages/{id}/activate`.

## Probarlo

Hace falta un token. Con los usuarios de [datos de ejemplo](datos-de-ejemplo.md):

**Linux**
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"cliente@despescar.com","password":"Cliente123!"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")

curl -s "http://localhost:8086/api/packages?active=true" -H "Authorization: Bearer $TOKEN"
```

**Windows (PowerShell)**
```powershell
$t = Invoke-RestMethod -Method Post -Uri http://localhost:8080/auth/login -ContentType 'application/json' `
  -Body '{"email":"cliente@despescar.com","password":"Cliente123!"}'

Invoke-RestMethod -Uri "http://localhost:8086/api/packages?active=true" -Headers @{ Authorization = "Bearer $($t.accessToken)" }
```

Crear, editar o borrar paquetes exige el token del administrador (`admin@despescar.com`).

## Quién lo usa

- **Frontend (`features/merge-koi-gateway`):** hoy **no lo llama**.
- **Chatbot KOI:** su herramienta de paquetes consulta este servicio **sin token**, y por código `GET /api/packages` exige sesión (el mismo patrón que `hotel-service`, donde se comprobó el `403`). Por eso, tal como está el código, el chatbot probablemente no puede traer paquetes. Ver [`koi-ia-service.md`](koi-ia-service.md).

## Problemas comunes

| Síntoma | Causa y solución |
|---|---|
| `401` o `403` al consultar | Falta el encabezado `Authorization: Bearer <token>`, o el token venció (duran 15 minutos). |
| `403` al crear | El usuario no es `SUPER_ADMIN`; si cambió de rol, volver a iniciar sesión. |
| Un paquete "borrado" sigue en la lista | `DELETE` lo desactiva. Filtrá por `active=true` o reactivalo con `activate`. |
| `Could not resolve placeholder 'JWT_SECRET'` | Falta definir la variable **en esa terminal**. |
| `Port 8086 was already in use` | **Linux:** `ss -ltnp \| grep 8086`. **Windows:** `netstat -ano \| findstr :8086`. |
