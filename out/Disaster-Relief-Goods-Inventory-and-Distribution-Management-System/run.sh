#!/bin/sh
# Starts BarangayCare. Needs JDK 25+ and Maven.
cd "$(dirname "$0")" && mvn -q compile exec:java
