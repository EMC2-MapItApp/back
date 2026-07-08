# ═══════════════════════════════════════════════════════════════
# setup-preprod.ps1 — Construye y publica la imagen Docker a GCR
#
# Prerequisitos:
#   - Docker Desktop corriendo
#   - gcloud CLI autenticado: gcloud auth login
#   - Docker autorizado: gcloud auth configure-docker europe-southwest1-docker.pkg.dev
#
# Uso:
#   .\scripts\setup-preprod.ps1             → compila + build + push
#   .\scripts\setup-preprod.ps1 -SkipBuild  → build Docker + push (sin recompilar)
#   .\scripts\setup-preprod.ps1 -SkipPush   → compila + build Docker (sin subir)
# ═══════════════════════════════════════════════════════════════
param(
    [switch]$SkipBuild,  # Usa el WAR existente en target/ sin recompilar
    [switch]$SkipPush    # Construye la imagen pero no la sube a GCR
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# Ruta completa de la imagen en Google Artifact Registry
$IMAGE = "europe-southwest1-docker.pkg.dev/mapitback/mapit-repo/backend:latest"

# ── Funciones (SRP) ─────────────────────────────────────────────

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
    Write-Host "[preprod] Prerequisitos verificados" -ForegroundColor Cyan
}

function Build-WarPreprod {
    Write-Host "[preprod] Compilando WAR con perfil Maven 'preprod'..." -ForegroundColor Cyan
    & .\mvnw.cmd clean package -P preprod -DskipTests -B
    if ($LASTEXITCODE -ne 0) {
        Write-Error "La compilación falló. Revisa los errores de Maven."
        exit 1
    }
    Write-Host "[preprod] WAR generado: target/mapIt-0.0.1-SNAPSHOT.war" -ForegroundColor Green
}

function Build-DockerImage {
    param([string]$ImageTag)
    Write-Host "[preprod] Construyendo imagen Docker: $ImageTag" -ForegroundColor Cyan
    docker build -t $ImageTag .
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Docker build falló."
        exit 1
    }
    Write-Host "[preprod] Imagen construida correctamente" -ForegroundColor Green
}

function Push-DockerImage {
    param([string]$ImageTag)
    Write-Host "[preprod] Autorizando Docker en Artifact Registry..." -ForegroundColor Cyan
    gcloud auth configure-docker europe-southwest1-docker.pkg.dev --quiet

    Write-Host "[preprod] Subiendo imagen: $ImageTag" -ForegroundColor Cyan
    docker push $ImageTag
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Docker push falló. Verifica autenticación con gcloud."
        exit 1
    }
    Write-Host "[preprod] Imagen publicada: $ImageTag" -ForegroundColor Green
}

# ── Ejecución ───────────────────────────────────────────────────

Assert-Prerequisites
if (-not $SkipBuild) { Build-WarPreprod }
Build-DockerImage -ImageTag $IMAGE
if (-not $SkipPush)  { Push-DockerImage -ImageTag $IMAGE }