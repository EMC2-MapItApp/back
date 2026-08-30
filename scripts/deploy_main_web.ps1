<#
.SYNOPSIS
    Integra desarrollo_web en main_web y sube el resultado a origin,
    disparando el pipeline de despliegue automático en Cloudflare.

.PARAMETER DryRun
    Ejecuta todas las comprobaciones sin realizar ninguna operación de escritura
    en git (checkout, merge, push). Útil para verificar el estado antes de actuar.

.PARAMETER Force
    Omite las confirmaciones interactivas en pasos críticos (checkout, merge, push).
    Usar en pipelines o cuando se tiene total seguridad del estado del repo.

.EXAMPLE
    .\scripts\deploy_web.ps1                   # modo interactivo normal
    .\scripts\deploy_web.ps1 -DryRun           # simulación sin cambios
    .\scripts\deploy_web.ps1 -Force            # sin confirmaciones
    .\scripts\deploy_web.ps1 -DryRun -Force    # simulación sin confirmaciones
#>
param(
    [switch]$DryRun,
    [switch]$Force
)

Set-StrictMode -Version Latest
# $ErrorActionPreference se deja en "Continue" (por defecto) porque este script
# usa comandos externos (git) que escriben en stderr en operaciones normales.
# Los fallos de git se detectan explícitamente con $LASTEXITCODE tras cada llamada.

# ── Helpers de color y salida ────────────────────────────────────────────────
function Write-Step { param($msg) Write-Host "`n  >> $msg" -ForegroundColor Cyan }
function Write-OK   { param($msg) Write-Host "     [OK]  $msg" -ForegroundColor Green }
function Write-Warn { param($msg) Write-Host "     [!]   $msg" -ForegroundColor Yellow }
function Write-Err  { param($msg) Write-Host "     [X]   $msg" -ForegroundColor Red }
function Write-Info { param($msg) Write-Host "           $msg" -ForegroundColor DarkGray }
function Write-Dry  { param($msg) Write-Host "     [DRY] $msg" -ForegroundColor DarkYellow }

# Pide confirmación al usuario antes de un paso crítico.
# Con -Force o -DryRun devuelve siempre $true (no hay interacción).
function Confirm-Step {
    param([string]$Question)
    if ($Force -or $DryRun) { return $true }
    $resp = Read-Host "     [?]  $Question [S/n]"
    return ($resp -eq "" -or $resp -match "^[sSyY]")
}

# ── Resolver ruta del repo WEB (hermano de BACK) ──────────────────────────────
# Este script vive en BACK/scripts/; WEB está en el directorio adyacente.
$WebRoot = Join-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) "WEB"
if (-not (Test-Path $WebRoot)) {
    Write-Host "     [X]   No se encontró el directorio WEB en: $WebRoot" -ForegroundColor Red
    exit 1
}

# ── Cabecera ─────────────────────────────────────────────────────────────────
$modeTag = if ($DryRun) { " · DRY RUN" } else { "" }
Write-Host "`n══════════════════════════════════════════════════" -ForegroundColor Magenta
Write-Host "   DEPLOY WEB — merge desarrollo → main$modeTag   " -ForegroundColor Magenta
Write-Host "══════════════════════════════════════════════════`n" -ForegroundColor Magenta

if ($DryRun) { Write-Warn "Modo DRY RUN: las operaciones de escritura git NO se ejecutarán." }
if ($Force)  { Write-Warn "Modo FORCE: se omiten las confirmaciones interactivas." }

# Todas las operaciones git se hacen desde el root del repo WEB.
Push-Location $WebRoot

