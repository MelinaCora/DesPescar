# Datos de ejemplo

← [Volver al índice](README.md)

El proyecto arranca con las bases **vacías**: sin aeropuertos no hay nada que elegir en el buscador de vuelos. El script `database/seed/seed.py` carga datos de prueba llamando a los propios servicios.

## Qué crea

| Dato | Cantidad | Detalle |
|---|---|---|
| Aeropuertos | 9 | EZE, AEP, COR, MDZ, BRC, SLA, SCL, MIA, MAD |
| Aerolíneas | 3 | Aerolíneas Argentinas (AR), LATAM (LA), Flybondi (FO) |
| Tarifas | 2 | Light y Standard, asignadas a todos los vuelos |
| Vuelos | 54 | 9 rutas, cada una con su ruta inversa, en 3 fechas |
| Hoteles | 6 | Buenos Aires, Córdoba, Bariloche, Mendoza, Madrid y Miami |
| Paquetes | 5 | Córdoba, Bariloche, Mendoza, Madrid y Miami, cada uno con un vuelo y un hotel |
| Usuarios | 2 | Un administrador y un cliente (ver abajo) |

- **Las fechas son relativas a hoy:** los vuelos salen 14, 17 y 21 días después del día en que corras el script. Al final te imprime esas fechas; usalas en el buscador.
- **Solo existen vuelos entre los pares de aeropuertos cargados**: por ejemplo EZE ↔ COR, AEP ↔ BRC (dos aerolíneas), AEP ↔ MDZ, AEP ↔ SLA, EZE ↔ SCL, EZE ↔ MIA y EZE ↔ MAD.
- **Se puede correr más de una vez:** si ya hay datos, no los duplica.

## Requisitos

- MySQL levantado ([`mysql-docker.md`](mysql-docker.md)).
- En marcha: [`identity-service`](identity-service.md), [`flightservice`](flightservice.md), [`hotel-service`](hotel-service.md) y [`package-service`](package-service.md).
- `docker` disponible en la terminal (el script lo usa para darle el rol de administrador al usuario `admin`).
- Python 3.

## Cómo ejecutarlo

Desde la raíz del repositorio.

**Linux**
```bash
./database/seed/seed.sh
```
(o, sin el envoltorio: `python3 database/seed/seed.py`)

**Windows (PowerShell)**
```powershell
py -3 database\seed\seed.py
```
Si `py` no existe, probá con `python database\seed\seed.py`. `seed.sh` es un script de bash y no se usa en Windows.

Salida esperada (las fechas cambian según el día):

```
usuario admin@despescar.com: creado
usuario cliente@despescar.com: creado
vuelos: 54 creados, salidas 18/10/2026, 21/10/2026, 25/10/2026
hoteles: 6 creados
paquetes: 5 creados
```

Tarda menos de un minuto. Si un servicio no está arriba, el script espera hasta 5 minutos a que abra su puerto y avisa cuál falta.

## Usuarios de ejemplo

| Correo | Contraseña | Rol |
|---|---|---|
| `admin@despescar.com` | `Admin2026!` | `SUPER_ADMIN` |
| `cliente@despescar.com` | `Cliente123!` | `USER` |

Son solo para desarrollo local. Para cambiarlos antes de correr el script:

**Linux**
```bash
ADMIN_EMAIL=otro@mail.com ADMIN_PASSWORD='OtraClave1!' ./database/seed/seed.sh
```

**Windows (PowerShell)**
```powershell
$env:ADMIN_EMAIL = 'otro@mail.com'; $env:ADMIN_PASSWORD = 'OtraClave1!'
py -3 database\seed\seed.py
```

También se pueden cambiar `CLIENT_EMAIL`, `CLIENT_PASSWORD`, las direcciones de los servicios (`IDENTITY_URL`, `FLIGHT_URL`, `HOTEL_URL`, `PACKAGE_URL`), `DB_CONTAINER` (por defecto `despescar-mysql`) y `DB_PASSWORD`.

## Si no pudo asignar el rol de administrador

Un usuario recién registrado es `USER`. Para convertir al administrador en `SUPER_ADMIN` el script ejecuta una sentencia SQL dentro del contenedor (`docker exec`); es necesario porque asignar roles ya exige ser `SUPER_ADMIN`. Si falla, ejecutá esa sentencia a mano desde **Adminer** (http://localhost:8090, funciona igual en Linux y Windows): elegí la base `despescar_identity`, abrí *Comando SQL* y ejecutá:

```sql
INSERT INTO despescar_identity.user_roles (user_id, role_id, assigned_at)
SELECT u.id, r.id, NOW(6)
FROM despescar_identity.users u
JOIN despescar_identity.roles r ON r.name = 'SUPER_ADMIN'
WHERE u.email = 'admin@despescar.com';
```

Luego volvé a correr el script. Los roles existen solo después de que `identity-service` arrancó al menos una vez.

## Problemas comunes

| Síntoma | Causa y solución |
|---|---|
| `... no responde en localhost:PUERTO` | Falta arrancar ese servicio. Ver su guía en el [índice](README.md). |
| `No pude asignar el rol SUPER_ADMIN con docker exec` | Docker apagado, contenedor con otro nombre o contraseña distinta. Revisá `DB_CONTAINER` y `DB_PASSWORD`, o usá la sentencia SQL de arriba. |
| `ERROR POST ... -> 403` al crear vuelos o paquetes | El rol de administrador no se aplicó antes del login. Corré el script otra vez. |
| **Windows:** `py` / `python` no se reconoce | Python no está en el `PATH`. Reinstalalo marcando **Add python.exe to PATH**, o desactivá el alias de la Tienda en *Configuración → Aplicaciones → Alias de ejecución de aplicaciones*. |
| **Linux:** `Permission denied` al ejecutar `seed.sh` | Usá `bash database/seed/seed.sh` o `python3 database/seed/seed.py`. |
| Quiero volver a cargar todo | [Empezá de cero](mysql-docker.md#empezar-de-cero) la base y corré el script otra vez. |
