# Instructivo: levantar el backend de DesPescar con Docker

Con Docker corre **solo la base de datos** (MySQL y Adminer). Los microservicios se arrancan con Maven, como siempre, y se conectan a ese MySQL. Así nadie tiene que instalar MySQL ni usar una contraseña distinta: todos usan la misma, `despescar_dev`.

> Todo lo de este documento es **para desarrollo local**. Las credenciales de ejemplo no sirven para producción.

## Qué vas a tener al final

| Qué | Dónde | Cómo se levanta |
|---|---|---|
| MySQL 8 (7 bases `despescar_*`) | `localhost:3306` | Docker |
| Adminer (ver las tablas desde el navegador) | http://localhost:8090 | Docker |
| `identity-service` | 8080 | Maven |
| `flightservice` | 8081 | Maven |
| `hotel-service` | 8083 | Maven |
| `payment-service` | 8084 | Maven |
| `reservation-service` | 8085 | Maven |
| `package-service` | 8086 | Maven |
| `gateway-service` (entrada del frontend) | 8087 | Maven |
| `koi-ia-service` (chatbot) | 8088 | Maven |

## Requisitos

- **Docker** con Compose v2 (`docker compose version` tiene que responder). La guía de instalación está en [`database/README.md`](../database/README.md).
- **Java 17 o superior** y **Maven 3.8 o superior** (`java -version`, `mvn -v`).
- **Python 3** (solo para cargar los datos de ejemplo).
- **RAM**: se recomiendan 8 GB. Con menos, arranca solo los servicios que necesites (ver "Problemas comunes").
- Puertos libres: 3306, 8080, 8081, 8083, 8084, 8085, 8086, 8087, 8088 y 8090.

## 1. Clonar el repositorio

```bash
git clone https://github.com/MelinaCora/DesPescar.git
cd DesPescar
git switch features/docker-setup
```

## 2. Levantar MySQL y Adminer

```bash
cd database
docker compose up -d
docker compose ps
```

Esperá a que `despescar-mysql` diga **healthy** (la primera vez descarga las imágenes y tarda un poco). En el primer arranque se crean solas las 7 bases de datos. Las tablas las crea Hibernate cuando arranca cada microservicio.

- No hace falta crear ningún archivo `.env`: la contraseña de `root` es `despescar_dev` por defecto, tanto en Docker como en los `application.properties`.
- Para mirar las tablas: http://localhost:8090 → Sistema **MySQL**, servidor `mysql`, usuario `root`, contraseña `despescar_dev`.

## 3. Variables de entorno

Definilas en **cada terminal** donde vayas a arrancar un servicio (o ponelas en tu `~/.bashrc`):

```bash
export JWT_SECRET='despescar-dev-secret-key-2026-must-be-long-enough'
export MERCADOPAGO_TOKEN='token-de-prueba'   # solo payment-service; con este valor arranca, pero los pagos reales no funcionan
export GROQ_API_KEY='tu-clave'               # solo koi-ia-service (chatbot); sacala gratis en https://console.groq.com/home
```

| Variable | La necesita | Notas |
|---|---|---|
| `JWT_SECRET` | identity, flight, reservation, package, payment, gateway | **Tiene que ser exactamente la misma en todos**, si no se rechazan los tokens entre servicios. `hotel-service` tiene un valor por defecto. |
| `MERCADOPAGO_TOKEN` | payment-service | Obligatoria para que arranque. Para pagos reales hace falta un token de MercadoPago. |
| `GROQ_API_KEY` | koi-ia-service | Obligatoria para que arranque. Sin una clave real el chatbot no responde con IA. |
| `DB_PASSWORD` | todos | **Solo** si cambiaste la contraseña de MySQL. Si no la tocaste, no la definas. |

## 4. Arrancar los microservicios

Cada uno en **su propia terminal**, desde la raíz del repositorio. Orden recomendado (el gateway al final):

```bash
cd services/identity-service    && mvn spring-boot:run
cd services/flightservice       && mvn spring-boot:run
cd services/hotel-service       && mvn spring-boot:run
cd services/package-service     && mvn spring-boot:run
cd services/reservation-service && mvn spring-boot:run
cd services/payment-service     && mvn spring-boot:run
cd services/koi-ia-service      && mvn -Dmaven.test.skip=true spring-boot:run
cd services/gateway-service     && mvn spring-boot:run
```

- Un servicio está listo cuando el log dice `Started ...Application in ... seconds`. El primer arranque tarda varios minutos porque descarga dependencias.
- Se usa `mvn` y no `./mvnw` porque en algunos sistemas los `mvnw` no tienen permiso de ejecución y `gateway-service` y `koi-ia-service` no traen wrapper.
- `koi-ia-service` se arranca con `-Dmaven.test.skip=true` porque `KoiAiAssistantTest` está desactualizado y no compila.
- Para ver datos y probar la compra alcanzan `identity`, `flightservice`, `hotel`, `package`, `reservation` y el `gateway`. `payment` y `koi-ia` son opcionales.

## 5. Cargar datos de ejemplo

