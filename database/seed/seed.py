#!/usr/bin/env python3
"""Carga datos de ejemplo para desarrollo local (aeropuertos, vuelos, hoteles, paquetes y usuarios).

Requisitos: MySQL (docker compose up -d) y los servicios identity (8080), flightservice (8081),
hotel-service (8083) y package-service (8086) en marcha. Es repetible: si ya hay datos, no los duplica.
Con --completar-rutas agrega los vuelos que falten sin borrar nada (--solo-vuelos y --completar-rutas se combinan).
Con --solo-vuelos solo hacen falta identity y flightservice (no se cargan hoteles ni paquetes).
Solo hay vuelos nacionales (Argentina) en pesos argentinos. Las fechas de los vuelos se calculan a
partir de hoy hasta fin de mes (minimo 14 dias), asi nunca quedan vencidas.
"""
import datetime as dt
import json
import os
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request

IDENTITY = os.environ.get("IDENTITY_URL", "http://localhost:8080")
FLIGHT = os.environ.get("FLIGHT_URL", "http://localhost:8081")
HOTEL = os.environ.get("HOTEL_URL", "http://localhost:8083")
PACKAGE = os.environ.get("PACKAGE_URL", "http://localhost:8086")
DB_CONTAINER = os.environ.get("DB_CONTAINER", "despescar-mysql")
DB_PASSWORD = os.environ.get("DB_PASSWORD", "despescar_dev")

ADMIN = (os.environ.get("ADMIN_EMAIL", "admin@despescar.com"), os.environ.get("ADMIN_PASSWORD", "Admin2026!"))
CLIENT = (os.environ.get("CLIENT_EMAIL", "cliente@despescar.com"), os.environ.get("CLIENT_PASSWORD", "Cliente123!"))

# Solo vuelos nacionales (Argentina), en pesos argentinos (ARS).
AIRPORTS = (  # nombre, IATA, ciudad
    ("Aeropuerto Internacional Ministro Pistarini", "EZE", "Buenos Aires"),
    ("Aeroparque Jorge Newbery", "AEP", "Buenos Aires"),
    ("Aeropuerto Internacional Ingeniero Ambrosio Taravella", "COR", "Córdoba"),
    ("Aeropuerto Internacional El Plumerillo", "MDZ", "Mendoza"),
    ("Aeropuerto Internacional Teniente Luis Candelaria", "BRC", "San Carlos de Bariloche"),
    ("Aeropuerto Internacional Martín Miguel de Güemes", "SLA", "Salta"),
    ("Aeropuerto Internacional Malvinas Argentinas", "USH", "Ushuaia"),
    ("Aeropuerto Internacional Comandante Armando Tola", "FTE", "El Calafate"),
    ("Aeropuerto Internacional Cataratas del Iguazú", "IGR", "Puerto Iguazú"),
    ("Aeropuerto Internacional Teniente Benjamín Matienzo", "TUC", "San Miguel de Tucumán"),
    ("Aeropuerto Internacional Presidente Perón", "NQN", "Neuquén"),
    ("Aeropuerto Internacional Astor Piazzolla", "MDQ", "Mar del Plata"),
)
# Aerolineas que operan cabotaje en Argentina.
AIRLINES = (("Aerolíneas Argentinas", "AR"), ("Flybondi", "FO"), ("JetSMART", "JA"))
AIRLINE_FACTOR = {"AR": 1.0, "FO": 0.85, "JA": 0.8}
# Las tarifas son lo que se suma al precio del vuelo: Light no suma nada (solo equipaje de mano).
FARES = (  # nombre, tipo, equipaje de mano, equipaje despachado, wifi, seleccion de asiento, base, impuestos (ARS)
    ("Light", "LIGHT", True, False, False, "PAID", 0, 0),
    ("Standard", "STANDARD", True, True, True, "FREE", 28000, 7000),
)
ROUTES = (  # origen, destino, minutos, precio base ARS, aerolineas que la vuelan (ida y vuelta, todos los dias)
    ("AEP", "COR", 85, 80000, ("AR", "FO", "JA")),
    ("AEP", "MDZ", 120, 95000, ("AR", "FO")),
    ("AEP", "BRC", 150, 120000, ("AR", "FO", "JA")),
    ("AEP", "SLA", 135, 110000, ("AR", "JA")),
    ("AEP", "TUC", 120, 90000, ("AR", "FO")),
    ("AEP", "IGR", 115, 100000, ("AR", "FO")),
    ("AEP", "NQN", 115, 95000, ("AR", "JA")),
    ("AEP", "MDQ", 65, 55000, ("AR", "FO")),
    ("AEP", "USH", 215, 190000, ("AR", "FO")),
    ("AEP", "FTE", 205, 180000, ("AR", "FO", "JA")),
    ("EZE", "COR", 90, 85000, ("AR", "JA")),
    ("EZE", "BRC", 150, 125000, ("AR",)),
    ("EZE", "MDZ", 125, 98000, ("JA",)),
    ("EZE", "USH", 215, 195000, ("AR",)),
    ("EZE", "IGR", 120, 105000, ("AR",)),
)
DEPARTURE_HOURS = (6, 8, 10, 12, 14, 16, 18, 20)

