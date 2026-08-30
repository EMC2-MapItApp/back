# ═══════════════════════════════════════════════════════════════
# setup-dev.ps1 — Levanta el entorno de desarrollo completo (back + front)
#
# Uso:
#   .\scripts\setup-dev.ps1              → arranca backend (8081) y frontend (4200)
#   .\scripts\setup-dev.ps1 -StartMongo  → además levanta MongoDB local
#
# Comprueba si ya hay una instancia escuchando en el puerto del backend
# (8081) o del frontend (4200); si la hay, pregunta si se debe detener
# antes de lanzar la nueva. Backend y frontend se abren como pestañas
# de una misma ventana de Windows Terminal (wt.exe) para que ambos
# queden corriendo en paralelo tras terminar este script.
# ═══════════════════════════════════════════════════════════════
param(
    [switch]$StartMongo  # Levanta MongoDB local con docker-compose.dev.yml
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ── Funciones (SRP: cada una hace una sola cosa) ────────────────

function Load-EnvFile {
    param([string]$Path)
    if (-not (Test-Path $Path)) {
        Write-Error "Fichero de entorno no encontrado: $Path"
        exit 1
    }
    Get-Content $Path | ForEach-Object {
        if ($_ -match '^\s*([^#=]+)=(.*)$') {
            [System.Environment]::SetEnvironmentVariable($Matches[1].Trim(), $Matches[2].Trim(), "Process")
        }
    }
    Write-Host "[dev] Variables cargadas desde $Path" -ForegroundColor Cyan
}

function Start-MongoLocal {
    Write-Host "[dev] Levantando MongoDB local..." -ForegroundColor Cyan
    docker-compose -f docker-compose.dev.yml up -d mongodb
    Write-Host "[dev] MongoDB disponible en localhost:27017" -ForegroundColor Green
}

# Comprueba si el puerto está ocupado y, si lo está, pregunta si se
# debe detener el proceso. Devuelve $true si el puerto queda libre
# para lanzar el nuevo servicio, $false si hay que omitir el arranque.
function Resolve-PortConflict {
    param([int]$Port, [string]$ServiceLabel)

    $conns = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if (-not $conns) { return $true }

    foreach ($processId in ($conns.OwningProcess | Select-Object -Unique)) {
        $proc = Get-Process -Id $processId -ErrorAction SilentlyContinue
        $procName = if ($proc) { $proc.ProcessName } else { "PID $processId" }
        Write-Host "[dev] Puerto $Port ocupado por $procName (PID $processId) — posible instancia previa de $ServiceLabel" -ForegroundColor Yellow
        $answer = Read-Host "¿Detener ese proceso para liberar el puerto ${Port}? (s/N)"
        if ($answer -match '^[sS]') {
            Stop-Process -Id $processId -Force
            Write-Host "[dev] Proceso $processId detenido" -ForegroundColor Green
        } else {
            Write-Host "[dev] Se mantiene el proceso existente en el puerto $Port — no se lanzará $ServiceLabel" -ForegroundColor Yellow
            return $false
        }
    }
    Start-Sleep -Seconds 1
    return $true
}

# Abre backend y/o frontend como pestañas de una misma ventana de
# Windows Terminal (una sola invocación de wt.exe encadenando
# "new-tab ... ; new-tab ..." para que ambas acaben en la misma ventana
# en vez de ventanas independientes).
function Start-DevTabs {
    param(
        [bool]$OpenBackend,
        [bool]$OpenFrontend,
        [string]$BackDir,
        [string]$WebDir
    )

    if ($OpenFrontend -and -not (Test-Path $WebDir)) {
        Write-Warning "[dev] No se encontró el directorio del frontend en $WebDir — se omite su arranque"
        $OpenFrontend = $false
    }

    if (-not $OpenBackend -and -not $OpenFrontend) { return }

    if (-not (Get-Command wt.exe -ErrorAction SilentlyContinue)) {
        Write-Error "wt.exe (Windows Terminal) no encontrado. Instálalo con 'winget install Microsoft.WindowsTerminal' o desde la Microsoft Store."
        exit 1
    }

    $wtArgs = @("-w", "0")
    $isFirstTab = $true

    if ($OpenBackend) {
        # spring-boot:run incluye dependencias 'provided' en classpath,
        # por lo que Tomcat embebido está disponible aunque el perfil
        # lo marque como provided (necesario para empaquetar WAR externo)
        #
        # Se usa "-d $BackDir" (--startingDirectory) en vez de anteponer
        # "Set-Location ...;" al -Command: wt.exe trocea la línea de comandos
        # por ';' como paso previo a cualquier análisis de comillas, así que
        # un ';' dentro del propio -Command (aunque vaya entrecomillado)
        # rompe el parseo. Evitar el ';' por completo es más robusto que
        # intentar escaparlo.
        $wtArgs += @("new-tab", "--title", "MapIt Backend", "-d", $BackDir, "powershell", "-NoExit", "-Command", ".\mvnw.cmd spring-boot:run -P dev")
        $isFirstTab = $false
        Write-Host "[dev] Backend: http://localhost:8081" -ForegroundColor Green
    }

    if ($OpenFrontend) {
        if (-not $isFirstTab) { $wtArgs += ";" }
        $wtArgs += @("new-tab", "--title", "MapIt Frontend", "-d", $WebDir, "powershell", "-NoExit", "-Command", "npm start")
        Write-Host "[dev] Frontend: http://localhost:4200" -ForegroundColor Green
    }

    # Se usa el operador de llamada (&) con splat en vez de
    # Start-Process -ArgumentList: este último, con un array, no entrecomilla
    # de forma fiable en Windows PowerShell 5.1 los elementos con espacios
    # (p. ej. "MapIt Backend" o el "-Command ..." completo), y wt.exe acaba
    # recibiendo palabras sueltas en vez de argumentos agrupados.
    & wt.exe @wtArgs
}

# ── Ejecución ───────────────────────────────────────────────────

$backDir = Split-Path $PSScriptRoot -Parent
$webDir  = Join-Path (Split-Path $backDir -Parent) "WEB"

$envFile = Join-Path $backDir ".env.dev"
Load-EnvFile -Path $envFile

if ($StartMongo) { Start-MongoLocal }

$openBackend  = Resolve-PortConflict -Port 8081 -ServiceLabel "backend"
$openFrontend = Resolve-PortConflict -Port 4200 -ServiceLabel "frontend"

Start-DevTabs -OpenBackend $openBackend -OpenFrontend $openFrontend -BackDir $backDir -WebDir $webDir

Write-Host "[dev] Listo — backend y frontend arrancando como pestañas de la misma ventana de Windows Terminal." -ForegroundColor Cyan
