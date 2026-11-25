#!/bin/bash
# Uso: scripts/cliente-consola.sh [host] [puerto] [nombre]
cd "$(dirname "$0")/.." || exit 1
java -cp out Client "$@"
