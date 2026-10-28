@echo off
rem Starts BarangayCare. Needs JDK 25+ and Maven.
cd /d "%~dp0"
mvn -q compile exec:java
