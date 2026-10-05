#!/usr/bin/env python3
"""Carga datos de ejemplo para desarrollo local (aeropuertos, vuelos, hoteles, paquetes y usuarios).

Requisitos: MySQL (docker compose up -d) y los servicios identity (8080), flightservice (8081),
hotel-service (8083) y package-service (8086) en marcha. Es repetible: si ya hay datos, no los duplica.
Las fechas de los vuelos se calculan a partir de hoy, asi nunca quedan vencidas.
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
        cmd = ["mysql", f"-u{user}", "-e", sql]
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


def main():
    for url, name in ((IDENTITY, "identity-service"), (FLIGHT, "flightservice"), (HOTEL, "hotel-service"), (PACKAGE, "package-service")):
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
    start = dt.datetime.combine(dt.date.today() + dt.timedelta(days=14), dt.time(8, 0))
    if airports:
        print(f"vuelos: ya hay {len(airports)} aeropuertos, no cargo vuelos")
        by_code = {a["code"]: a["id"] for a in airports}
    else:
        by_code = {}
        for name, code, city, country in (
            ("Aeropuerto Internacional Ministro Pistarini", "EZE", "Buenos Aires", "Argentina"),
            ("Aeroparque Jorge Newbery", "AEP", "Buenos Aires", "Argentina"),
            ("Aeropuerto Internacional Ingeniero Ambrosio Taravella", "COR", "Córdoba", "Argentina"),
            ("Aeropuerto Internacional El Plumerillo", "MDZ", "Mendoza", "Argentina"),
            ("Aeropuerto Internacional Teniente Luis Candelaria", "BRC", "San Carlos de Bariloche", "Argentina"),
            ("Aeropuerto Internacional Martín Miguel de Güemes", "SLA", "Salta", "Argentina"),
            ("Aeropuerto Internacional Comodoro Arturo Merino Benítez", "SCL", "Santiago", "Chile"),
            ("Miami International Airport", "MIA", "Miami", "Estados Unidos"),
            ("Aeropuerto Adolfo Suárez Madrid-Barajas", "MAD", "Madrid", "España"),
        ):
            _, a = call("POST", FLIGHT + "/api/airports", {"name": name, "code": code, "city": city, "country": country}, token)
            by_code[code] = a["id"]
        airlines = {}
        for name, code, country in (("Aerolíneas Argentinas", "AR", "Argentina"), ("LATAM Airlines", "LA", "Chile"), ("Flybondi", "FO", "Argentina")):
            _, a = call("POST", FLIGHT + "/api/airlines", {"name": name, "code": code, "country": country, "logoUrl": None}, token)
            airlines[code] = a["id"]
        fares = []
        # Precios en pesos. Light no suma nada: el precio del vuelo ya es la tarifa mas barata.
        for name, kind, carry, checked, wifi, seat, base, tax in (
            ("Light", "LIGHT", True, False, False, "PAID", 0, 0),
            ("Standard", "STANDARD", True, True, True, "FREE", 40000, 5000),
        ):
            _, f = call("POST", FLIGHT + "/api/fares", {
                "name": name, "type": kind, "personalItem": True, "carryOn": carry, "checkedBaggage": checked,
                "wifi": wifi, "seatSelection": seat, "currency": "ARS", "baseFare": base, "taxesAndFees": tax,
                "transparentFinalPrice": base + tax}, token)
            fares.append(f["id"])
        routes = (  # aerolinea, origen, destino, precio por pasajero en ARS (tarifa Light), minutos
            ("AR", "EZE", "COR", 95000, 90), ("AR", "AEP", "BRC", 160000, 150), ("AR", "AEP", "MDZ", 120000, 120),
            ("AR", "EZE", "MAD", 1150000, 780), ("LA", "EZE", "SCL", 260000, 130), ("LA", "EZE", "MIA", 980000, 540),
            ("LA", "AEP", "SLA", 140000, 130), ("FO", "AEP", "COR", 70000, 85), ("FO", "AEP", "BRC", 130000, 150),
        )
        n = 0
        for i, (al, o, d, price, mins) in enumerate(routes):
            for offset in (0, 3, 7):
                for back in (False, True):
                    a, b = (d, o) if back else (o, d)
                    dep = start + dt.timedelta(days=offset, hours=i % 6 * 2 + (5 if back else 0))
                    n += 1
                    number = f"{al}{1000 + n}"
                    flight_numbers[(al, a, b, offset)] = number
                    call("POST", FLIGHT + "/api/flights", {
                        "flightNumber": number, "airlineId": airlines[al], "originAirportId": by_code[a],
                        "destinationAirportId": by_code[b], "departureTime": dep.isoformat(),
                        "arrivalTime": (dep + dt.timedelta(minutes=mins)).isoformat(), "price": price,
                        "availableSeats": 150, "status": "SCHEDULED", "faresId": fares}, token)
        print(f"vuelos: {n} creados, salidas {', '.join((start + dt.timedelta(days=d)).strftime('%d/%m/%Y') for d in (0, 3, 7))}")

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
    _, packages = call("GET", PACKAGE + "/api/packages", token=token)
    if packages:
        print(f"paquetes: ya hay {len(packages)}, no cargo paquetes")
    else:
        for name, desc, dest, key, city, nights, price in (
            ("Escapada a Córdoba", "Vuelo y hotel 4 estrellas en Córdoba, sierras y vida cultural.", "Córdoba, Argentina", ("AR", "EZE", "COR"), "Córdoba", 4, 520),
            ("Bariloche Aventura", "Lagos, montañas y chocolate. Incluye all inclusive en Llao Llao.", "San Carlos de Bariloche, Argentina", ("AR", "AEP", "BRC"), "San Carlos de Bariloche", 6, 1450),
            ("Mendoza y sus vinos", "Ruta del vino con hotel 4 estrellas en Mendoza.", "Mendoza, Argentina", ("AR", "AEP", "MDZ"), "Mendoza", 5, 780),
            ("Madrid Clásica", "Vuelo directo a Madrid y hotel cerca del aeropuerto.", "Madrid, España", ("AR", "EZE", "MAD"), "Madrid", 7, 2100),
            ("Miami Beach All Inclusive", "Sol y playa en Miami Beach con todo incluido.", "Miami, Estados Unidos", ("LA", "EZE", "MIA"), "Miami", 7, 2800),
        ):
            body = {"name": name, "description": desc, "destination": dest, "durationNights": nights, "basePrice": price,
                    "hotelId": hotel_by_city.get(city)}
            number = flight_numbers.get((*key, 0))  # solo si los vuelos se cargaron en esta corrida
            if number:
                body["flightNumber"] = number
            call("POST", PACKAGE + "/api/packages", body, token)
        print("paquetes: 5 creados")

    print("\nListo. Usuarios de prueba (solo desarrollo local):")
    print(f"  administrador: {ADMIN[0]} / {ADMIN[1]}")
    print(f"  cliente:       {CLIENT[0]} / {CLIENT[1]}")


if __name__ == "__main__":
    main()
