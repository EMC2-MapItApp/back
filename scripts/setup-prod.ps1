# ═══════════════════════════════════════════════════════════════
# setup-prod.ps1 - Construye y publica la imagen Docker a GCR
#
# Prerequisitos:
#   - Docker Desktop corriendo
#   - gcloud CLI autenticado: gcloud auth login
#
# Uso:
#   .\scripts\setup-prod.ps1                        -> build + push + deploy
#   .\scripts\setup-prod.ps1 -SkipPush              -> solo construye la imagen (sin subir)
#   .\scripts\setup-prod.ps1 -SkipDeploy            -> build + push (sin redesplegar Cloud Run)
#
# Nota: el Dockerfile incluye el build Maven internamente (multi-stage).
# Las variables de aplicacion (MongoDB, JWT) se inyectan en Google Cloud Run.
# ═══════════════════════════════════════════════════════════════
param(
       [switch]$SkipPush,    # Construye la imagen pero no la sube a GCR
    [switch]$SkipDeploy   # Sube la imagen pero no despliega en Cloud Run
)

#COnstantes de configuracion
$SERVICE = "backend"
$REGION  = "europe-southwest1"
$PROJECT = "mapitback"

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# Ruta completa de la imagen en Google Artifact Registry
$IMAGE = "europe-southwest1-docker.pkg.dev/mapitback/mapit-repo/backend:latest"

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
    Write-Host "[prod] Variables cargadas desde $Path" -ForegroundColor Cyan
}

function Assert-Prerequisites {
    # Fail Fast: verificar herramientas antes de empezar
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        Write-Error "Docker no encontrado. Instala Docker Desktop."
        exit 1
    }
    if (-not (Get-Command gcloud -ErrorAction SilentlyContinue)) {
        Write-Error "gcloud CLI no encontrado. Instala Google Cloud SDK."
        exit 1
    }
    Write-Host "[prod] Prerequisitos verificados" -ForegroundColor Cyan
}

function Build-DockerImage {
    param([string]$ImageTag)
    # El Dockerfile ejecuta mvnw clean package -P prod internamente (Stage 1)
    # No es necesario compilar el WAR previamente de forma local
    Write-Host "[prod] Construyendo imagen Docker: $ImageTag" -ForegroundColor Cyan
    docker build -t $ImageTag .
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Docker build fallo. Revisa los errores anteriores."
        exit 1
    }
    Write-Host "[prod] Imagen construida correctamente" -ForegroundColor Green
}

function Push-DockerImage {
    param([string]$ImageTag)
    # Idempotente: si ya esta configurado no hace nada, pero renueva token si expiro
    Write-Host "[prod] Autorizando Docker en Artifact Registry..." -ForegroundColor Cyan
    gcloud auth configure-docker europe-southwest1-docker.pkg.dev --quiet

    Write-Host "[prod] Subiendo imagen: $ImageTag" -ForegroundColor Cyan
    docker push $ImageTag
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Docker push fallo. Verifica autenticacion con: gcloud auth login"
        exit 1
    }
    Write-Host "[prod] Imagen publicada: $ImageTag" -ForegroundColor Green
}

function Deploy-CloudRun {
    param(
        [string]$ServiceName,
        [string]$ImageTag,
        [string]$Region,
        [string]$Project
    )
    Write-Host "[prod] Desplegando nueva revision en Cloud Run: $ServiceName" -ForegroundColor Cyan
    gcloud run deploy $ServiceName `
        --image $ImageTag `
        --region $Region `
        --project $Project `
        --quiet
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Cloud Run deploy fallo. Revisa los errores anteriores."
        exit 1
    }
    Write-Host "[prod] Servicio Cloud Run actualizado y reiniciado: $ServiceName" -ForegroundColor Green
}

# ── Ejecucion ───────────────────────────────────────────────────

$envFile = Join-Path (Split-Path $PSScriptRoot -Parent) ".env.prod"
Load-EnvFile -Path $envFile

Assert-Prerequisites
Build-DockerImage -ImageTag $IMAGE
if (-not $SkipPush) {
    Push-DockerImage -ImageTag $IMAGE
    if (-not $SkipDeploy) {
        Deploy-CloudRun -ServiceName $SERVICE -ImageTag $IMAGE -Region $REGION -Project $PROJECT
    }
}


