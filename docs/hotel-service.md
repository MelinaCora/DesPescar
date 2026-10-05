# hotel-service

← [Volver al índice](README.md)

Catálogo de hoteles: búsqueda por destino y fechas, detalle con habitaciones cotizadas y alta de hoteles completos (tipos de habitación, servicios y política de cancelación).

| | |
|---|---|
| Puerto | **8083** |
| Base de datos | `despescar_hotel` |
| Por el gateway | `/api/hotels/**` (se reescribe a `/hoteles/**`) y `/hoteles/**` |

## Variables de entorno

| Variable | Obligatoria | Para qué |
|---|---|---|
| `JWT_SECRET` | **Sí** | Validar los tokens. El mismo valor en todos los servicios. Ya no tiene valor por defecto. |
| `DB_PASSWORD` | No | Solo si cambiaste la contraseña de MySQL. |

## Levantarlo

> **Primero:** instalá el [módulo común](README.md#antes-de-levantar-los-servicios-instalar-el-módulo-común) (`mvn -q -f services/common-security/pom.xml install`, una sola vez).

Con MySQL arriba ([`mysql-docker.md`](mysql-docker.md)), desde la raíz del repositorio:

**Linux**
```bash
export JWT_SECRET='despescar-dev-secret-key-2026-must-be-long-enough'
cd services/hotel-service
mvn spring-boot:run
```

**Windows (PowerShell)**
```powershell
$env:JWT_SECRET = 'despescar-dev-secret-key-2026-must-be-long-enough'
cd services\hotel-service
mvn spring-boot:run
```

Está listo cuando el log dice `Started HotelServiceApplication`. Crea las tablas (`hoteles`, `tipos_habitacion`, `retenciones` y las de imágenes, servicios y política de cancelación). Arranca vacío: ver [datos de ejemplo](datos-de-ejemplo.md).

> **Migración:** si ya tenías una base `despescar_hotel` de la versión anterior (con `precioPorNoche` y `habitacionesDisponibles` en el hotel), hay que recrearla; ver [Migración](#migración).

## Endpoints y permisos

Por el gateway llevan el prefijo `/api/hotels` (por ejemplo `GET /api/hotels/destinos`); directo al servicio, `/hoteles`.

| Método y ruta | Acceso | Qué hace |
|---|---|---|
| `GET /hoteles?destino=&checkIn=&checkOut=&huespedes=` | **Pública** | Busca hoteles. Todos los parámetros son opcionales |
| `GET /hoteles/destinos` | **Pública** | Lista de destinos (`ciudad` y `pais`) con hoteles activos, sin repetir y ordenados |
| `GET /hoteles/{id}?checkIn=&checkOut=&huespedes=` | **Pública** | Detalle del hotel con sus habitaciones; con fechas, también la cotización |
| `POST /hoteles` | `SUPER_ADMIN` o `HOTEL_ADMIN` | Crea un hotel completo con sus habitaciones. Responde `201` con el detalle |

Cualquier otra ruta exige sesión. Sin token responde `401`; con token pero sin el rol, `403`. Ya no existen `GET /hoteles/ciudad/{city}`, `PUT` ni `DELETE /hoteles/{id}`, `PATCH /hoteles/{id}/rooms` ni `GET /test`.

### Parámetros de búsqueda y detalle

| Parámetro | Detalle |
|---|---|
| `destino` | Texto libre: busca dentro de la ciudad o del país, sin distinguir mayúsculas ni tildes |
| `checkIn`, `checkOut` | Formato `AAAA-MM-DD`. Van juntos o ninguno. El check-out tiene que ser posterior al check-in, el check-in no puede ser pasado y la estadía es de hasta 30 noches |
| `huespedes` | De 1 a 10 (1 si no se indica) |

Sin fechas se muestran los precios por noche (`precioDesde`) y `disponible`, `precioTotalDesde` y la cotización de cada habitación quedan en `null`. Con fechas, cada tipo de habitación informa `unidadesLibres`, `habitacionesNecesarias` (cuántas hacen falta para los huéspedes según su capacidad), `precioTotal` de la estadía y `disponible`.

## Modelo

- **Hotel:** `nombre`, `ciudad`, `pais`, `direccion`, `estrellas` (1 a 5), `descripcion`, `allInclusive`, `imagenes` (URLs `https`), `servicios`, `politicaCancelacion`, `horaCheckIn` (14:00 por defecto), `zonaHoraria` (`America/Argentina/Buenos_Aires` por defecto) y `adminUserId` (opcional). Además guarda `calificacionPromedio` y `cantidadResenas`.
- **Servicios** (`servicios`): `WIFI`, `PILETA`, `DESAYUNO`, `ESTACIONAMIENTO`, `GIMNASIO`, `SPA`, `AIRE_ACONDICIONADO`, `RESTAURANTE`, `MASCOTAS`, `TRASLADO`.
- **Tipo de habitación** (`habitaciones`, al menos una): `nombre`, `descripcion`, `capacidad` (1 a 10), `precioPorNoche` (mayor a 0), `cantidadUnidades` (1 a 500) e `imagenes`. El precio y el stock viven acá, no en el hotel.
- **Política de cancelación** (`politicaCancelacion`, al menos un tramo): lista de `{horasAntes, porcentajeReembolso}`, que se lee "si se cancela con al menos `horasAntes` de anticipación, se reembolsa `porcentajeReembolso`%". Las horas no se repiten y el reembolso no puede aumentar a medida que se acerca la fecha. Ejemplo: `[{"horasAntes":24,"porcentajeReembolso":100},{"horasAntes":0,"porcentajeReembolso":0}]`.
- **Retenciones** (tabla `retenciones`): unidades de un tipo de habitación tomadas por una reserva para un rango de noches, con estado `RETENIDA`, `CONFIRMADA` o `LIBERADA` y vencimiento. La disponibilidad se calcula por fecha restando las retenciones vigentes que se solapan con la estadía; ya no hay un contador de habitaciones. Todavía no hay endpoints que las creen (llegan con el carrito y la reserva).

## Errores

Todos los errores tienen el formato `{"error": "mensaje"}`:

| Código | Cuándo |
|---|---|
| `400` | Fechas incompletas o inválidas, huéspedes fuera de rango, parámetro con formato inválido, cuerpo ilegible o política de cancelación incoherente |
| `400` con `campos` | Falla la validación del alta: `{"error": "Datos inválidos", "campos": {"estrellas": "..."}}` |
| `404` | El hotel no existe o está inactivo |
| `401` / `403` | Sin token / sin el rol necesario (alta) |

## Probarlo

Las lecturas no necesitan token. Con los datos de [datos de ejemplo](datos-de-ejemplo.md), por el gateway:

```bash
curl -s http://localhost:8087/api/hotels/destinos

curl -s "http://localhost:8087/api/hotels?destino=mendoza&checkIn=2026-11-10&checkOut=2026-11-13&huespedes=2"

curl -s "http://localhost:8087/api/hotels/<id-del-hotel>?checkIn=2026-11-10&checkOut=2026-11-13&huespedes=2"
```

(Usá fechas futuras; las de arriba son de ejemplo.) En PowerShell: `Invoke-RestMethod "http://localhost:8087/api/hotels/destinos"`.

Para crear un hotel hace falta el token del administrador (`admin@despescar.com`):

**Linux**
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@despescar.com","password":"Admin2026!"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")

curl -s -X POST http://localhost:8087/api/hotels -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{
  "nombre": "Hotel Ejemplo", "ciudad": "Salta", "pais": "Argentina", "direccion": "Calle 123",
  "estrellas": 4, "descripcion": "Hotel de prueba.", "allInclusive": false,
  "imagenes": ["https://example.com/hotel.jpg"],
  "servicios": ["WIFI", "DESAYUNO"],
  "politicaCancelacion": [{"horasAntes": 48, "porcentajeReembolso": 100}, {"horasAntes": 0, "porcentajeReembolso": 0}],
  "habitaciones": [{"nombre": "Doble", "descripcion": "Dos camas.", "capacidad": 2, "precioPorNoche": 90000,
                    "cantidadUnidades": 10, "imagenes": ["https://example.com/doble.jpg"]}]
}'
```

**Windows (PowerShell)**
```powershell
$t = Invoke-RestMethod -Method Post -Uri http://localhost:8080/auth/login -ContentType 'application/json' `
  -Body '{"email":"admin@despescar.com","password":"Admin2026!"}'

