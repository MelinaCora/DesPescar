# koi-ia-service (chatbot KOI con Groq)

← [Volver al índice](README.md)

KOI es el asistente de viajes de DesPescar. Usa un modelo de IA de [Groq](https://groq.com) para conversar y consulta los demás servicios para armar las respuestas.

| | |
|---|---|
| Puerto | **8088** |
| Base de datos | `despescar_koiia` (tablas `koi_conversation_sessions` y `koi_conversation_messages`) |
| Por el gateway | `/api/koi/**` (**pública**: no pide token) |
| Modelo | `openai/gpt-oss-20b` por la API de Groq (`https://api.groq.com/openai/v1`) |

## Qué necesita para funcionar

| Qué | Para qué | Obligatorio |
|---|---|---|
| **`GROQ_API_KEY`** (clave de Groq) | Hablar con el modelo de IA | **Sí.** Sin ella el servicio ni arranca |
| MySQL con la base `despescar_koiia` | Guardar sesiones y mensajes | Sí ([`mysql-docker.md`](mysql-docker.md)) |
| [`flightservice`](flightservice.md) (8081) | Buscar vuelos | Para que pueda hablar de vuelos |
| [`gateway-service`](gateway-service.md) (8087) | Entrada del frontend | Para usarlo desde el frontend |
| ~400 MB de RAM libres | El servicio y su lanzador de Maven | Sí |

No necesita `JWT_SECRET`. [`hotel-service`](hotel-service.md) (8083) y [`package-service`](package-service.md) (8086) están en el código del chatbot, pero **hoy no le sirven** (ver "Límites conocidos").

## 1. Sacar la clave de Groq (gratis)

1. Entrá a https://console.groq.com/keys. Si no tenés cuenta, la consola te pide crearla.
2. Creá una clave nueva y **copiala en el momento**: estas consolas suelen mostrar la clave una sola vez. Si la perdés, creás otra.
3. Las claves de Groq empiezan con `gsk_`.

El plan gratuito incluye el modelo del proyecto, con estos límites:

| Límite | Valor |
|---|---|
| Peticiones por minuto | 30 |
| Peticiones por día | 1.000 |
| Tokens por minuto | 8.000 |
| Tokens por día | 200.000 |

Los **8.000 tokens por minuto** son el límite más justo: cada consulta manda además los últimos 6 mensajes de la conversación. Con varias preguntas largas seguidas puede aparecer un error de límite (ver "Problemas comunes").

> La documentación de Groq no aclara si piden tarjeta para el plan gratuito; lo vas a saber al registrarte. Los límites y la consola pueden cambiar: ante la duda, mirá https://console.groq.com/docs/rate-limits.

## 2. Guardar la clave sin subirla al repositorio

**La clave es personal y secreta.** El proyecto la lee de la variable de entorno `GROQ_API_KEY` precisamente para que nunca esté escrita en un archivo del repositorio. No la pegues en ningún `application.properties`, ni en un commit, ni en un chat.

### Cada vez que abrís la terminal (no queda en el historial)

**Linux**
```bash
read -rs -p "GROQ_API_KEY: " GROQ_API_KEY; export GROQ_API_KEY; echo
```

**Windows (PowerShell)**
```powershell
$s = Read-Host "GROQ_API_KEY" -AsSecureString
$env:GROQ_API_KEY = [System.Net.NetworkCredential]::new('', $s).Password
```

Pegá la clave y Enter (no se ve mientras la pegás).

### Guardada en un archivo personal, fuera del repositorio

**Linux**
```bash
# ~/.despescar-env
export GROQ_API_KEY='gsk_...'
```
```bash
chmod 600 ~/.despescar-env      # solo vos podés leerlo (una sola vez)
source ~/.despescar-env         # en cada terminal donde vayas a levantar el chatbot
```

**Windows (PowerShell)**
```powershell
# %USERPROFILE%\.despescar-env.ps1   (en tu carpeta de usuario, fuera del repositorio)
$env:GROQ_API_KEY = 'gsk_...'
```
```powershell
. $HOME\.despescar-env.ps1      # en cada terminal donde vayas a levantar el chatbot
```
Si PowerShell bloquea el script, hay que permitir la ejecución de scripts locales (`Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`).

En Windows también existe `setx GROQ_API_KEY "gsk_..."`, que la guarda en tu usuario para todas las terminales **nuevas**. Es cómodo, pero queda en el registro de Windows: usalo solo en un equipo que sea tuyo.

Si la clave se expuso por error, entrá a https://console.groq.com/keys, borrala (revocala) y creá otra.

## 3. Levantar el servicio

Con MySQL arriba y la clave cargada (sección 2), desde la raíz del repositorio:

**Linux**
```bash
cd services/koi-ia-service
mvn spring-boot:run
```

**Windows (PowerShell)**
```powershell
cd services\koi-ia-service
mvn spring-boot:run
```

- Está listo cuando el log dice `Started KoiIaServiceApplication in ... seconds`. La primera vez tarda varios minutos porque descarga dependencias.
- Este servicio no trae `mvnw`, por eso se usa `mvn`.
- Los tests (`mvn test`, 13 pruebas) cubren el asistente y el servicio de conversaciones con la IA simulada: no necesitan `GROQ_API_KEY` ni MySQL.

## 4. De dónde saca los datos

El modelo no inventa los vuelos, hoteles ni paquetes: el servicio los consulta con "herramientas" a los otros servicios (direcciones en `koi.catalog.*` del `application.properties`):

| Si el usuario pregunta por… | Consulta a | Puerto |
|---|---|---|
| Vuelos de ida y vuelta | `flightservice` | 8081 |
| Paquetes turísticos | `package-service` | 8086 |
| Hoteles de una ciudad | `hotel-service` | 8083 |

Para probarlo con datos, cargá los [datos de ejemplo](datos-de-ejemplo.md).

## 5. Levantar el gateway con tiempo de espera ampliado

Una consulta con búsqueda de vuelos puede tardar **hasta unos 10 segundos** (en las pruebas, entre 1 y 10), y la primera después de arrancar, algo más. El gateway por defecto corta a los 5 segundos con un error 504, así que desde el frontend las preguntas largas fallarían. Levantalo con los dos límites a 30 segundos, como se explica en [`gateway-service.md`](gateway-service.md#con-tiempo-de-espera-ampliado-recomendado-para-el-chatbot-y-equipos-lentos).

## 6. Probar que funciona

Con el gateway y el chatbot arriba (para probar directo al servicio, cambiá `8087` por `8088`):

**Linux**
```bash
# 1) crear una conversación
SID=$(curl -s -X POST http://localhost:8087/api/koi/sessions \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['sessionId'])")

# 2) mandar un mensaje
curl -s -X POST "http://localhost:8087/api/koi/sessions/$SID/messages" \
  -H 'Content-Type: application/json' \
  -d '{"message":"Hola, quiero viajar a Bariloche. Que me recomendas?","history":[]}'
```

**Windows (PowerShell)**
```powershell
# 1) crear una conversación
$s = Invoke-RestMethod -Method Post -Uri http://localhost:8087/api/koi/sessions

# 2) mandar un mensaje
Invoke-RestMethod -Method Post -Uri "http://localhost:8087/api/koi/sessions/$($s.sessionId)/messages" `
  -ContentType 'application/json' `
  -Body '{"message":"Hola, quiero viajar a Bariloche. Que me recomendas?","history":[]}'
```

Una respuesta correcta es un JSON con estos campos:

| Campo | Qué es |
|---|---|
| `reply` | El texto que muestra el chat |
| `intent` y `stage` | Qué entendió KOI y en qué etapa está (por ejemplo `VACATION`, `COLLECTING_INFO`) |
| `needsMoreInfo`, `missingFields`, `nextQuestion` | Qué datos le faltan para recomendar |
| `recommendations` | Recomendaciones estructuradas, si las hay |
| `sessionId` | Identificador de la conversación |

Si `reply` viene en lenguaje natural y con tono de asistente, la clave y la conexión con Groq funcionan. La primera respuesta tarda unos segundos de más.

## 7. Probarlo en el frontend

Frontend `features/merge-koi-gateway` (ver [`frontend.md`](frontend.md)). El chat llama a:

- `POST /api/koi/sessions` para iniciar la conversación.
- `POST /api/koi/sessions/{id}/messages` con `{ "message": "...", "history": [ ... ] }`, donde `history` son los últimos 6 mensajes de la conversación.

Si el chat muestra error, mirá la pestaña **Network** de las herramientas del navegador: el estado de esas dos peticiones dice si el problema está en el gateway (504, 5xx) o en el chatbot.

## 8. Límites conocidos

- **La búsqueda de vuelos ignora las fechas.** La herramienta `buscarVuelosIdaYVuelta` recibe las fechas pero no las usa: trae todos los vuelos, filtra por ciudad de origen y destino y toma los **tres primeros**, sin ordenar. Por eso puede decir "no hay vuelo ese día" aunque exista.
- **No puede traer hoteles ni paquetes.** Sus herramientas llaman a `hotel-service` y a `package-service` **sin enviar ningún token**, y esos servicios exigen sesión para consultar. Se comprobó con `hotel-service`: sin token responde `403`, con token `200` (devuelve el hotel). Para paquetes se deduce del código, que usa la misma regla. Aunque los dos servicios estén encendidos, hoy el chatbot no los puede usar. Es una limitación del código, no de la configuración; hay que corregirla en el chatbot (por ejemplo, haciendo públicas esas consultas o enviando un token de servicio).
- **Cuota gratuita.** Ver los límites de la sección 1.
- **Las respuestas de IA pueden variar.** Para precios y disponibilidad, la fuente fiable es la búsqueda de vuelos del propio sitio, no el texto del chat.

## Problemas comunes

| Síntoma | Causa y solución |
|---|---|
| `Could not resolve placeholder 'GROQ_API_KEY'` al arrancar | La variable no está cargada **en esa terminal**. Repetí la sección 2 y arrancá de nuevo. |
| `Port 8088 was already in use` | Ya hay un chatbot corriendo. **Linux:** `ss -ltnp \| grep 8088`. **Windows:** `netstat -ano \| findstr :8088`. Detené el anterior (`Ctrl+C` en su terminal, o `kill <PID>` / `Stop-Process -Id <PID>`). |
| Error de conexión con MySQL (`Communications link failure`, `Access denied`) | MySQL no está arriba o tiene otra contraseña. Ver [`mysql-docker.md`](mysql-docker.md). |
| El chat responde con error 504 desde el frontend | El gateway cortó a los 5 segundos. Levantalo con el tiempo ampliado (sección 5). |
| El gateway devuelve error 5xx en `/api/koi/...` | El chatbot no está corriendo en el 8088 o todavía está arrancando. Esperá al `Started ...` del log. |
| En el log del chatbot aparece un 401 de Groq | La clave está mal copiada, revocada o tiene espacios o comillas de más. Creá una nueva en la consola y repetí la sección 2. |
| En el log aparece un 429 de Groq | Superaste el límite del plan gratuito (30 peticiones o 8.000 tokens por minuto). Esperá un minuto. |
| Dice "no hay vuelos" con fechas que sí tienen vuelos | Es la limitación de fechas de la sección 8. |
| No encuentra hoteles ni paquetes | Es la limitación de la sección 8: el chatbot los consulta sin token. |

## Apagar y volver a levantar

Para detenerlo, `Ctrl+C` en su terminal. Para volver a levantarlo, repetí las secciones 2 y 3: la variable `GROQ_API_KEY` no persiste entre terminales (salvo que uses el archivo personal o `setx`).

## Seguridad

- La clave de Groq es tuya: no la compartas ni la subas al repositorio.
- Esta guía no incluye ninguna clave real, solo el formato (`gsk_...`).
- Si trabajás con una herramienta que ejecuta comandos por vos, cargá la clave vos mismo en tu terminal (sección 2) y no la pegues en el chat.
