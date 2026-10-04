#!/usr/bin/env bash
# Carga datos de ejemplo (ver seed.py). Requiere MySQL en Docker y los servicios identity, flight, hotel y package en marcha.
set -euo pipefail
cd "$(dirname "$0")"
command -v python3 >/dev/null || { echo "Hace falta python3 (viene instalado en Linux y macOS)."; exit 1; }
command -v docker >/dev/null || { echo "Hace falta docker (se usa para asignar el rol de administrador)."; exit 1; }
exec python3 seed.py "$@"
