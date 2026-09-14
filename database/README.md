# Despescar — Entorno de Base de Datos con Docker

## Guía para el equipo, desde cero

**Última actualización:** incluye lo aprendido en la primera instalación real del equipo (Linux Mint / Ubuntu 24.04), además del análisis del backend real (`DesPescar-features-chatbot-integration.zip`).

---

## 1. ¿Qué problema resuelve esto?

Despescar tiene **7 microservicios Spring Boot**, y cada uno necesita conectarse a MySQL para funcionar. Si cada persona del equipo instala MySQL "a mano" en su computadora, van a terminar con 9 configuraciones distintas (versiones distintas, contraseñas distintas, bases con nombres distintos) y bugs del tipo *"a mí me funciona, a vos no"*.

La solución: **Docker**. Un archivo de configuración (`docker-compose.yml`) que se sube a Git, y que cualquiera del equipo usa para levantar, en su propia computadora, una base de datos MySQL **idéntica** a la de sus compañeros — misma versión, mismas bases de datos, mismo usuario y contraseña de desarrollo.

---

## 2. Terminología básica (glosario para empezar)

No hace falta memorizar esto, pero conviene tenerlo cerca las primeras veces que uses los comandos.

| Término | Qué significa |
|---|---|
| **Docker** | El programa que permite crear y correr "contenedores" en tu computadora. |
| **Docker Desktop** | La aplicación con interfaz gráfica que instalás para usar Docker en Windows/Mac. Queda corriendo en segundo plano. |
| **Imagen (image)** | Una "plantilla" o "receta" de un programa ya armado y listo para correr (ej: `mysql:8.0` es la imagen oficial de MySQL versión 8.0). No se edita, se descarga. |
| **Contenedor (container)** | Una imagen **en ejecución**. Es un proceso aislado corriendo en tu máquina, con su propio sistema de archivos interno, pero sin instalarse "de verdad" en tu sistema operativo. |
| **Docker Compose** | Una herramienta (incluida en Docker Desktop) que permite definir **uno o varios contenedores** y cómo se relacionan entre sí, en un solo archivo YAML (`docker-compose.yml`). En vez de escribir comandos largos, describís todo en ese archivo y lo levantás con un solo comando. |
| **Volumen (volume)** | Un espacio de almacenamiento persistente que Docker gestiona, para que los datos de un contenedor **no se pierdan** cuando el contenedor se apaga o se borra. |
| **Puerto (port)** | El "canal" de red por el que un programa escucha conexiones. MySQL usa por defecto el puerto `3306`. Cuando "mapeamos" un puerto, le decimos a Docker: "lo que llega al puerto 3306 de mi computadora, mandalo al puerto 3306 de adentro del contenedor". |
| **Variables de entorno (environment variables)** | Configuración que se le pasa a un contenedor al arrancar (usuario, contraseña, nombre de base de datos), sin tener que escribirla dentro del código. |
| **`.env`** | Un archivo de texto donde se guardan esas variables de entorno. **No se sube a Git** porque puede tener contraseñas. |
| **Healthcheck** | Una verificación automática que hace Docker para saber si un contenedor "ya está listo" para recibir conexiones, no solo si ya arrancó. |
| **Daemon** | El proceso de Docker que corre en segundo plano todo el tiempo, gestionando los contenedores. Si Docker Desktop está cerrado, el daemon no corre, y los comandos `docker` no van a funcionar. |

---

## 3. Instalación (una sola vez, por persona)

### ¿Qué hay que instalar?

Solamente **Docker**. Nada de MySQL.

