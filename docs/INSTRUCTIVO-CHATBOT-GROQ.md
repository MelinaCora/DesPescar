# Instructivo: levantar el chatbot KOI con Groq

KOI es el asistente de viajes de DesPescar. Vive en `koi-ia-service` (puerto **8088**): usa un modelo de IA de [Groq](https://groq.com) para conversar y consulta los demás servicios (vuelos, hoteles y paquetes) para armar las respuestas.

> Este documento es para **desarrollo local**. Es complementario a [`INSTRUCTIVO-DOCKER.md`](INSTRUCTIVO-DOCKER.md), que explica cómo levantar MySQL y el resto de los servicios.

## Qué necesita para funcionar

| Qué | Para qué | Obligatorio |
|---|---|---|
| **`GROQ_API_KEY`** (clave de Groq) | Hablar con el modelo de IA | **Sí.** Sin ella el servicio ni arranca |
| MySQL con la base `despescar_koiia` | Guardar sesiones y mensajes | Sí (`cd database && docker compose up -d`) |
| `flightservice` (8081) | Buscar vuelos | Para que pueda hablar de vuelos |
| `package-service` (8086) | Buscar paquetes turísticos | Solo si preguntan por paquetes |
| `hotel-service` (8083) | Buscar hoteles | Solo si preguntan por hoteles |
| `gateway-service` (8087) | Entrada del frontend | Para usarlo desde el frontend |
| ~400 MB de RAM libres | El servicio y su lanzador de Maven | Sí |

No necesita `JWT_SECRET`: las rutas `/api/koi/**` son públicas en el gateway.

## 1. Sacar la clave de Groq (gratis)

1. Entrá a https://console.groq.com/keys. Si no tenés cuenta, la consola te pide crearla.
2. Creá una clave nueva y **copiala en el momento**: estas consolas suelen mostrar la clave una sola vez. Si la perdés, creás otra.
3. Las claves de Groq empiezan con `gsk_`.

El plan gratuito incluye el modelo que usa el proyecto (`openai/gpt-oss-20b`, configurado en el `application.properties` de `koi-ia-service`), con estos límites:

| Límite | Valor |
|---|---|
| Peticiones por minuto | 30 |
| Peticiones por día | 1.000 |
| Tokens por minuto | 8.000 |
| Tokens por día | 200.000 |

Los **8.000 tokens por minuto** son el límite más justo: cada consulta manda además los últimos 6 mensajes de la conversación. Con varias preguntas largas seguidas puede aparecer un error de límite (ver "Problemas comunes").

> La documentación de Groq no aclara si piden tarjeta para el plan gratuito; lo vas a saber al registrarte. Los límites y la consola pueden cambiar: ante la duda, mirá https://console.groq.com/docs/rate-limits.

## 2. Guardar la clave sin subirla al repositorio

**La clave es personal y secreta.** El proyecto la lee de una variable de entorno (`GROQ_API_KEY`) precisamente para que nunca esté escrita en un archivo del repositorio. No la pegues en ningún `application.properties`, ni en un commit, ni en un chat.

Dos formas seguras de cargarla en tu terminal:

**A) Cada vez que abrís la terminal** (no queda en el historial de comandos):

```bash
read -rs -p "GROQ_API_KEY: " GROQ_API_KEY; export GROQ_API_KEY; echo
```

Pegá la clave y Enter (no se ve mientras la pegás).

**B) Un archivo personal fuera del repositorio:**

```bash
# ~/.despescar-env
export GROQ_API_KEY='gsk_...'
```

```bash
chmod 600 ~/.despescar-env      # solo vos podés leerlo (una sola vez)
source ~/.despescar-env         # en cada terminal donde vayas a levantar el chatbot
```

Si la clave se expuso por error, entrá a https://console.groq.com/keys, borrala (revocala) y creá otra.

## 3. Levantar el servicio

Con MySQL arriba y la clave cargada (sección 2):

```bash
cd services/koi-ia-service
mvn -Dmaven.test.skip=true spring-boot:run
```

- Está listo cuando el log dice `Started KoiIaServiceApplication in ... seconds`. La primera vez tarda varios minutos porque descarga dependencias.
- Se usa `-Dmaven.test.skip=true` porque `KoiAiAssistantTest` está desactualizado y no compila: arma el asistente con un `OllamaClient` y llama a `extractTravelInfo`, que el código actual (basado en Groq) ya no tiene. Sin esa opción falla con `COMPILATION ERROR`.
- Crea solo las tablas `koi_conversation_sessions` y `koi_conversation_messages` en `despescar_koiia`.

## 4. De dónde saca los datos

El modelo no inventa los vuelos, hoteles ni paquetes: el servicio los consulta con "herramientas" a los otros servicios (direcciones en `koi.catalog.*` del `application.properties`):

| Si el usuario pregunta por… | Consulta a | Puerto |
|---|---|---|
| Vuelos de ida y vuelta | `flightservice` | 8081 |
| Paquetes turísticos | `package-service` | 8086 |
| Hoteles de una ciudad | `hotel-service` | 8083 |

Si uno de esos servicios está apagado, el chatbot no va a poder traer esos datos. Para probarlo completo, levantá los tres (ver `INSTRUCTIVO-DOCKER.md`) y cargá los datos de ejemplo con `./database/seed/seed.sh`.

## 5. Levantar el gateway con tiempo de espera ampliado