El proyecto arranca con las bases vacías: sin aeropuertos no hay nada que elegir en el buscador. Con `identity`, `flightservice`, `hotel-service` y `package-service` en marcha:

```bash
./database/seed/seed.sh
```

Crea aeropuertos (EZE, AEP, COR, MDZ, BRC, SLA, SCL, MIA, MAD), 3 aerolíneas, 2 tarifas (Light y Standard), 54 vuelos, 6 hoteles, 5 paquetes y dos usuarios. Detalles:

- **Se puede correr más de una vez**: si ya hay datos, no los duplica.
- **Las fechas son relativas a hoy**: los vuelos salen 14, 17 y 21 días después de la fecha en que corras el script. Al final te imprime esas fechas; usalas en el buscador.
- Solo existen vuelos entre los pares de aeropuertos cargados (por ejemplo EZE ↔ COR, AEP ↔ BRC, EZE ↔ MAD).

Usuarios de ejemplo:

| Correo | Contraseña | Rol |
|---|---|---|
| `admin@despescar.com` | `Admin2026!` | `SUPER_ADMIN` |
| `cliente@despescar.com` | `Cliente123!` | `USER` |

### Usuario administrador (si el script no pudo asignar el rol)

El script le da el rol `SUPER_ADMIN` al administrador con `docker exec`. Si falla, hacelo a mano y volvé a correr el script:

```bash
docker exec -e MYSQL_PWD=despescar_dev despescar-mysql mysql -uroot -e "
INSERT INTO despescar_identity.user_roles (user_id, role_id, assigned_at)
SELECT u.id, r.id, NOW(6) FROM despescar_identity.users u
JOIN despescar_identity.roles r ON r.name = 'SUPER_ADMIN'
WHERE u.email = 'admin@despescar.com';"
```

## 6. Frontend

Repositorio `TenSF25/DesPescar`, rama `features/merge-koi-gateway`:

```bash
pnpm install
pnpm dev
```

Abrilo en http://localhost:5173. Apunta por defecto al gateway en `http://localhost:8087`; si el tuyo está en otro lado, copiá `.env.example` a `.env` y cambiá `VITE_GATEWAY_URL`.

## 7. Probar que funciona

1. Entrá a http://localhost:5173/vuelos **sin iniciar sesión**: el buscador tiene que listar aeropuertos de origen y destino.
2. Buscá EZE → COR en una de las fechas que imprimió el script (en ida y vuelta, la fecha de vuelta tiene que ser otra de esas fechas).
3. Elegí un vuelo: te pide iniciar sesión (`cliente@despescar.com`) y vuelve a la compra.

## 8. Día a día

```bash
cd database
docker compose up -d      # prender MySQL
docker compose down       # apagarlo (los datos se conservan)
docker compose logs mysql # ver los logs
```

## 9. Empezar de cero

```bash
cd database
docker compose down -v    # BORRA todos los datos de la base
docker compose up -d
```

Después hay que volver a arrancar los servicios (para que recreen las tablas) y correr de nuevo `./database/seed/seed.sh`.

## Problemas comunes

| Síntoma | Causa y solución |
|---|---|
| `Access denied for user 'root'` | El volumen se creó antes con otra contraseña (por ejemplo con una versión anterior de este compose). Hacé `docker compose down -v` y volvé a levantar. |
| `Unknown database 'despescar_...'` | El volumen es anterior a los scripts de `database/init`. Mismo arreglo: `down -v`. |
| `Port 3306 is already in use` | Hay un MySQL instalado en tu sistema. Pararlo: `sudo systemctl stop mysql`. |
| `Port 8080 was already in use` | Otro programa usa el 8080 (Adminer ya no, quedó en el 8090). Revisalo con `ss -ltnp \| grep 8080`. |
| `Could not resolve placeholder 'JWT_SECRET'` (o `MERCADOPAGO_TOKEN`, `GROQ_API_KEY`) | Falta exportar la variable en esa terminal (sección 3). |
| `Cannot connect to the Docker daemon` | Docker está apagado: `sudo systemctl start docker` (o abrí Docker Desktop). Para que arranque solo: `sudo systemctl enable docker`. |
| Error 504 del gateway o todo muy lento | Falta de memoria: el gateway corta a los 5 segundos. Cerrá programas, arrancá menos servicios a la vez, o subí el límite: `mvn spring-boot:run -Dspring-boot.run.arguments="--resilience4j.timelimiter.instances.gatewayCircuitBreaker.timeoutDuration=30s"` en `gateway-service`. |
| El selector de aeropuertos sale vacío | Falta correr el seed (sección 5), o el gateway no está levantado. |
| Los servicios se caen tras reiniciar el equipo | Es normal: no son servicios del sistema. Repetí las secciones 2, 3 y 4. |

## Seguridad

La contraseña `despescar_dev`, los usuarios de ejemplo y el `JWT_SECRET` de este documento son **solo para desarrollo local**. En un despliegue real hay que definir valores propios mediante variables de entorno y nunca subirlos al repositorio.
