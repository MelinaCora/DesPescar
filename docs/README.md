# Documentación del backend de DesPescar

Cada servicio tiene su propia guía. Esta página es el índice: explica qué hace falta instalar, en qué orden se levanta todo y cómo se escriben los comandos en **Linux** y en **Windows**.

> **Sistemas cubiertos.** Los comandos de **Linux** se ejecutaron y comprobaron. Los de **Windows (PowerShell)** se escribieron revisando uno por uno su sintaxis, pero **no se pudieron ejecutar en Windows**: si algo falla, avisá al equipo para corregir la guía. macOS no está cubierto.

## Guías

| Guía | Qué trata | Puerto |
|---|---|---|
| [`mysql-docker.md`](mysql-docker.md) | MySQL y Adminer con Docker (la base de datos de todos los servicios) | 3306 y 8090 |
| [`datos-de-ejemplo.md`](datos-de-ejemplo.md) | Script que carga aeropuertos, vuelos, hoteles, paquetes y usuarios de prueba | — |
| [`identity-service.md`](identity-service.md) | Registro, login, tokens y roles | 8080 |
| [`flightservice.md`](flightservice.md) | Aeropuertos, aerolíneas, tarifas y vuelos | 8081 |
| [`hotel-service.md`](hotel-service.md) | Hoteles | 8083 |
| [`payment-service.md`](payment-service.md) | Pagos con MercadoPago | 8084 |
| [`reservation-service.md`](reservation-service.md) | Reservas y selección de asientos en tiempo real | 8085 |
| [`package-service.md`](package-service.md) | Paquetes turísticos | 8086 |
| [`gateway-service.md`](gateway-service.md) | Puerta de entrada del frontend: rutas, permisos y límites | 8087 |
| [`koi-ia-service.md`](koi-ia-service.md) | Chatbot KOI con IA de Groq | 8088 |
| [`frontend.md`](frontend.md) | Cómo se conecta el frontend `features/merge-koi-gateway` | 5173 |

Para la teoría de Docker y su instalación paso a paso, ver [`../database/README.md`](../database/README.md).

## Qué hay que instalar