try {

# ── 1. Verificar y cambiar a main_web ────────────────────────────────────────
# El script siempre opera sobre main_web. Si estamos en otra rama, pedimos
# confirmación antes de hacer checkout para no interrumpir trabajo en curso.
Write-Step "Verificando rama activa..."
[string]$CurrentBranch = ""
$gitBranch = git rev-parse --abbrev-ref HEAD 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Err "No se pudo obtener la rama actual. ¿Estás dentro de un repositorio git?"
    Write-Info ([string]$gitBranch)
    exit 1
}
$CurrentBranch = [string]$gitBranch
if ($CurrentBranch -ne "main_web") {
    Write-Warn "Rama actual: '$CurrentBranch'."
    if ($DryRun) {
        Write-Dry "Se haría checkout a 'main_web' (omitido en dry-run)."
        Write-Warn "Las comprobaciones siguientes se hacen desde '$CurrentBranch'; los resultados pueden no ser exactos."
    } else {
        if (-not (Confirm-Step "¿Cambiar a 'main_web' ahora?")) {
            Write-Err "Operación cancelada por el usuario."; exit 1
        }
        git checkout main_web
        if ($LASTEXITCODE -ne 0) { Write-Err "No se pudo cambiar a 'main_web'."; exit 1 }
        Write-OK "Cambiado a 'main_web'."
    }
} else {
    Write-OK "Ya estamos en 'main_web'."
}

# ── 2. Verificar cambios locales sin confirmar ───────────────────────────────
# Un working tree sucio puede generar conflictos durante el merge o incluir
# cambios no intencionados en el commit de integración.
Write-Step "Comprobando cambios locales sin confirmar..."
[string]$Changes = [string](git status --porcelain 2>&1)
if ($LASTEXITCODE -ne 0) { Write-Err "Error al ejecutar 'git status'."; exit 1 }
if ($Changes) {
    Write-Err "Hay cambios locales sin confirmar. Confírmalos o descártalos antes de continuar."
    $Changes -split "`n" | ForEach-Object { Write-Info "  $_" }
    exit 1
}
Write-OK "Sin cambios locales pendientes."

# ── 3. Fetch de ambas ramas ──────────────────────────────────────────────────
# Actualizamos las referencias remotas para que las comprobaciones posteriores
# reflejen el estado real del servidor, no una caché local desactualizada.
# Nota: git fetch escribe en stderr en una operación normal (solo progreso).
# Al redirigir esa stderr (con "2>$null" o incluso "2>&1"), PowerShell la
# convierte en un ErrorRecord ANTES de aplicar el destino de la redirección;
# si la sesión que invoca (dot-source) tiene $ErrorActionPreference = "Stop",
# ese ErrorRecord aborta el script pese a la redirección. Para que solo este
# paso sea no-terminante (y el resto del script conserve el comportamiento
# de la sesión llamante), se baja $ErrorActionPreference a "Continue" solo
# alrededor de estas dos llamadas y se restaura justo después. Los fallos
# reales se siguen detectando con $LASTEXITCODE, igual que en el resto.
Write-Step "Sincronizando referencias remotas..."
$previousEap = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
git fetch origin main_web 2>$null
git fetch origin desarrollo_web 2>$null
$ErrorActionPreference = $previousEap
if ($LASTEXITCODE -ne 0) { Write-Err "Error al hacer fetch. Comprueba tu conexión."; exit 1 }
Write-OK "Fetch completado."

# ── 4. Verificar que main_web no está por detrás de origin/main_web ──────────
# Si alguien hizo push a main_web, hay que incorporar esos cambios primero.
# De lo contrario el push final será rechazado (non-fast-forward).
Write-Step "Comprobando si 'main_web' está al día con origin..."
[string]$MainBehind = [string](git log HEAD..origin/main_web --oneline 2>&1)
if ($LASTEXITCODE -ne 0) { Write-Err "Error al comparar con origin/main_web."; exit 1 }
if ($MainBehind) {
    Write-Err "'main_web' local está por detrás de 'origin/main_web'. Haz 'git pull' antes de continuar."
    $MainBehind -split "`n" | ForEach-Object { Write-Info "  $_" }
    exit 1
}
Write-OK "'main_web' está al día con origin."

