#!/usr/bin/env bash
# Carga datos de ejemplo (ver seed.py). Requiere MySQL en Docker y los servicios identity, flight, hotel y package en marcha.
set -euo pipefail
cd "$(dirname "$0")"
command -v python3 >/dev/null || { echo "Hace falta python3 (viene instalado en Linux y macOS)."; exit 1; }
if [ "${MYSQL_LOCAL:-}" != "1" ]; then
  command -v docker >/dev/null || { echo "Hace falta docker (se usa para asignar el rol de administrador), o MYSQL_LOCAL=1 con MySQL instalado."; exit 1; }
fi
exec python3 seed.py "$@"
