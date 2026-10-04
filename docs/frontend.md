# Frontend (`features/merge-koi-gateway`)

← [Volver al índice](README.md)

Esta versión del backend está pensada para el frontend del repositorio **`TenSF25/DesPescar`**, rama **`features/merge-koi-gateway`**. Esta guía explica cómo correrlo contra el backend y qué llamadas hace.

| | |
|---|---|
| Repositorio | https://github.com/TenSF25/DesPescar |
| Rama | `features/merge-koi-gateway` |
| Dirección local | http://localhost:5173 (Vite) |
| Habla con | El [gateway](gateway-service.md) en `http://localhost:8087`, y directo con el WebSocket de [reservas](reservation-service.md) |

## Requisitos

- **Node.js** `^20.19.0` o `>=22.12.0` (lo exige el `package.json`). Descarga: https://nodejs.org
- **pnpm** `11.9.0` (el `package.json` lo fija en `packageManager`). Instalación: https://pnpm.io/installation
- **Git**.

Comprobalo con `node -v` y `pnpm -v` (igual en Linux y Windows).

## Correrlo

Antes tienen que estar arriba el [gateway](gateway-service.md) y, como mínimo, [`identity-service`](identity-service.md) y [`flightservice`](flightservice.md), con los [datos de ejemplo](datos-de-ejemplo.md) cargados. Los comandos son iguales en Linux y en Windows (PowerShell):

```bash
git clone https://github.com/TenSF25/DesPescar.git
cd DesPescar
git switch features/merge-koi-gateway
pnpm install
pnpm dev
```

Abrí http://localhost:5173. Si el 5173 estuviera ocupado, Vite usa el siguiente (5174) y lo muestra en la terminal; el gateway acepta cualquier origen, así que funciona igual.

### Variables (opcionales)

El frontend ya apunta por defecto a las direcciones locales. Solo necesitás un archivo `.env` si tu backend está en otro lado:

| Variable | Valor por defecto | Para qué |
|---|---|---|
| `VITE_GATEWAY_URL` | `http://localhost:8087` | Dirección del gateway (todas las llamadas HTTP) |
| `VITE_WS_URL` | `ws://localhost:8085/ws-despescar` | WebSocket de selección de asientos |

**Linux:** `cp .env.example .env`  ·  **Windows (PowerShell):** `Copy-Item .env.example .env`  ·  luego editá el `.env`.

## Qué llamadas hace (y contra qué servicio)

Se revisaron, leyendo el código, y coinciden con el backend de esta rama:

| Qué hace el frontend | Llamada | Servicio |
|---|---|---|
| Registro, login y renovar token | `/api/auth/register`, `/login`, `/refresh`; `/api/users/me` | [identity](identity-service.md) |
| Aeropuertos y búsqueda de vuelos | `/api/airports`, `/api/flights/search`, `/api/flights/{id}` (**públicas**) | [flightservice](flightservice.md) |
| Mapa de asientos | `/api/bookings/flights/{id}/seats` y `/seat-map` | [reservation](reservation-service.md) |
| Asientos en tiempo real | WebSocket `ws://localhost:8085/ws-despescar` | [reservation](reservation-service.md) |
| Crear reserva | `POST /api/bookings/init` | [reservation](reservation-service.md) |
| Cargar pasajeros | `PUT /api/bookings/{id}/passengers` | [reservation](reservation-service.md) |
| Pagar | `POST /api/payments` con `{reservationId}` y redirección al `checkoutUrl` de MercadoPago | [payment](payment-service.md) |
| Chatbot | `/api/koi/sessions` y `/api/koi/sessions/{id}/messages` | [koi-ia](koi-ia-service.md) |

## El recorrido de una compra

1. **Buscar vuelos** en `/vuelos`: no pide iniciar sesión. Hay vuelos solo en las fechas que cargó el script de datos de ejemplo.
2. **Elegir un vuelo.** En una búsqueda de ida y vuelta, el primer clic elige la ida y el segundo la vuelta; en una solo de ida, alcanza con uno.
3. **Iniciar sesión**, si no la tenías: te lleva a `/login` y, al ingresar, vuelve a la compra.
4. **Tarifa y equipaje** (`/booking/baggage`) y luego **asientos** (`/booking/seats`).
5. **Pago:** el botón "Continuar a Mercado Pago" redirige al `checkoutUrl`. El frontend solo redirige si la dirección es `https` y de MercadoPago.

Usuarios de prueba: ver el [índice](README.md#usuarios-de-prueba).

## Lo que todavía no está conectado

- **Pantallas de administración de hoteles:** usan datos de ejemplo del propio frontend (hay un `TODO` para conectarlas con [`hotel-service`](hotel-service.md)).
- **Hoteles y paquetes** no se consultan desde esta rama del frontend.
- **Pago grupal / Checkout API:** ninguna rama del frontend lo implementa, y el backend de esta rama conserva el pago por `POST /api/payments` que sí usa el frontend (ver [`payment-service.md`](payment-service.md)).
- Los cambios del frontend sin commitear (por ejemplo, de selección de asientos) no se revisaron.

## Problemas comunes

| Síntoma | Causa y solución |
|---|---|
| Los selectores de origen y destino salen vacíos | Falta cargar los [datos de ejemplo](datos-de-ejemplo.md), o el gateway no está arriba. |
| La búsqueda no devuelve vuelos | No hay vuelos para esa fecha. Usá las fechas que imprimió el script. |
| `504` en alguna acción | El gateway cortó a los 5 segundos. Ver [`gateway-service.md`](gateway-service.md#con-tiempo-de-espera-ampliado-recomendado-para-el-chatbot-y-equipos-lentos). |
| Error de red o de CORS en la consola del navegador | Revisá que el gateway responda en el 8087 y que `VITE_GATEWAY_URL` apunte ahí (pestaña **Network**). El gateway acepta cualquier origen. |
| Los asientos no cambian en tiempo real | `reservation-service` apagado o `VITE_WS_URL` mal configurada. Mirá **Network → WS** en el navegador. |
| El chat de KOI da error | Ver [`koi-ia-service.md`](koi-ia-service.md). |
| `pnpm` no se reconoce | Instalá pnpm (https://pnpm.io/installation) y abrí una terminal nueva. |
| `The engine "node" is incompatible` | Tu versión de Node no cumple `^20.19.0` o `>=22.12.0`. Actualizala. |
| Al elegir un vuelo te manda a `/login` y, después de ingresar, no vuelve a la compra | La ida al login funciona; la vuelta a la compra está implementada pero **no se comprobó en el navegador**. Si falla, avisá al equipo con lo que muestra la pestaña **Console**. |