AIRPORT_COORDS = {  # lat, lon: sirven para estimar duracion y precio de las rutas sin servicio explicito
    "EZE": (-34.82, -58.54), "AEP": (-34.56, -58.42), "COR": (-31.32, -64.21), "MDZ": (-32.83, -68.79),
    "BRC": (-41.15, -71.16), "SLA": (-24.86, -65.49), "USH": (-54.84, -68.31), "FTE": (-50.28, -72.05),
    "IGR": (-25.74, -54.47), "TUC": (-26.84, -65.10), "NQN": (-38.95, -68.16), "MDQ": (-37.93, -57.57),
}


def construir_rutas():
    """ROUTES mas una ruta (una aerolinea, ida y vuelta) entre cada par de aeropuertos que aun no tenga servicio,
    asi cualquier origen/destino que elija el usuario tiene vuelos. EZE y AEP son la misma ciudad: no se unen."""
    import math
    rutas = list(ROUTES)
    cubiertas = {frozenset((o, d)) for o, d, *_ in ROUTES} | {frozenset(("EZE", "AEP"))}
    codes = [code for _, code, _ in AIRPORTS]
    i = 0
    for x, a in enumerate(codes):
        for b in codes[x + 1:]:
            if frozenset((a, b)) in cubiertas:
                continue
            (la1, lo1), (la2, lo2) = AIRPORT_COORDS[a], AIRPORT_COORDS[b]
            p1, p2 = math.radians(la1), math.radians(la2)
            h = math.sin((p2 - p1) / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(math.radians(lo2 - lo1) / 2) ** 2
            km = 2 * 6371 * math.asin(math.sqrt(h))
            minutos = round((35 + km / 12) / 5) * 5
            precio = round((25000 + km * 55) / 500) * 500
            rutas.append((a, b, minutos, precio, (AIRLINES[i % len(AIRLINES)][1],)))
            i += 1
    return rutas



def call(method, url, body=None, token=None, ok=(200, 201, 204)):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=120) as res:
            raw = res.read()
            return res.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as err:
        raw = err.read().decode(errors="replace")
        if err.code in ok:
            return err.code, None
        sys.exit(f"ERROR {method} {url} -> {err.code}: {raw[:300]}")
    except urllib.error.URLError as err:
        sys.exit(f"ERROR {method} {url}: {err.reason}")


def wait_port(url, name, timeout=300):
    host_port = url.split("//")[1].split("/")[0]
    host, port = host_port.split(":")
    end = time.time() + timeout
    while time.time() < end:
        try:
            with socket.create_connection((host, int(port)), timeout=2):
                return
        except OSError:
            time.sleep(3)
    sys.exit(f"{name} no responde en {host_port}. Arrancalo primero (ver docs/INSTRUCTIVO-DOCKER.md).")


def grant_super_admin(email):
    sql = (
        "INSERT INTO despescar_identity.user_roles (user_id, role_id, assigned_at) "
        "SELECT u.id, r.id, NOW(6) FROM despescar_identity.users u "
        "JOIN despescar_identity.roles r ON r.name = 'SUPER_ADMIN' "
        f"WHERE u.email = '{email}' AND NOT EXISTS (SELECT 1 FROM despescar_identity.user_roles ur "
        "WHERE ur.user_id = u.id AND ur.role_id = r.id);"
    )
    if os.environ.get("MYSQL_LOCAL") == "1":
        # MySQL instalado en la maquina (sin Docker): usa el cliente mysql local.
        user = os.environ.get("DB_USER", "root")
        cmd = ["mysql", f"-u{user}"]
        if os.environ.get("DB_HOST"):  # base remota (por ejemplo Aiven)
            cmd += ["-h", os.environ["DB_HOST"], "-P", os.environ.get("DB_PORT", "3306"),
                    f"--ssl-mode={os.environ.get('DB_SSL_MODE', 'REQUIRED')}"]
        cmd += ["-e", sql]
        env = {**os.environ, "MYSQL_PWD": DB_PASSWORD}
    else:
        cmd = ["docker", "exec", "-e", f"MYSQL_PWD={DB_PASSWORD}", DB_CONTAINER, "mysql", "-uroot", "-e", sql]
        env = None
    res = subprocess.run(cmd, capture_output=True, text=True, env=env)
    if res.returncode != 0:
        sys.exit(
            "No pude asignar el rol SUPER_ADMIN con el cliente mysql:\n"
            f"{res.stderr.strip()}\n"
            "Hacelo a mano (ver docs/INSTRUCTIVO-DOCKER.md, seccion 'Usuario administrador') y volve a correr el script."
        )