- **Windows:** descargar Docker Desktop desde [docker.com](https://www.docker.com/products/docker-desktop/), instalar como cualquier programa. Puede pedir activar WSL2 (Windows Subsystem for Linux) — el instalador lo indica y lo resuelve automáticamente en la mayoría de los casos.
- **Mac:** mismo instalador, elegir la versión según el procesador (Apple Silicon o Intel — el instalador de la web ya detecta cuál te corresponde).
- **Linux (Ubuntu / Linux Mint):** ver el procedimiento verificado en el punto 3.1, que ya probamos en el equipo.

### ⚠️ Advertencia de seguridad antes de instalar (léanla todos)

Al instalar Docker, en algún momento vamos a copiar y pegar comandos en la terminal desde este documento o desde la web oficial. **Antes de pegar cualquier comando en una terminal, verifiquen que lo que se pegó coincide exactamente con lo que copiaron.**

Existe una técnica llamada ***pastejacking*** (secuestro de portapapeles): algunas páginas web maliciosas reemplazan silenciosamente el contenido que copiás, aunque en pantalla se vea un comando inofensivo. Durante la instalación real del equipo, este tipo de situación estuvo a punto de ejecutar código no identificado (un script que decodificaba un binario) en vez del instalador legítimo de Docker.

**Regla simple:** nunca pegues un comando largo con `sudo` en la terminal sin revisar antes qué contiene. Ante la duda, escribilo a mano, o pegalo primero en un editor de texto para revisarlo antes de correrlo.

### 3.1. Instalación verificada en Linux Mint / Ubuntu 24.04 ("noble")

Estos son los pasos reales que funcionaron, con las correcciones que hicieron falta — así el resto del equipo con la misma distro los puede seguir directo, sin repetir el proceso de prueba y error.

**1. Actualizar el sistema:**
```bash
sudo apt update && sudo apt upgrade -y
```

**2. Instalar los requisitos previos y la clave GPG de Docker:**
```bash
sudo apt-get -y install ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL "https://download.docker.com/linux/ubuntu/gpg" -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
```

> **Importante:** usar la URL `/linux/ubuntu/gpg`, no `/linux/debian/gpg`. El instalador automático de Docker (`get.docker.com`) puede detectar mal algunas variantes de Mint y agregar el repositorio de Debian en vez del de Ubuntu, lo que después genera un conflicto de dependencias (`containerd.io` pide una versión de `libseccomp2` que Ubuntu todavía no tiene disponible). Yendo directo por el repositorio de Ubuntu se evita ese problema.

**3. Agregar el repositorio, con el codename de tu versión de Ubuntu** (`noble` para Mint 22.x / Ubuntu 24.04 — si tenés otra versión, confirmalo antes con `cat /etc/upstream-release/lsb-release`):
```bash
echo "deb [arch=amd64 signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu noble stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
```

**4. Instalar Docker:**
```bash
sudo apt-get install docker-ce docker-ce-cli containerd.io docker-compose-plugin
```

**5. Permitir usar Docker sin `sudo` en cada comando:**
```bash
sudo usermod -aG docker $USER
```
Después de este paso, **cerrá sesión y volvé a entrar** (o reiniciá) para que el cambio tenga efecto. Es el paso que más gente se salta y después le tira error de permisos.

**6. Verificar que quedó todo bien, ya sin `sudo`:**
```bash
docker run hello-world
```
Si aparece el mensaje "Hello from Docker!", la instalación está completa y funcional.

### ¿Cómo sé que quedó bien instalado? (chequeo rápido general)

```bash
docker --version
docker compose version
```

Si devuelven un número de versión, está listo. Si da error, o Docker Desktop no está abierto (Windows/Mac), o en Linux hace falta haber hecho el paso 5 y reiniciado sesión.

---

## 4. Qué encontramos en el backend real (importante)

Antes de armar los archivos, analizamos el proyecto backend (`DesPescar-features-chatbot-integration`) para no inventar nada. Esto es lo que ya está definido en el código:

### 4.1. Cada microservicio tiene su propia base de datos

Este es el patrón llamado **"database per service"**, típico de arquitecturas de microservicios: cada servicio es dueño exclusivo de sus datos, nadie accede directamente a la base de otro.

| Microservicio | Base de datos (MySQL) |
|---|---|
| identity-service (usuarios, roles, permisos, JWT) | `despescar_identity` |
| flightservice (vuelos, aerolíneas, aeropuertos) | `despescar_flight` |
| hotel-service (hoteles) | `despescar_hotel` |
| reservation-service (reservas, pasajeros, equipaje) | `despescar_reservation` |
| package-service (paquetes turísticos) | `despescar_package` |
| payment-service (pagos — entidades aún vacías, en desarrollo) | `despescar_payment` |
| koi-ia-service (chatbot, sesiones de conversación) | `despescar_koiia` |

Las 7 bases conviven en **un mismo servidor MySQL** (un solo contenedor Docker), pero están separadas lógicamente — como 7 cajones distintos dentro del mismo mueble.

### 4.2. Las tablas se crean solas (no hace falta escribirlas a mano)

Cada microservicio tiene configurado:

```properties
spring.jpa.hibernate.ddl-auto=update
```

Esto le dice a Hibernate (la capa de acceso a datos de Spring Boot): *"al arrancar, mirá las clases `@Entity` del código, y creá o actualizá las tablas en la base para que coincidan"*.

**Consecuencia práctica:** Docker solo necesita crear las **7 bases de datos vacías**. Las tablas (`users`, `flights`, `hoteles`, `reservas_grupo`, etc.) las genera automáticamente cada microservicio la primera vez que arranca y se conecta a su base correspondiente.

> **Nota técnica (no requiere acción ahora):** `ddl-auto=update` es cómodo para desarrollo, pero no es la práctica recomendada para producción a largo plazo, porque Hibernate puede inferir cambios de forma inesperada. La alternativa profesional es usar migraciones versionadas (Flyway o Liquibase). No es necesario para el demo del 6/10 — se deja como posible mejora futura.

---

## 5. Los archivos que usamos y qué hace cada uno

### 5.1. Ubicación

Como frontend y backend están en **repositorios separados**, estos archivos viven dentro del **repositorio del backend**, en una carpeta propia:

```
despescar-backend/
├── database/
│   ├── docker-compose.yml
│   ├── .env.example
│   └── init/
│       └── 01_create_databases.sql
├── services/
│   ├── identity-service/
│   ├── flightservice/
│   ├── hotel-service/
│   ├── reservation-service/
│   ├── package-service/
│   ├── payment-service/
│   └── koi-ia-service/
└── ...
```

### 5.2. `docker-compose.yml`

```yaml
services:
  mysql:
    image: mysql:8.0
    container_name: despescar-mysql
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql
      - ./init:/docker-entrypoint-initdb.d
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 5s
      timeout: 5s
      retries: 10

  adminer:
    image: adminer
    container_name: despescar-adminer
    restart: unless-stopped
    ports:
      - "8080:8080"
    depends_on:
      - mysql

volumes:
  mysql_data:
```

**Explicación línea por línea:**

- `image: mysql:8.0` → la versión exacta de MySQL que todos van a tener. Nadie usa una versión distinta por accidente.
- `MYSQL_ROOT_PASSWORD` → la contraseña del usuario administrador (`root`), tomada del archivo `.env` (ver más abajo). Coincide con lo que ya está configurado en los `application.properties` de cada microservicio (`sqlrootpassword`, usuario `root`).
- `ports: "3306:3306"` → conecta el puerto 3306 de tu computadora con el 3306 de adentro del contenedor (el puerto estándar de MySQL). Así, cada microservicio corriendo en tu máquina (fuera de Docker) puede conectarse a `localhost:3306`, tal cual está en su `application.properties`.
- `volumes: mysql_data:/var/lib/mysql` → acá es donde MySQL guarda todos los datos. Este volumen persiste aunque apagues el contenedor.
- `./init:/docker-entrypoint-initdb.d` → le dice a MySQL: *"la primera vez que arranques con datos vacíos, ejecutá todos los `.sql` que encuentres en esta carpeta"*. Ahí es donde creamos las 7 bases.
- `healthcheck` → evita que un microservicio intente conectarse antes de que MySQL esté realmente listo para recibir conexiones.
- **Adminer**: una interfaz web súper liviana para ver las tablas y los datos sin instalar ningún programa aparte. Se accede desde el navegador en `http://localhost:8080`.

### 5.3. `.env.example`

```
MYSQL_ROOT_PASSWORD=sqlrootpassword
```

Este archivo **sí se sube a Git** (es solo una plantilla, sin secretos reales de producción — para desarrollo local está bien que la contraseña sea simple y visible, ya que solo vive en la computadora de cada uno).

Cada persona, la primera vez, copia este archivo:

```bash
cp .env.example .env
```

Y el `.env` real (con el nombre exacto `.env`, sin `.example`) **no se sube a Git** — se agrega a `.gitignore`:

```
database/.env
```

### 5.4. `init/01_create_databases.sql`

```sql
CREATE DATABASE IF NOT EXISTS despescar_identity;
CREATE DATABASE IF NOT EXISTS despescar_flight;
CREATE DATABASE IF NOT EXISTS despescar_hotel;
CREATE DATABASE IF NOT EXISTS despescar_reservation;
CREATE DATABASE IF NOT EXISTS despescar_package;
CREATE DATABASE IF NOT EXISTS despescar_payment;
CREATE DATABASE IF NOT EXISTS despescar_koiia;
```

Nada más que eso. Las tablas las crea Hibernate automáticamente cuando cada microservicio arranca por primera vez (ver punto 4.2).

---

## 6. Cómo se usa, paso a paso

### 6.1. La primera vez (cada persona, una sola vez al clonar el repo)

```bash
# 1. Clonar el repo del backend (si no lo tenés todavía)
git clone <url-del-repo-backend>
cd despescar-backend/database

# 2. Copiar el archivo de variables de entorno
cp .env.example .env

# 3. Levantar el contenedor de MySQL + Adminer
docker compose up -d
```

Eso descarga las imágenes (la primera vez tarda unos minutos), crea el contenedor, y ejecuta el script que crea las 7 bases vacías.

### 6.2. Verificar que levantó bien

```bash
docker compose ps
```

Tiene que mostrar `despescar-mysql` y `despescar-adminer` con estado `Up` (o `healthy`).

También podés entrar a `http://localhost:8080` en el navegador (Adminer), loguearte con:
- Sistema: MySQL
- Servidor: `mysql` (o `localhost` si Adminer corre fuera de Docker — en este caso, como Adminer también está en el compose, usar `mysql`)
- Usuario: `root`
- Contraseña: la que pusiste en `.env`

Y deberías ver las 7 bases listadas (todavía sin tablas, hasta que arranques los microservicios).

### 6.3. Arrancar un microservicio y ver que crea sus tablas solo

Con MySQL corriendo en Docker, cada uno arranca su microservicio de Spring Boot **normalmente, fuera de Docker** (como ya lo venían haciendo, con Maven/su IDE). Al arrancar, va a conectarse a `localhost:3306`, encontrar su base vacía, y crear las tablas automáticamente.

### 6.4. Día a día (una vez configurado)

```bash
# Prender MySQL (por ejemplo, al empezar a trabajar)
docker compose up -d

# Apagar MySQL (los datos quedan guardados)
docker compose down
```

### 6.5. "Resetear todo a cero"

Si algo quedó en un estado raro y querés volver a empezar como si acabaras de clonar el repo:

```bash
docker compose down -v
docker compose up -d
```

El `-v` borra también el volumen de datos, así que las 7 bases se vuelven a crear vacías, y los microservicios (al arrancar) regeneran las tablas desde cero.

---

## 7. La pregunta importante: ¿cómo afecta esto a mis compañeros?

Esta es la parte que más conviene tener clara para que nadie se lleve una sorpresa.

### 7.1. Cada persona tiene SU PROPIA base de datos, aislada

Cuando vos levantás `docker compose up -d`, se crea un contenedor MySQL **que vive únicamente en tu computadora**. Los datos que cargues, borres o modifiques ahí **no se sincronizan automáticamente con nadie más**.

Ejemplo concreto: si Arnold crea una reserva de prueba en su base local mientras testea el Hotel_Dashboard, esa reserva **existe solo en la computadora de Arnold**. Noelia, Thiago, o cualquier otro no la van a ver en la suya.

**Esto es intencional y es una ventaja**, no un defecto: cada uno puede romper, ensuciar o resetear su propia base sin afectar el trabajo de los demás.

### 7.2. Entonces, ¿qué es lo que SÍ se comparte?

Lo que se comparte por Git son los **archivos de configuración**, no los datos en sí:

- Si alguien cambia el `docker-compose.yml` (por ejemplo, agrega una base nueva porque se suma un microservicio), ese cambio se comitea, se sube, y cuando el resto del equipo hace `git pull`, tienen el mismo archivo. Para que tome efecto, hay que volver a levantar el contenedor.
- Las **tablas** no se comparten como archivo — cada quien las genera localmente porque Hibernate las crea a partir del mismo código Java que todos tienen (por eso es tan importante que todos trabajen sobre el mismo código de entidades, vía Git, como ya vienen haciendo).
- Si en el futuro quieren compartir **datos de ejemplo específicos** (por ejemplo, "quiero que todos tengan cargado el mismo vuelo de prueba para el demo"), eso se resuelve con un script de seed adicional (`02_seed_demo.sql`) que se agrega a la carpeta `init/` y se comitea — ahí sí, todos lo van a tener al resetear su base.

### 7.3. ¿Y la base de producción (Aiven)?

Es un caso completamente distinto y no se toca con esto. Aiven es la base **real, en la nube, compartida**, que se va a usar cuando el proyecto se despliegue para el demo del 6 de octubre. Ahí sí hay una sola base para todos — pero esa etapa es de **integración/despliegue**, no de desarrollo diario. Mientras estén construyendo y probando funcionalidades día a día, cada uno trabaja aislado en su Docker local.

### 7.4. Resumen en una frase

> **Git sincroniza el código y la configuración. Docker local NO sincroniza los datos entre compañeros — cada uno tiene su copia aislada, y eso es lo que buscamos.**

---

## 7.5. Cómo subir esto a Git sin pisar el trabajo del equipo

No hay que crear ni editar estos archivos directamente sobre la rama donde ya está todo integrado (aunque esa rama no se llame `main`, sino la que el equipo use como base común). Conviene trabajar en una rama propia y después abrir un Pull Request, mismo flujo que ya usan para las features del frontend.

**1. Actualizar la rama integrada antes de arrancar:**
```bash
git checkout <nombre-de-la-rama-integrada>
git pull origin <nombre-de-la-rama-integrada>
```

**2. Crear una rama nueva a partir de ahí:**
```bash
git checkout -b feature/database-docker-setup
```

**3. Confirmar en qué rama estás parado antes de crear archivos:**
```bash
git branch
```
(el `*` al lado del nombre indica la rama activa)

**4. Trabajar ahí:** crear la carpeta `database/` con los tres archivos (ver punto 5), probarlos localmente, y recién después comitear.

**5. Subir la rama y abrir el Pull Request** hacia la rama integrada, para que el equipo lo revise antes de mezclarlo.

Por qué conviene esto: si algo de la configuración tiene un error (una ruta mal puesta, un typo), no se lo lleva puesto el resto del equipo en su próximo `git pull` — queda contenido en tu rama hasta que se revisa y aprueba.

---

## 8. Comandos esenciales (referencia rápida)

| Comando | Qué hace |
|---|---|
| `docker compose up -d` | Levanta los contenedores en segundo plano (`-d` = detached) |
| `docker compose down` | Apaga los contenedores, sin borrar los datos |
| `docker compose down -v` | Apaga los contenedores **y borra los datos** (reset total) |
| `docker compose ps` | Muestra el estado de los contenedores |
| `docker compose logs mysql` | Muestra los logs de MySQL (útil si algo no arranca) |
| `docker compose logs -f mysql` | Igual, pero en vivo (`-f` = follow) |

---

## 9. Problemas comunes al empezar

- **"Error: puerto 3306 ya está en uso"** → probablemente ya tenés un MySQL instalado directamente en tu sistema (no en Docker) corriendo en ese puerto. Hay que apagarlo, o cambiar el mapeo de puertos en el `docker-compose.yml` (por ejemplo `"3307:3306"`, ajustando también el `application.properties` local si hace falta).
- **"docker: command not found"** → Docker Desktop no está instalado o no se abrió. Hay que abrirlo y esperar a que el ícono indique que está corriendo.
- **Las tablas no aparecen** → hay que arrancar el microservicio correspondiente (Spring Boot) al menos una vez; las tablas las crea Hibernate al conectarse, no Docker.
- **"Until Docker Desktop is running"** → mismo caso que el segundo punto, hay que tener la aplicación abierta en segundo plano.
- **`E: Unable to correct problems, you have held broken packages` al instalar en Ubuntu/Mint** → generalmente significa que el repositorio de Docker que se agregó no corresponde a tu distro. Revisá `/etc/apt/sources.list.d/docker.list`: tiene que decir `ubuntu` y el codename correcto (`noble` para Ubuntu 24.04 / Mint 22.x), no `debian trixie`. Ver el procedimiento completo en el punto 3.1.
- **`containerd.io : Depends: libseccomp2 (>= 2.6.0) but 2.5.5-... is to be installed`** → mismo caso que el anterior: el repositorio agregado era el de Debian en vez del de Ubuntu. Se soluciona corrigiendo el archivo de repositorio (punto 3.1) y volviendo a correr `apt-get update` antes de reinstalar.
- **En Adminer: "Connection refused"** → revisá qué pusiste en el campo **Servidor**. Tiene que ser `mysql` (el nombre del servicio en el `docker-compose.yml`), **nunca** `localhost` ni una IP — Adminer corre en su propio contenedor, y `localhost` ahí adentro no es tu base de MySQL, es el propio contenedor de Adminer.
- **Login rechazado en Adminer aunque la contraseña sea la correcta del `.env`** → revisá los logs con `docker compose logs mysql` y buscá la línea `root@localhost is created with an empty password!`. Si aparece, significa que el `.env` no se aplicó en el primer arranque (por ejemplo, no existía todavía cuando se corrió `docker compose up -d` por primera vez). **Ojo:** corregir el `.env` después no alcanza, porque MySQL solo aplica la contraseña la primera vez que inicializa sus archivos. Hay que resetear el volumen para que vuelva a inicializar desde cero:
  ```bash
  docker compose down -v
  docker compose up -d
  ```

---

## 10. Qué sigue después de esto

Con esta base funcionando, los próximos pasos naturales del proyecto son:

1. Confirmar que cada microservicio arranca correctamente contra su base en Docker.
2. Eventualmente, sumar los propios microservicios Spring Boot al mismo `docker-compose.yml` (dockerizarlos también), para que levantar todo el backend sea un solo comando — esto queda para más adelante, no es necesario ahora.
3. Cuando llegue el momento del despliegue, migrar la configuración de conexión de `localhost:3306` a las credenciales de Aiven (solo en el entorno de producción, sin tocar el flujo de desarrollo local).

---

*Documento generado como parte del proceso de documentación acordado para el proyecto Despescar — Fundación Pescar Argentina.*
