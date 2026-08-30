<#
.SYNOPSIS
    Integra desarrollo_back en main_back y sube el resultado a origin,
    disparando el pipeline CI/CD hacia Cloud Run.

.PARAMETER DryRun
    Ejecuta todas las comprobaciones sin realizar ninguna operación de escritura
    en git (checkout, merge, push). Útil para verificar el estado antes de actuar.

.PARAMETER Force
    Omite las confirmaciones interactivas en pasos críticos (checkout, merge, push).
    Usar en pipelines o cuando se tiene total seguridad del estado del repo.

.EXAMPLE
    .\scripts\deploy_back.ps1                   # modo interactivo normal
    .\scripts\deploy_back.ps1 -DryRun           # simulación sin cambios
    .\scripts\deploy_back.ps1 -Force            # sin confirmaciones
    .\scripts\deploy_back.ps1 -DryRun -Force    # simulación sin confirmaciones
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

# ── Cabecera ─────────────────────────────────────────────────────────────────
$modeTag = if ($DryRun) { " · DRY RUN" } else { "" }
Write-Host "`n══════════════════════════════════════════════════" -ForegroundColor Magenta
Write-Host "   DEPLOY BACK — merge desarrollo → main$modeTag   " -ForegroundColor Magenta
Write-Host "══════════════════════════════════════════════════`n" -ForegroundColor Magenta

if ($DryRun) { Write-Warn "Modo DRY RUN: las operaciones de escritura git NO se ejecutarán." }
if ($Force)  { Write-Warn "Modo FORCE: se omiten las confirmaciones interactivas." }