def crear_vuelos(token, by_code, airlines, fares, flight_numbers, existentes=frozenset(), ultimo_numero=0):
    """Crea un vuelo por ruta, aerolinea y sentido para cada dia desde hoy hasta fin de mes (minimo 14 dias).
    `existentes` son claves (aerolinea, origen, destino, fecha) ya cargadas: no se repiten."""
    today = dt.date.today()
    # Del dia de hoy al fin de mes (o, si queda poco, al menos 14 dias): siempre hay fechas para buscar.
    month_end = (today.replace(day=28) + dt.timedelta(days=4)).replace(day=1) - dt.timedelta(days=1)
    last_day = max(month_end, today + dt.timedelta(days=14))
    now = dt.datetime.now()
    rutas = construir_rutas()
    n = ultimo_numero
    creados = 0
    day_index = 0
    day = today
    while day <= last_day:
        for r, (o, d, mins, price, serving) in enumerate(rutas):
            for k, al in enumerate(serving):
                for back in (False, True):
                    a, b = (d, o) if back else (o, d)
                    if (al, a, b, day.isoformat()) in existentes:
                        continue
                    hour = DEPARTURE_HOURS[(r + 2 * k + (3 if back else 0)) % len(DEPARTURE_HOURS)]
                    dep = dt.datetime.combine(day, dt.time(hour, (r * 10 + k * 15) % 60))
                    if dep <= now:
                        continue
                    n += 1
                    creados += 1
                    number = f"{al}{1000 + n}"
                    flight_numbers.setdefault((al, a, b, day_index), number)
                    # tarifa base ARS: cada aerolinea tiene su factor y el precio varia un poco segun el dia
                    amount = round(price * AIRLINE_FACTOR[al] * (1 + 0.04 * ((day_index + r) % 4)) / 500) * 500
                    call("POST", FLIGHT + "/api/flights", {
                        "flightNumber": number, "airlineId": airlines[al], "originAirportId": by_code[a],
                        "destinationAirportId": by_code[b], "departureTime": dep.isoformat(),
                        "arrivalTime": (dep + dt.timedelta(minutes=mins)).isoformat(), "price": amount,
                        "availableSeats": 150, "status": "SCHEDULED", "faresId": fares}, token)
        day += dt.timedelta(days=1)
        day_index += 1
    print(f"vuelos: {creados} creados, del {today.strftime('%d/%m/%Y')} al {last_day.strftime('%d/%m/%Y')}, todos los dias")