# ── 5. Verificar que desarrollo_web tiene commits nuevos que integrar ─────────
# Si ambas ramas están al mismo nivel no hay nada que mergear; salimos limpiamente
# para evitar commits de merge vacíos que ensucian el historial.
Write-Step "Comprobando commits pendientes de integrar desde 'desarrollo_web'..."
[string]$PendingCommits = [string](git log HEAD..origin/desarrollo_web --oneline 2>&1)
if ($LASTEXITCODE -ne 0) { Write-Err "Error al comparar con origin/desarrollo_web."; exit 1 }
if (-not $PendingCommits) {
    Write-Warn "'desarrollo_web' no tiene commits nuevos respecto a 'main_web'. No hay nada que mergear."
    exit 0
}
Write-OK "Commits a integrar:"
$PendingCommits -split "`n" | ForEach-Object { Write-Info "  $_" }

# ── 6. Merge de desarrollo_web en main_web ───────────────────────────────────
# --no-ff fuerza un commit de merge explícito, preservando en el historial de
# main_web el contexto de la rama de desarrollo (qué se integró y cuándo).
Write-Step "Merge de 'origin/desarrollo_web' → 'main_web'..."
if ($DryRun) {
    Write-Dry "git merge origin/desarrollo_web --no-ff -m '...' (omitido en dry-run)"
    Write-OK "Simulación de merge completada."
} else {
    if (-not (Confirm-Step "¿Confirmas el merge de 'desarrollo_web' en 'main_web'?")) {
        Write-Err "Merge cancelado por el usuario."; exit 1
    }
    [string]$LastCommitMsg = [string](git log origin/desarrollo_web -1 --format="%s" 2>&1)
    git merge origin/desarrollo_web --no-ff -m "$ chore: merge desarrollo_web -> main_web [deploy] · $LastCommitMsg"
    if ($LASTEXITCODE -ne 0) {
        Write-Err "Error durante el merge. Resuelve los conflictos manualmente y vuelve a ejecutar el script."
        exit 1
    }
    Write-OK "Merge completado con éxito."
}

# ── 7. Push de main_web a origin ─────────────────────────────────────────────
# El push a main_web dispara el pipeline de Cloudflare que construye el bundle
# Angular y despliega el sitio estático en Cloudflare Workers + Assets.
Write-Step "Push de 'main_web' a origin..."
if ($DryRun) {
    Write-Dry "git push origin main_web (omitido en dry-run)"
    Write-OK "Simulación de push completada."
} else {
    if (-not (Confirm-Step "¿Confirmas el push de 'main_web' a origin? (esto lanzará el deploy en Cloudflare)")) {
        Write-Err "Push cancelado. El merge LOCAL se hizo pero NO está en origin."
        Write-Warn "Cuando estés listo ejecuta: git push origin main_web"
        exit 1
    }
    git push origin main_web
    if ($LASTEXITCODE -ne 0) {
        Write-Err "Error al hacer push. Comprueba tu conexión y permisos."
        exit 1
    }
    Write-OK "'main_web' actualizada en origin."
}

# ── 8. Volver a desarrollo_web ───────────────────────────────────────────────
# Tras el deploy siempre se trabaja en desarrollo_web, nunca directamente en main.
Write-Step "Volviendo a 'desarrollo_web'..."
if ($DryRun) {
    Write-Dry "git checkout desarrollo_web (omitido en dry-run)"
} else {
    git checkout desarrollo_web
    if ($LASTEXITCODE -ne 0) {
        Write-Warn "No se pudo volver a 'desarrollo_web'. Cambia de rama manualmente."
    } else {
        Write-OK "De vuelta en 'desarrollo_web'."
    }
}

# ── Resumen final ─────────────────────────────────────────────────────────────
$finalMsg = if ($DryRun) { "Simulación completada. No se realizaron cambios en el repo." } `
            else         { "Listo. CI/CD debería arrancar el deploy a Cloudflare." }
Write-Host "`n══════════════════════════════════════════════════" -ForegroundColor Green
Write-Host "   $finalMsg" -ForegroundColor Green
Write-Host "══════════════════════════════════════════════════`n" -ForegroundColor Green

} finally {
    # Siempre restauramos el directorio de trabajo original, incluso si el script falla.
    Pop-Location
}
