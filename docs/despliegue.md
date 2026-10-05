# Despliegue en produccion (Docker + Caddy + Aiven)

Los microservicios se empaquetan en imagenes Docker y se levantan con `docker-compose.prod.yml`. **Caddy** recibe el trafico de internet, saca el certificado HTTPS y lo reparte. La base de datos es externa (**Aiven MySQL**). `package-service` queda desactivado (hay instrucciones para activarlo en el compose).

```
Internet ─► Caddy (80/443, HTTPS)
              ├─ /ws-despescar* ─► reservation-service:8085   (WebSocket de asientos)
              └─ todo lo demas ──► gateway-service:8087 ─► resto de servicios
```

Solo Caddy publica puertos. Los servicios se hablan por nombre dentro de la red de Docker.

## Archivos

| Archivo | Para que sirve |
|---|---|
| `Dockerfile` | Imagen generica: `--build-arg SERVICE=<carpeta de services/>`. Compila tambien `common-security` |
| `docker-compose.prod.yml` | Los servicios y Caddy, con las variables de cada uno |
| `deploy/Caddyfile` | Rutas de Caddy y HTTPS automatico |
| `.env.example` | Plantilla del `.env` (el `.env` real no se sube a git) |

## Variables de entorno

Todas tienen un valor por defecto pensado para desarrollo local, asi que sin definirlas todo sigue funcionando como antes.

| Variable | Servicios | Por defecto | Para que sirve |
|---|---|---|---|
| `DB_HOST`, `DB_PORT` | los 7 con base | `localhost`, `3306` | Donde esta MySQL |
| `DB_USER`, `DB_PASSWORD` | los 7 con base | `root`, `despescar_dev` | Credenciales de MySQL |
| `DB_SSL_MODE` | los 7 con base | `PREFERRED` | Aiven exige `REQUIRED` |
| `DB_POOL_SIZE` | los 7 con base | `10` | Conexiones por servicio (en Aiven gratis, `3`) |
| `JPA_SHOW_SQL` | los 7 con base | `true` | Mostrar SQL en los logs |
| `FLIGHT_SERVICE_URL`, `HOTEL_SERVICE_URL`, `PACKAGE_SERVICE_URL` | gateway, reservation, koi | `http://localhost:8081`, `:8083`, `:8086` | Direccion de esos servicios |
| `IDENTITY_SERVICE_URL`, `RESERVATION_SERVICE_URL`, `PAYMENT_SERVICE_URL`, `KOI_SERVICE_URL` | gateway (reservation tambien lo usa payment) | `localhost` + su puerto | Direccion de esos servicios |
| `CORS_ALLOWED_ORIGINS` | gateway, koi | `*` (gateway), `http://localhost:5173` (koi) | Origen del front permitido |
| `JWT_SECRET`, `INVENTORY_SERVICE_TOKEN`, `RESERVATION_SERVICE_SYNC_TOKEN` | varios | sin valor | Secretos: usar valores propios y largos |

## Aiven

- Aiven crea solo la base `defaultdb`: hay que crear las bases de `database/init/01_create_databases.sql` (sin `despescar_package` si no se usa ese servicio).
- Las tablas las crea cada servicio al arrancar (`ddl-auto=update`).
- Los datos de ejemplo se cargan despues con `database/seed/seed.sh` apuntando al gateway ya desplegado.
