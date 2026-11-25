#!/bin/bash
# Uso: scripts/servidor.sh [puerto]   (por defecto 5050)
cd "$(dirname "$0")/.." || exit 1
java -cp out Server "$@"
