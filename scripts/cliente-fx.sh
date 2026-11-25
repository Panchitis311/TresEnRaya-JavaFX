#!/bin/bash
# Cliente grafico JavaFX. Los datos de conexion se piden en la ventana.
cd "$(dirname "$0")/.." || exit 1
: "${JAVAFX_HOME:?Define JAVAFX_HOME, p. ej. export JAVAFX_HOME=~/javafx-sdk-21.0.x}"
java --module-path "$JAVAFX_HOME/lib" --add-modules javafx.controls -cp out ClientFX
