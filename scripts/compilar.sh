#!/bin/bash
# Compila todo el proyecto en ./out. Requiere JDK 21 y JAVAFX_HOME (carpeta del SDK de JavaFX 21).
cd "$(dirname "$0")/.." || exit 1
: "${JAVAFX_HOME:?Define JAVAFX_HOME, p. ej. export JAVAFX_HOME=~/javafx-sdk-21.0.x}"
mkdir -p out
javac --module-path "$JAVAFX_HOME/lib" --add-modules javafx.controls -d out src/*.java && echo "Compilado en ./out"