# ── 1. Verificar y cambiar a main_back ──────────────────────────────────────
# El script siempre opera sobre main_back. Si estamos en otra rama, pedimos
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
if ($CurrentBranch -ne "main_back") {
    Write-Warn "Rama actual: '$CurrentBranch'."
    if ($DryRun) {
        Write-Dry "Se haría checkout a 'main_back' (omitido en dry-run)."
        Write-Warn "Las comprobaciones siguientes se hacen desde '$CurrentBranch'; los resultados pueden no ser exactos."
    } else {
        if (-not (Confirm-Step "¿Cambiar a 'main_back' ahora?")) {
            Write-Err "Operación cancelada por el usuario."; exit 1
        }
        git checkout main_back
        if ($LASTEXITCODE -ne 0) { Write-Err "No se pudo cambiar a 'main_back'."; exit 1 }
        Write-OK "Cambiado a 'main_back'."
    }
} else {
    Write-OK "Ya estamos en 'main_back'."
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
git fetch origin main_back 2>$null
git fetch origin desarrollo_back 2>$null
$ErrorActionPreference = $previousEap
if ($LASTEXITCODE -ne 0) { Write-Err "Error al hacer fetch. Comprueba tu conexión."; exit 1 }
Write-OK "Fetch completado."

# ── 4. Verificar que main_back no está por detrás de origin/main_back ────────
# Si alguien hizo push a main_back, hay que incorporar esos cambios primero.
# De lo contrario el push final será rechazado (non-fast-forward).
Write-Step "Comprobando si 'main_back' está al día con origin..."
[string]$MainBehind = [string](git log HEAD..origin/main_back --oneline 2>&1)
if ($LASTEXITCODE -ne 0) { Write-Err "Error al comparar con origin/main_back."; exit 1 }
if ($MainBehind) {
    Write-Err "'main_back' local está por detrás de 'origin/main_back'. Haz 'git pull' antes de continuar."
    $MainBehind -split "`n" | ForEach-Object { Write-Info "  $_" }
    exit 1
}
Write-OK "'main_back' está al día con origin."

# ── 5. Verificar que desarrollo_back tiene commits nuevos que integrar ────────
# Si ambas ramas están al mismo nivel no hay nada que mergear; salimos limpiamente
# para evitar commits de merge vacíos que ensucian el historial.
Write-Step "Comprobando commits pendientes de integrar desde 'desarrollo_back'..."
[string]$PendingCommits = [string](git log HEAD..origin/desarrollo_back --oneline 2>&1)
if ($LASTEXITCODE -ne 0) { Write-Err "Error al comparar con origin/desarrollo_back."; exit 1 }
if (-not $PendingCommits) {
    Write-Warn "'desarrollo_back' no tiene commits nuevos respecto a 'main_back'. No hay nada que mergear."
    exit 0
}
Write-OK "Commits a integrar:"
$PendingCommits -split "`n" | ForEach-Object { Write-Info "  $_" }

# ── 6. Merge de desarrollo_back en main_back ─────────────────────────────────
# --no-ff fuerza un commit de merge explícito, preservando en el historial de
# main_back el contexto de la rama de desarrollo (qué se integró y cuándo).
Write-Step "Merge de 'origin/desarrollo_back' → 'main_back'..."
if ($DryRun) {
    Write-Dry "git merge origin/desarrollo_back --no-ff -m '...' (omitido en dry-run)"
    Write-OK "Simulación de merge completada."
} else {
    if (-not (Confirm-Step "¿Confirmas el merge de 'desarrollo_back' en 'main_back'?")) {
        Write-Err "Merge cancelado por el usuario."; exit 1
    }
    [string]$LastCommitMsg = [string](git log origin/desarrollo_back -1 --format="%s" 2>&1)
    git merge origin/desarrollo_back --no-ff -m " chore: merge desarrollo_back -> main_back [deploy] · $LastCommitMsg"
    if ($LASTEXITCODE -ne 0) {
        Write-Err "Error durante el merge. Resuelve los conflictos manualmente y vuelve a ejecutar el script."
        exit 1
    }
    Write-OK "Merge completado con éxito."
}

# ── 7. Push de main_back a origin ────────────────────────────────────────────
# El push a main_back dispara el workflow de GitHub Actions (.github/workflows/deploy.yml)
# que construye la imagen Docker y la despliega en Cloud Run.
Write-Step "Push de 'main_back' a origin..."
if ($DryRun) {
    Write-Dry "git push origin main_back (omitido en dry-run)"
    Write-OK "Simulación de push completada."
} else {
    if (-not (Confirm-Step "¿Confirmas el push de 'main_back' a origin? (esto lanzará el deploy en CI/CD)")) {
        Write-Err "Push cancelado. El merge LOCAL se hizo pero NO está en origin."
        Write-Warn "Cuando estés listo ejecuta: git push origin main_back"
        exit 1
    }
    git push origin main_back
    if ($LASTEXITCODE -ne 0) {
        Write-Err "Error al hacer push. Comprueba tu conexión y permisos."
        exit 1
    }
    Write-OK "'main_back' actualizada en origin."
}

# ── 8. Volver a desarrollo_back ──────────────────────────────────────────────
# Tras el deploy siempre se trabaja en desarrollo_back, nunca directamente en main.
Write-Step "Volviendo a 'desarrollo_back'..."
if ($DryRun) {
    Write-Dry "git checkout desarrollo_back (omitido en dry-run)"
} else {
    git checkout desarrollo_back
    if ($LASTEXITCODE -ne 0) {
        Write-Warn "No se pudo volver a 'desarrollo_back'. Cambia de rama manualmente."
    } else {
        Write-OK "De vuelta en 'desarrollo_back'."
    }
}

# ── Resumen final ─────────────────────────────────────────────────────────────
$finalMsg = if ($DryRun) { "Simulación completada. No se realizaron cambios en el repo." } `
            else         { "Listo. CI/CD debería arrancar el deploy a Cloud Run." }
Write-Host "`n══════════════════════════════════════════════════" -ForegroundColor Green
Write-Host "   $finalMsg" -ForegroundColor Green
Write-Host "══════════════════════════════════════════════════`n" -ForegroundColor Green