Una consulta con búsqueda de vuelos puede tardar **hasta unos 10 segundos** (en las pruebas, entre 1 y 10), y la primera después de arrancar, algo más. El gateway por defecto corta a los 5 segundos con un error 504, así que desde el frontend las preguntas largas fallarían. Para desarrollo, subí **los dos** límites a 30 segundos:

```bash
cd services/gateway-service
export JWT_SECRET='despescar-dev-secret-key-2026-must-be-long-enough'
mvn spring-boot:run -Dspring-boot.run.arguments="--resilience4j.timelimiter.instances.gatewayCircuitBreaker.timeoutDuration=30s --spring.cloud.gateway.httpclient.response-timeout=30s"
```

Los argumentos valen solo para esa ejecución; si reiniciás el gateway sin ellos, vuelve a 5 segundos.

## 6. Probar que funciona

Desde una terminal, con el gateway y el chatbot arriba (se puede probar también directo al servicio cambiando `8087` por `8088`):

```bash
# 1) crear una conversación
SID=$(curl -s -X POST http://localhost:8087/api/koi/sessions \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['sessionId'])")

# 2) mandar un mensaje
curl -s -X POST "http://localhost:8087/api/koi/sessions/$SID/messages" \
  -H 'Content-Type: application/json' \
  -d '{"message":"Hola, quiero viajar a Bariloche. Que me recomendas?","history":[]}'
```

Una respuesta correcta es un JSON con estos campos:

| Campo | Qué es |
|---|---|
| `reply` | El texto que muestra el chat |
| `intent` y `stage` | Qué entendió KOI y en qué etapa de la conversación está (por ejemplo `VACATION`, `COLLECTING_INFO`) |
| `needsMoreInfo`, `missingFields`, `nextQuestion` | Qué datos le faltan para recomendar |
| `recommendations` | Recomendaciones estructuradas, si las hay |
| `sessionId` | Identificador de la conversación |

Si `reply` viene en lenguaje natural y con tono de asistente, la clave y la conexión con Groq funcionan. La primera respuesta tarda unos segundos de más.

## 7. Probarlo en el frontend

Frontend `TenSF25/DesPescar`, rama `features/merge-koi-gateway` (`pnpm dev`, http://localhost:5173). El chat llama a:

- `POST /api/koi/sessions` para iniciar la conversación.
- `POST /api/koi/sessions/{id}/messages` con `{ "message": "...", "history": [ ... ] }`, donde `history` son los últimos 6 mensajes de la conversación.

Si el chat muestra error, mirá la pestaña **Network** de las herramientas del navegador: el estado de esas dos peticiones dice si el problema está en el gateway (504, 5xx) o en el chatbot.

## 8. Límites conocidos

- **La búsqueda de vuelos ignora las fechas.** La herramienta `buscarVuelosIdaYVuelta` recibe las fechas pero no las usa: trae todos los vuelos, filtra por ciudad de origen y destino y toma los **tres primeros**, sin ordenar. Por eso puede decir "no hay vuelo ese día" aunque exista. Es una limitación del código del chatbot, no de la configuración.
- **Depende de los otros servicios.** Sin `package-service` o `hotel-service` no puede hablar de paquetes u hoteles.
- **Cuota gratuita.** Ver los límites de la sección 1.
- **Las respuestas de IA pueden variar.** Para precios y disponibilidad, la fuente fiable es la búsqueda de vuelos del propio sitio, no el texto del chat.

## Problemas comunes

| Síntoma | Causa y solución |
|---|---|
| `Could not resolve placeholder 'GROQ_API_KEY'` al arrancar | La variable no está cargada **en esa terminal**. Repetí la sección 2 y arrancá de nuevo. |
| `COMPILATION ERROR` / `testCompile` al arrancar | Falta `-Dmaven.test.skip=true` (sección 3). |
| `Port 8088 was already in use` | Ya hay un chatbot corriendo. Revisalo con `ss -ltnp \| grep 8088` y detené el anterior. |
| Error de conexión con MySQL (`Communications link failure`, `Access denied`) | MySQL no está arriba o tiene otra contraseña. Ver `INSTRUCTIVO-DOCKER.md` (`docker compose up -d`; si el volumen es viejo, `down -v`). |
| El chat responde con error 504 desde el frontend | El gateway cortó a los 5 segundos. Levantalo con los dos límites de la sección 5. |
| El gateway devuelve error 5xx en `/api/koi/...` | El chatbot no está corriendo en el 8088 o todavía está arrancando. Esperá al `Started ...` del log. |
| En el log del chatbot aparece un 401 de Groq | La clave está mal copiada, revocada o tiene espacios o comillas de más. Creá una nueva en la consola y repetí la sección 2. |
| En el log aparece un 429 de Groq | Superaste el límite del plan gratuito (30 peticiones o 8.000 tokens por minuto). Esperá un minuto. |
| Dice "no hay vuelos" o no encuentra hoteles ni paquetes | Faltan datos (`seed.sh`), están apagados `hotel-service` o `package-service`, o es la limitación de fechas de la sección 8. |

## Apagar y volver a levantar

Para detenerlo, `Ctrl+C` en su terminal. Para volver a levantarlo, repetí las secciones 2 y 3: la variable `GROQ_API_KEY` no persiste entre terminales (salvo que uses el archivo de la opción B).

## Seguridad

- La clave de Groq es tuya: no la compartas ni la subas al repositorio.
- Esta guía no incluye ninguna clave real, solo el formato (`gsk_...`).
- Si trabajás con una herramienta que ejecuta comandos por vos, cargá la clave tú mismo en tu terminal (sección 2) y no la pegues en el chat.
