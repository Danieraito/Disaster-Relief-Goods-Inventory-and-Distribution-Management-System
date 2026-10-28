#!/bin/sh
# Compiles BarangayCare and starts the website (Mac/Linux). Run it from the project folder.
mkdir -p out
javac -cp "jars/*" -d out LIB/*.java SERVER/*.java || { echo "Compile failed. Fix the errors above and try again."; exit 1; }
echo "Starting BarangayCare... open http://localhost:8080 in your browser."
java -cp "out:jars/*" SERVER.WebServer