| Herramienta | Para qué | Linux | Windows |
|---|---|---|---|
| **Docker** con Compose v2 | MySQL y Adminer | Instalación en [`../database/README.md`](../database/README.md) | [Docker Desktop](https://www.docker.com/products/docker-desktop/) (necesita WSL 2 o Hyper-V; ver [`mysql-docker.md`](mysql-docker.md)) |
| **Java 17 o superior** | Los microservicios | Paquete del sistema o [Temurin](https://adoptium.net) | [Temurin](https://adoptium.net) (instalador `.msi`) |
| **Maven 3.8 o superior** | Compilar y arrancar | Paquete del sistema | [Descarga](https://maven.apache.org/download.cgi), descomprimir y agregar su carpeta `bin` al `PATH` |
| **Python 3** | Solo para los datos de ejemplo | Ya viene instalado | [python.org](https://www.python.org/downloads/): en el instalador, marcar **Add python.exe to PATH** |
| **Git** | Clonar el repositorio | Paquete del sistema | [Git para Windows](https://git-scm.com/download/win) |
| **Node.js** y **pnpm** | Solo para el frontend | Ver [`frontend.md`](frontend.md) | Ver [`frontend.md`](frontend.md) |

Comprobá las versiones con `java -version`, `mvn -v`, `docker compose version` y `python3 --version` (en Windows: `py -3 --version`).

Recomendado: **8 GB de RAM**. Con menos, levantá solo los servicios que necesites.

## Orden de arranque

Cada servicio va en **su propia terminal**, desde la raíz del repositorio:

1. **MySQL y Adminer** → [`mysql-docker.md`](mysql-docker.md)
2. [`identity-service`](identity-service.md)
3. [`flightservice`](flightservice.md)
4. [`hotel-service`](hotel-service.md) y [`package-service`](package-service.md) (opcionales para comprar un vuelo)
5. [`reservation-service`](reservation-service.md)
6. [`payment-service`](payment-service.md) (opcional hasta el paso de pago)
7. [`koi-ia-service`](koi-ia-service.md) (opcional, solo el chatbot)
8. [`gateway-service`](gateway-service.md) **al final**
9. [Datos de ejemplo](datos-de-ejemplo.md), con identity, flight, hotel y package en marcha
10. [Frontend](frontend.md)

El primer arranque de cada servicio tarda varios minutos (descarga dependencias). Está listo cuando el log dice `Started ...Application in ... seconds`.

## Variables de entorno

Se definen **en la terminal donde arranca cada servicio** y no persisten entre terminales.

| Variable | La usan | Obligatoria |
|---|---|---|
| `JWT_SECRET` | identity, flight, reservation, package, payment y gateway | Sí, **el mismo valor en todos** |
| `RESERVATION_SERVICE_SYNC_TOKEN` | reservation y payment | Imprescindible para pagar, el mismo valor en los dos |
| `INVENTORY_SERVICE_TOKEN` | reservation, flight y hotel | Para que una reserva confirmada descuente asientos y habitaciones, el mismo valor en los tres |
| `GROQ_API_KEY` | koi-ia-service | Sí, para el chatbot |
| `MERCADOPAGO_ACCESS_TOKEN` y demás `MERCADOPAGO_*` | payment-service | Solo para generar el enlace de pago |
| `DB_PASSWORD` | todos | Solo si cambiaste la contraseña de MySQL (por defecto `despescar_dev`) |

Valores de ejemplo para desarrollo local (**no sirven para producción**):

```
JWT_SECRET                      despescar-dev-secret-key-2026-must-be-long-enough
RESERVATION_SERVICE_SYNC_TOKEN  despescar-dev-sync-token
INVENTORY_SERVICE_TOKEN         despescar-dev-inventory-token
```

## Comandos: Linux y Windows

Todas las guías usan estas equivalencias. En Windows se usa **PowerShell** (si usás `cmd`, ver la última columna).

| Qué hacer | Linux (bash) | Windows (PowerShell) | Windows (`cmd`) |
|---|---|---|---|
| Definir una variable en la terminal | `export JWT_SECRET='valor'` | `$env:JWT_SECRET = 'valor'` | `set JWT_SECRET=valor` (sin comillas) |
| Pedir un secreto sin mostrarlo | `read -rs -p "Clave: " GROQ_API_KEY; export GROQ_API_KEY` | Ver [`koi-ia-service.md`](koi-ia-service.md) | — |
| Entrar a una carpeta | `cd services/identity-service` | `cd services\identity-service` | `cd services\identity-service` |
| Copiar un archivo | `cp .env.example .env` | `Copy-Item .env.example .env` | `copy .env.example .env` |
| Ver qué usa un puerto | `ss -ltnp \| grep 8080` | `netstat -ano \| findstr :8080` | `netstat -ano \| findstr :8080` |
| Detener un proceso por su PID | `kill <PID>` | `Stop-Process -Id <PID>` | `taskkill /PID <PID> /F` |
| Arrancar Docker | `sudo systemctl start docker` | Abrir **Docker Desktop** y esperar a que diga *Engine running* | igual |
| Ejecutar Python | `python3 archivo.py` | `py -3 archivo.py` (o `python`) | `py -3 archivo.py` |
| Hacer una petición HTTP | `curl -s ...` | `Invoke-RestMethod ...` | `curl.exe ...` (con comillas distintas) |

Notas para Windows:

- En **PowerShell**, `curl` es un alias de `Invoke-WebRequest` y no acepta las opciones de `curl`. Por eso las guías usan `Invoke-RestMethod`. Con `curl.exe` el JSON del cuerpo exige escapar las comillas, por eso conviene evitarlo.
- Al pasar opciones de Maven con punto, como `-Dmaven.test.skip=true`, **ponelas entre comillas** (`"-Dmaven.test.skip=true"`). PowerShell parte el argumento en el punto si no. Entre comillas funciona igual en Linux y en `cmd`, así que las guías usan siempre esa forma.
- Para que una variable persista entre terminales en Windows existe `setx NOMBRE "valor"`, que la guarda en tu usuario y vale solo para terminales **nuevas**. Es cómodo, pero queda guardada en el registro: no lo uses para claves que no quieras dejar en tu equipo.
- Si Windows muestra un aviso del firewall al arrancar Java o Docker, permití el acceso en redes privadas.
- Clonar el repositorio en una ruta corta y sin espacios (por ejemplo `C:\dev\DesPescar`) evita problemas con herramientas de línea de comandos.
- `seed.sh` es un script de bash: en Windows usá directamente `seed.py` (ver [`datos-de-ejemplo.md`](datos-de-ejemplo.md)).
- El repositorio incluye un `.gitattributes` que mantiene los scripts `.sh` con saltos de línea de Linux al clonar en Windows.

## Usuarios de prueba

Los crea [`datos-de-ejemplo.md`](datos-de-ejemplo.md). **Solo para desarrollo local.**

| Correo | Contraseña | Rol |
|---|---|---|
| `admin@despescar.com` | `Admin2026!` | `SUPER_ADMIN` |
| `cliente@despescar.com` | `Cliente123!` | `USER` |

## Seguridad

La contraseña `despescar_dev`, los usuarios de ejemplo y los valores de `JWT_SECRET` y `RESERVATION_SERVICE_SYNC_TOKEN` de estas guías son **solo para desarrollo local**. En un despliegue real hay que definir valores propios por variables de entorno y nunca subirlos al repositorio. La clave de Groq y las credenciales de MercadoPago son personales: no se comparten ni se suben.
