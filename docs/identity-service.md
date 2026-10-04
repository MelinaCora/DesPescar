# identity-service

← [Volver al índice](README.md)

Registro, inicio de sesión, tokens y roles. Es el servicio del que dependen todos los demás para saber quién es el usuario.

| | |
|---|---|
| Puerto | **8080** |
| Base de datos | `despescar_identity` |
| Por el gateway | `/api/auth/**` → `/auth/**` y `/api/users/**` → `/users/**` |

## Variables de entorno

| Variable | Obligatoria | Para qué |
|---|---|---|
| `JWT_SECRET` | **Sí** | Firma los tokens. **Tiene que ser el mismo valor en todos los servicios.** |
| `DB_PASSWORD` | No | Solo si cambiaste la contraseña de MySQL (por defecto `despescar_dev`). |

## Levantarlo

Con MySQL arriba ([`mysql-docker.md`](mysql-docker.md)), desde la raíz del repositorio:

**Linux**
```bash
export JWT_SECRET='despescar-dev-secret-key-2026-must-be-long-enough'
cd services/identity-service
mvn spring-boot:run
```

**Windows (PowerShell)**
```powershell
$env:JWT_SECRET = 'despescar-dev-secret-key-2026-must-be-long-enough'
cd services\identity-service
mvn spring-boot:run
```

Está listo cuando el log dice `Started IdentityServiceApplication`. Al arrancar crea las tablas (`users`, `roles`, `permissions`, `user_roles`, `role_permissions`, `refresh_tokens`) y los **4 roles** y **8 permisos** básicos.

## Roles

| Rol | Para qué |
|---|---|
| `USER` | Cliente: ver vuelos y hoteles y gestionar sus propias reservas |
| `SUPER_ADMIN` | Administrador global (usuarios, vuelos, hoteles, paquetes, reservas) |
| `AIRLINE_ADMIN` | Administra vuelos y aerolíneas |
| `HOTEL_ADMIN` | Administra hoteles |

Quien se registra queda siempre como `USER`. El primer `SUPER_ADMIN` hay que asignarlo directamente en la base de datos (asignar roles ya exige ser `SUPER_ADMIN`): lo hace el script de [datos de ejemplo](datos-de-ejemplo.md), que también explica cómo hacerlo a mano.

Para los demás servicios el rol `USER` equivale a **cliente** (`ROLE_CLIENTE` en reservas y pagos).

## Tokens y bloqueo

- El **token de acceso** dura **15 minutos** y el de **renovación** (`refresh`) 7 días.
- Tras **5 intentos fallidos** de inicio de sesión la cuenta se bloquea **15 minutos** y el servicio responde `423`.

## Endpoints

| Método y ruta | Quién puede |
|---|---|
| `POST /auth/register` | Cualquiera. Cuerpo: `firstName` y `lastName` (2 a 50 caracteres), `email`, `password` (6 a 100). Un correo repetido responde `409`. |
| `POST /auth/login` | Cualquiera. Cuerpo: `email` y `password`. Devuelve `accessToken` y `refreshToken`. |
| `POST /auth/refresh` | Cualquiera, con un `refreshToken` válido en el cuerpo. |
| `POST /auth/logout` | Cualquiera, con el `refreshToken` en el cuerpo (cierra esa sesión). |
| `GET /auth/me` | Sesión iniciada. Devuelve solo correo y rol. |
| `GET /users/me` y `GET /users/me/roles` | Sesión iniciada. `/users/me` devuelve los datos del usuario, incluido su `id`, que usa el frontend. |
| `GET /users`, `GET /users/{id}`, `GET /users/roles` | `SUPER_ADMIN` |
| `POST /users/{id}/roles` y `DELETE /users/{id}/roles/{roleId}` | `SUPER_ADMIN`. El cuerpo de `POST` lleva `roleName` y, opcionalmente, `airlineId` y `hotelId`. |

> Los campos `airlineId` y `hotelId` de la asignación de roles son numéricos, mientras que en `flightservice` y `hotel-service` los identificadores son UUID. Conviene revisarlo con el equipo antes de usar roles con alcance.

## Probarlo

**Linux**
```bash
curl -s -X POST http://localhost:8080/auth/register -H 'Content-Type: application/json' \
  -d '{"firstName":"Ana","lastName":"Perez","email":"ana@mail.com","password":"Clave123!"}'

TOKEN=$(curl -s -X POST http://localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"ana@mail.com","password":"Clave123!"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")

curl -s http://localhost:8080/users/me -H "Authorization: Bearer $TOKEN"
```

**Windows (PowerShell)**
```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/auth/register -ContentType 'application/json' `
  -Body '{"firstName":"Ana","lastName":"Perez","email":"ana@mail.com","password":"Clave123!"}'

$t = Invoke-RestMethod -Method Post -Uri http://localhost:8080/auth/login -ContentType 'application/json' `
  -Body '{"email":"ana@mail.com","password":"Clave123!"}'

Invoke-RestMethod -Uri http://localhost:8080/users/me -Headers @{ Authorization = "Bearer $($t.accessToken)" }
```

Un registro correcto devuelve `id`, `firstName`, `lastName`, `email` y `role` (`USER`).

## Problemas comunes

| Síntoma | Causa y solución |
|---|---|
| `Could not resolve placeholder 'JWT_SECRET'` | Falta definir la variable **en esa terminal** (ver arriba). |
| `Port 8080 was already in use` | **Linux:** `ss -ltnp \| grep 8080`. **Windows:** `netstat -ano \| findstr :8080`. Es el puerto de este servicio: ya hay otra instancia u otro programa. |
| Error de conexión o `Access denied` con MySQL | MySQL apagado o con otra contraseña. Ver [`mysql-docker.md`](mysql-docker.md). |
| `423` al iniciar sesión | La cuenta se bloqueó por 5 intentos fallidos: esperá 15 minutos. |
| Los demás servicios rechazan el token (`401`) | `JWT_SECRET` distinto entre servicios. Tiene que ser idéntico en todos. |
| `403` en `/users` | El usuario no es `SUPER_ADMIN`. Después de cambiar el rol hay que **volver a iniciar sesión**, porque el rol viaja dentro del token. |