def main():
    solo_vuelos = "--solo-vuelos" in sys.argv  # no toca hoteles ni paquetes (sus servicios pueden estar apagados)
    sin_paquetes = solo_vuelos or "--sin-paquetes" in sys.argv  # package-service apagado: carga vuelos y hoteles
    services = [(IDENTITY, "identity-service"), (FLIGHT, "flightservice")]
    if not solo_vuelos:
        services.append((HOTEL, "hotel-service"))
    if not sin_paquetes:
        services.append((PACKAGE, "package-service"))
    for url, name in services:
        wait_port(url, name)

    # --- usuarios ---
    for email, password, first, last in ((*ADMIN, "Admin", "DesPescar"), (*CLIENT, "Cliente", "Demo")):
        status, _ = call(
            "POST", IDENTITY + "/auth/register",
            {"firstName": first, "lastName": last, "email": email, "password": password}, ok=(200, 201, 409),
        )
        print(f"usuario {email}: {'ya existia' if status == 409 else 'creado'}")
    grant_super_admin(ADMIN[0])  # antes del login: el rol viaja dentro del token
    _, login = call("POST", IDENTITY + "/auth/login", {"email": ADMIN[0], "password": ADMIN[1]})
    token = login["accessToken"]

    # --- vuelos ---
    _, airports = call("GET", FLIGHT + "/api/airports")
    flight_numbers = {}
    if airports and "--completar-rutas" in sys.argv:
        # No borra nada: agrega solo los vuelos que faltan (por ejemplo, rutas nuevas o dias nuevos).
        by_code = {a["code"]: a["id"] for a in airports}
        _, airline_list = call("GET", FLIGHT + "/api/airlines", token=token)
        airlines = {a["code"]: a["id"] for a in airline_list}
        _, fare_list = call("GET", FLIGHT + "/api/fares", token=token)
        _, vuelos = call("GET", FLIGHT + "/api/flights", token=token)
        existentes = {
            (v["flightNumber"][:2], v["originAirport"]["code"], v["destinationAirport"]["code"], v["departureTime"][:10])
            for v in vuelos
        }
        ultimo = max((int(v["flightNumber"][2:]) - 1000 for v in vuelos), default=0)
        crear_vuelos(token, by_code, airlines, [f["id"] for f in fare_list], flight_numbers, existentes, ultimo)
    elif airports:
        print(f"vuelos: ya hay {len(airports)} aeropuertos, no cargo vuelos (usa --completar-rutas para agregar los que falten)")
        by_code = {a["code"]: a["id"] for a in airports}
    else:
        by_code = {}
        for name, code, city in AIRPORTS:
            _, a = call("POST", FLIGHT + "/api/airports", {"name": name, "code": code, "city": city, "country": "Argentina"}, token)
            by_code[code] = a["id"]
        airlines = {}
        for name, code in AIRLINES:
            _, a = call("POST", FLIGHT + "/api/airlines", {"name": name, "code": code, "country": "Argentina", "logoUrl": None}, token)
            airlines[code] = a["id"]
        fares = []
        for name, kind, carry, checked, wifi, seat, base, tax in FARES:
            _, f = call("POST", FLIGHT + "/api/fares", {
                "name": name, "type": kind, "personalItem": True, "carryOn": carry, "checkedBaggage": checked,
                "wifi": wifi, "seatSelection": seat, "currency": "ARS", "baseFare": base, "taxesAndFees": tax,
                "transparentFinalPrice": base + tax}, token)
            fares.append(f["id"])

        crear_vuelos(token, by_code, airlines, fares, flight_numbers)

    if solo_vuelos:
        print("\nListo (solo vuelos: no se cargaron hoteles ni paquetes).")
        return

    # --- hoteles ---
    _, hotels = call("GET", HOTEL + "/hoteles", token=token)
    if hotels:
        print(f"hoteles: ya hay {len(hotels)}, no cargo hoteles")
        hotel_by_city = {h["ciudad"]: h["id"] for h in hotels}
    else:
        hotel_by_city = {}
        img = [
            "https://images.unsplash.com/photo-1611892440504-42a792e24d32",
            "https://images.unsplash.com/photo-1590490360182-c33d57733427",
            "https://images.unsplash.com/photo-1631049307264-da0ec9d70304",
        ]
        flexible = [{"horasAntes": 24, "porcentajeReembolso": 100}, {"horasAntes": 0, "porcentajeReembolso": 0}]
        escalonada = [{"horasAntes": 72, "porcentajeReembolso": 100}, {"horasAntes": 24, "porcentajeReembolso": 50},
                      {"horasAntes": 0, "porcentajeReembolso": 0}]
        no_reembolsable = [{"horasAntes": 0, "porcentajeReembolso": 0}]

        def hab(nombre, desc, cap, precio, unidades, i):
            return {"nombre": nombre, "descripcion": desc, "capacidad": cap, "precioPorNoche": precio,
                    "cantidadUnidades": unidades, "imagenes": [img[i % 3]]}

        hoteles = (
            ("Alvear Palace", "Buenos Aires", "Argentina", "Av. Alvear 1891", 5, False, escalonada,
             "America/Argentina/Buenos_Aires", "Palacio clásico en Recoleta con atención de mayordomo.",
             ["WIFI", "DESAYUNO", "SPA", "GIMNASIO", "RESTAURANTE", "AIRE_ACONDICIONADO"],
             [hab("Clásica doble", "Cama king o dos twin.", 2, 380000, 20, 0),
              hab("Suite Deluxe", "Living separado y vista a la avenida.", 3, 620000, 8, 1)]),
            ("Sheraton Córdoba", "Córdoba", "Argentina", "Duarte Quirós 1300", 4, False, flexible,
             "America/Argentina/Buenos_Aires", "Hotel de negocios a minutos del centro.",
             ["WIFI", "PILETA", "GIMNASIO", "ESTACIONAMIENTO", "RESTAURANTE"],
             [hab("Doble estándar", "Dos camas o una king.", 2, 145000, 30, 2),
              hab("Familiar", "Ideal para cuatro personas.", 4, 230000, 10, 0)]),
            ("Llao Llao Resort", "San Carlos de Bariloche", "Argentina", "Av. Bustillo km 25", 5, True, escalonada,
             "America/Argentina/Buenos_Aires", "Resort entre lagos y montañas, con todo incluido.",
             ["WIFI", "PILETA", "DESAYUNO", "SPA", "GIMNASIO", "RESTAURANTE", "TRASLADO"],
             [hab("Doble vista al bosque", "Balcón al bosque.", 2, 410000, 15, 1),
              hab("Doble vista al lago", "Vista al Nahuel Huapi.", 2, 520000, 10, 2),
              hab("Suite familiar", "Dos ambientes.", 4, 780000, 5, 0)]),
            ("Sheraton Mendoza", "Mendoza", "Argentina", "Primitivo de la Reta 989", 4, False, flexible,
             "America/Argentina/Buenos_Aires", "En el centro, cerca de bodegas y de la Peatonal.",
             ["WIFI", "PILETA", "DESAYUNO", "ESTACIONAMIENTO", "MASCOTAS"],
             [hab("Doble estándar", "Vista a la ciudad.", 2, 160000, 25, 2),
              hab("Triple", "Tres camas individuales.", 3, 210000, 8, 1)]),
            ("Hilton Madrid Airport", "Madrid", "España", "Av. de la Hispanidad 2", 4, False, no_reembolsable,
             "Europe/Madrid", "Junto a Barajas, con traslado gratis a las terminales.",
             ["WIFI", "GIMNASIO", "RESTAURANTE", "TRASLADO", "AIRE_ACONDICIONADO"],
             [hab("Doble", "Insonorizada.", 2, 230000, 40, 0)]),
            ("Fontainebleau Miami Beach", "Miami", "Estados Unidos", "4441 Collins Ave", 5, True, escalonada,
             "America/New_York", "Resort frente al mar con todo incluido.",
             ["WIFI", "PILETA", "SPA", "GIMNASIO", "RESTAURANTE", "AIRE_ACONDICIONADO"],
             [hab("Doble vista al mar", "Balcón al océano.", 2, 540000, 30, 1),
              hab("Suite junior", "Living integrado.", 3, 790000, 10, 2)]),
        )
        for i, (nombre, ciudad, pais, direccion, estrellas, todo, politica, zona, desc, servicios, habs) in enumerate(hoteles):
            _, h = call("POST", HOTEL + "/hoteles", {
                "nombre": nombre, "ciudad": ciudad, "pais": pais, "direccion": direccion, "estrellas": estrellas,
                "descripcion": desc, "allInclusive": todo, "imagenes": [img[i % 3], img[(i + 1) % 3]],
                "servicios": servicios, "politicaCancelacion": politica, "zonaHoraria": zona,
                "habitaciones": habs}, token)
            hotel_by_city[ciudad] = h["id"]
        print(f"hoteles: {len(hotel_by_city)} creados")

    # --- paquetes ---
    if sin_paquetes:
        print("paquetes: omitidos (--sin-paquetes)")
        packages = None
    else:
        _, packages = call("GET", PACKAGE + "/api/packages", token=token)
    if sin_paquetes:
        pass
    elif packages:
        print(f"paquetes: ya hay {len(packages)}, no cargo paquetes")
    else:
        for name, desc, dest, key, city, nights, price in (
            ("Escapada a Córdoba", "Vuelo y hotel 4 estrellas en Córdoba, sierras y vida cultural.", "Córdoba, Argentina", ("AR", "EZE", "COR"), "Córdoba", 4, 520),
            ("Bariloche Aventura", "Lagos, montañas y chocolate. Incluye all inclusive en Llao Llao.", "San Carlos de Bariloche, Argentina", ("AR", "AEP", "BRC"), "San Carlos de Bariloche", 6, 1450),
            ("Mendoza y sus vinos", "Ruta del vino con hotel 4 estrellas en Mendoza.", "Mendoza, Argentina", ("AR", "AEP", "MDZ"), "Mendoza", 5, 780),
        ):
            body = {"name": name, "description": desc, "destination": dest, "durationNights": nights, "basePrice": price,
                    "hotelId": hotel_by_city.get(city)}
            number = flight_numbers.get((*key, 0))  # solo si los vuelos se cargaron en esta corrida
            if number:
                body["flightNumber"] = number
            call("POST", PACKAGE + "/api/packages", body, token)
        print("paquetes: 3 creados")

    print("\nListo. Usuarios de prueba (solo desarrollo local):")
    print(f"  administrador: {ADMIN[0]} / {ADMIN[1]}")
    print(f"  cliente:       {CLIENT[0]} / {CLIENT[1]}")


if __name__ == "__main__":
    main()
