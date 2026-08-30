# ═══════════════════════════════════════════════════════════════
# setup-dev.ps1 — Prepara y compila el entorno de desarrollo
#
# Uso:
#   .\scripts\setup-dev.ps1              → compila WAR
#   .\scripts\setup-dev.ps1 -StartMongo  → compila WAR + levanta MongoDB local
#   .\scripts\setup-dev.ps1 -SkipBuild   → solo levanta MongoDB
#
# Resultado: target/mapIt-0.0.1-SNAPSHOT.war
# Desplegar en Tomcat: copiar el WAR a /webapps de tu Tomcat local
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
    $composeFile = Join-Path (Split-Path $PSScriptRoot -Parent) "docker-compose.dev.yml"
    docker-compose -f $composeFile up -d mongodb
    Write-Host "[dev] MongoDB disponible en localhost:27017" -ForegroundColor Green
}

function Invoke-SpringBootRun {
    Write-Host "[dev] Arrancando aplicación con Spring Boot (perfil dev)..." -ForegroundColor Cyan
    Write-Host "[dev] Puerto: 8081 — http://localhost:8081" -ForegroundColor Cyan
    $projectRoot = Split-Path $PSScriptRoot -Parent
    $mvnw = Join-Path $projectRoot "mvnw.cmd"
    $pom = Join-Path $projectRoot "pom.xml"
    # spring-boot:run incluye dependencias 'provided' en classpath,
    # por lo que Tomcat embebido está disponible aunque el perfil
    # lo marque como provided (necesario para empaquetar WAR externo)
    # -f apunta al pom.xml explícitamente: mvnw.cmd no cambia de directorio,
    # así el script funciona sin importar desde dónde se invoque
    & $mvnw -f $pom spring-boot:run -P dev
}

# ── Ejecución ───────────────────────────────────────────────────

$envFile = Join-Path (Split-Path $PSScriptRoot -Parent) ".env.dev"
Load-EnvFile -Path $envFile

if ($StartMongo) { Start-MongoLocal }
Invoke-SpringBootRun