# MySQL y Adminer con Docker

← [Volver al índice](README.md)

Con Docker corre **solo la base de datos**. Los microservicios se arrancan con Maven y se conectan a este MySQL. Todos usan la misma contraseña de desarrollo (`despescar_dev`), así nadie tiene que instalar MySQL ni cambiar archivos.

| Qué | Dónde |
|---|---|
| MySQL 8 con las 7 bases `despescar_*` | `localhost:3306` |
| Adminer (ver las tablas desde el navegador) | http://localhost:8090 |
| Archivos | carpeta [`database/`](../database): `docker-compose.yml`, `init/01_create_databases.sql`, `.env.example` |

Las bases son `despescar_identity`, `despescar_flight`, `despescar_hotel`, `despescar_package`, `despescar_reservation`, `despescar_payment` y `despescar_koiia`. Las tablas las crea Hibernate cuando arranca cada servicio.

> La teoría de Docker y la instalación paso a paso en Linux están en [`../database/README.md`](../database/README.md).

## Requisitos

- **Linux:** Docker con Compose v2 (`docker compose version` tiene que responder).
- **Windows:** [Docker Desktop](https://www.docker.com/products/docker-desktop/). Necesita virtualización activada y WSL 2 (o Hyper-V); el instalador lo indica. Ver la documentación oficial en https://docs.docker.com/desktop/.
- Los puertos **3306** y **8090** tienen que estar libres.

## Levantarlo

Desde la raíz del repositorio. Los comandos son iguales en Linux y en Windows:

```bash
cd database
docker compose up -d
docker compose ps
```

- **Linux:** si Docker no está arrancado, primero `sudo systemctl start docker`.
- **Windows:** abrí **Docker Desktop** y esperá a que diga *Engine running* antes de ejecutar los comandos.

Esperá a que `despescar-mysql` diga **healthy**. La primera vez descarga las imágenes e inicializa la base: en un equipo lento puede tardar **hasta 3 minutos**, y `docker compose up -d` espera a que termine antes de levantar Adminer. Es normal; no lo canceles.

No hace falta crear ningún `.env`: la contraseña de `root` es `despescar_dev` por defecto, tanto en Docker como en los `application.properties` de los servicios.

## Entrar a Adminer

http://localhost:8090 → Sistema **MySQL**, servidor `mysql`, usuario `root`, contraseña `despescar_dev`.

## Día a día

```bash
cd database
docker compose up -d       # prender MySQL
docker compose down        # apagarlo (los datos se conservan)
docker compose logs mysql  # ver los logs
```

## Empezar de cero

```bash
cd database
docker compose down -v     # BORRA todos los datos de la base
docker compose up -d
```

Después hay que volver a arrancar los servicios (para que recreen las tablas) y volver a cargar los [datos de ejemplo](datos-de-ejemplo.md).

## Cambiar la contraseña (opcional)

La contraseña solo se aplica al **crear** el volumen. Si querés otra distinta de `despescar_dev`:

**Linux**
```bash
cd database
cp .env.example .env       # y editá DB_PASSWORD en .env
docker compose down -v && docker compose up -d
export DB_PASSWORD='tu-clave'   # en cada terminal donde arranques un servicio
```

**Windows (PowerShell)**
```powershell
cd database
Copy-Item .env.example .env     # y editá DB_PASSWORD en .env
docker compose down -v; docker compose up -d
$env:DB_PASSWORD = 'tu-clave'   # en cada terminal donde arranques un servicio
```

## Problemas comunes

| Síntoma | Causa y solución |
|---|---|
| `Access denied for user 'root'` | El volumen se creó antes con otra contraseña. Hacé `docker compose down -v` y volvé a levantar. |
| `Unknown database 'despescar_...'` | El volumen es anterior a los scripts de `database/init`. Mismo arreglo: `down -v`. |
| `docker compose up -d` falla con "dependency failed to start: ... unhealthy" | Versión anterior del compose, que marcaba MySQL como no saludable durante la primera inicialización. La actual ya tiene un periodo de gracia de 5 minutos: actualizá el repositorio. |
| `Port 3306 is already in use` | Hay otro MySQL en el equipo. **Linux:** `sudo systemctl stop mysql`. **Windows:** abrí *Servicios* (`services.msc`), buscá el servicio de MySQL (el nombre puede variar, por ejemplo `MySQL80`) y detenelo. |
| `Port 8090 is already in use` | Otro programa usa el 8090. **Linux:** `ss -ltnp \| grep 8090`. **Windows:** `netstat -ano \| findstr :8090`. |
| `Cannot connect to the Docker daemon` / `error during connect` | Docker está apagado. **Linux:** `sudo systemctl start docker` (y `sudo systemctl enable docker` para que arranque solo). **Windows:** abrí Docker Desktop. |
| **Windows:** el contenedor no ve la carpeta `init` (no se crean las bases) | Si usás el modo Hyper-V y el repositorio está en una unidad distinta de `C:`, habilitá el uso compartido de esa unidad en *Settings → Resources → File sharing*, o clonalo en `C:`. Con WSL 2 normalmente no hace falta. Para repetir la creación de las bases: `docker compose down -v` y levantar de nuevo. |
| Todo se vuelve muy lento | Falta de memoria. Cerrá programas y levantá menos servicios a la vez. En Windows, Docker Desktop con WSL 2 también consume RAM. |

## Contenedores y volúmenes

- Los contenedores se llaman `despescar-mysql` y `despescar-adminer`, y tienen reinicio automático: **vuelven a arrancar solos** cuando se inicia Docker.
- Los datos viven en el volumen `database_mysql_data` (el prefijo es el nombre de la carpeta `database`). `docker volume ls` lo muestra.
