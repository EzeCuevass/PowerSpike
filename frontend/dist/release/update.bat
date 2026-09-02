@echo off
rem ============================================================
rem  PowerSpike update.bat
rem  Cierra la app, instala el nuevo .msi en silencio y relanza.
rem  Se ejecuta con elevación (UAC) desde el UpdaterService.
rem ============================================================

setlocal EnableDelayedExpansion

echo [PowerSpike] Actualizando a la nueva version...

rem --- 1. Cerrar la app si esta abierta ---
taskkill /IM "PowerSpike.exe" /F >nul 2>&1

rem --- 2. Esperar a que el proceso termine ---
:waitloop
tasklist /FI "IMAGENAME eq PowerSpike.exe" 2>nul | find /I "PowerSpike.exe" >nul
if not errorlevel 1 (
    timeout /t 1 /nobreak >nul
    goto waitloop
)

rem --- 3. Capturar el .msi de la misma carpeta (%~dp0 = carpeta del script, no el CWD) ---
for %%f in ("%~dp0PowerSpike-*.msi") do set "MSI_FILE=%%~nxf"
if not defined MSI_FILE (
    echo [PowerSpike] ERROR: no se encontro el .msi en la carpeta de updates.
    pause
    exit /b 1
)
echo [PowerSpike] Instalando %MSI_FILE%...
msiexec /i "%~dp0%MSI_FILE%" /qn /norestart

if errorlevel 1 (
    echo [PowerSpike] ERROR: la instalacion fallo (codigo %errorlevel%)
    pause
    exit /b 1
)

echo [PowerSpike] Instalado correctamente. Relanzando la app...

rem --- 4. Relanzar la app instalada ---
if exist "%ProgramFiles%\PowerSpike\PowerSpike.exe" (
    start "" "%ProgramFiles%\PowerSpike\PowerSpike.exe"
) else if exist "%LOCALAPPDATA%\PowerSpike\PowerSpike.exe" (
    start "" "%LOCALAPPDATA%\PowerSpike\PowerSpike.exe"
) else (
    echo [PowerSpike] App actualizada. Abrila manualmente.
)

exit /b 0