$hotel = @{ nombre='Hotel Ejemplo'; ciudad='Salta'; pais='Argentina'; direccion='Calle 123'; estrellas=4
  descripcion='Hotel de prueba.'; allInclusive=$false; imagenes=@('https://example.com/hotel.jpg')
  servicios=@('WIFI','DESAYUNO')
  politicaCancelacion=@(@{horasAntes=48;porcentajeReembolso=100}, @{horasAntes=0;porcentajeReembolso=0})
  habitaciones=@(@{nombre='Doble';descripcion='Dos camas.';capacidad=2;precioPorNoche=90000;cantidadUnidades=10;imagenes=@('https://example.com/doble.jpg')}) } | ConvertTo-Json -Depth 5
Invoke-RestMethod -Method Post -Uri http://localhost:8087/api/hotels -ContentType 'application/json' -Headers @{ Authorization = "Bearer $($t.accessToken)" } -Body $hotel
```

## Migración

El modelo cambió: el precio y la cantidad de habitaciones pasaron del hotel a cada tipo de habitación y la disponibilidad se calcula por fecha. Como `ddl-auto=update` no elimina las columnas viejas obligatorias, con una base `despescar_hotel` anterior el alta falla con `500` y el seed se corta. Además, los paquetes guardan ids de hoteles que dejan de existir, así que hay que **recrear `despescar_hotel` y `despescar_package`**:

1. Frená `hotel-service` y `package-service`.
2. Recreá las bases:
   ```bash
   # Con Docker
   docker exec -it despescar-mysql mysql -uroot -p -e "DROP DATABASE despescar_hotel; CREATE DATABASE despescar_hotel; DROP DATABASE despescar_package; CREATE DATABASE despescar_package;"
   # Con MySQL instalado
   mysql -u<usuario> -p -e "DROP DATABASE despescar_hotel; CREATE DATABASE despescar_hotel; DROP DATABASE despescar_package; CREATE DATABASE despescar_package;"
   ```
3. Volvé a compilar y levantar `hotel-service`, `package-service` y el gateway.
4. Corré de nuevo el seed ([datos de ejemplo](datos-de-ejemplo.md)).
5. Verificá sin token: `curl http://localhost:8087/api/hotels/destinos` debe devolver 6 destinos.

## Quién lo usa

- **Frontend (`features/hoteles`):** el buscador del Home tiene la pestaña Hoteles, con la página de resultados (`/hoteles`) y el detalle (`/hoteles/:id`), que usan las tres lecturas públicas. Las pantallas de administración de hoteles siguen con datos de ejemplo del propio frontend, y la compra de vuelos envía `hotelId` vacío.
- **Reservas con `hotelId`:** siguen sin funcionar, ya que el ajuste de habitaciones se quitó; se rehacen con el carrito en una etapa posterior.
- **Chatbot KOI:** su herramienta de hoteles consulta `GET /hoteles/ciudad/{ciudad}`, que ya no existe, así que por ahora no trae hoteles. Ver [`koi-ia-service.md`](koi-ia-service.md).

## Problemas comunes

| Síntoma | Causa y solución |
|---|---|
| `Could not find artifact com.despescar:common-security` | Falta instalar el [módulo común](README.md#antes-de-levantar-los-servicios-instalar-el-módulo-común). |
| `401` al crear | Falta el encabezado `Authorization: Bearer <token>`, o el token venció (duran 15 minutos). |
| `403` al crear | El usuario no es `SUPER_ADMIN` ni `HOTEL_ADMIN`; si cambió de rol, volver a iniciar sesión. |
| `500` al crear con una base vieja | Falta recrear `despescar_hotel`: ver [Migración](#migración). |
| Lista vacía | No se cargaron datos: ver [`datos-de-ejemplo.md`](datos-de-ejemplo.md). |
| `400` "Indicá check-in y check-out." | Se mandó solo una de las dos fechas. |
| `Port 8083 was already in use` | **Linux:** `ss -ltnp \| grep 8083`. **Windows:** `netstat -ano \| findstr :8083`. |
