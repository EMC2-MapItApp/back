# ═══════════════════════════════════════════════════════════════
# commit-ai.ps1 — Genera con IA los mensajes de commit de BACK y WEB
#                  para un mismo cambio, actualiza el changelog de
#                  cara al usuario y hace el commit (+ push opcional)
#
# Uso:
#   .\scripts\commit-ai.ps1
#
# Requiere:
#   - ANTHROPIC_API_KEY, vía variable de entorno o vía BACK/.env.local
#     (gitignorado; línea "ANTHROPIC_API_KEY=sk-ant-...")
#   - Este script vive en BACK/scripts/; WEB debe ser el directorio
#     hermano ..\WEB (si no existe, se trabaja solo con BACK)
#   - Cambios en staging en BACK y/o WEB (git add), o se ofrece
#     hacer `git add -A` en cada repo que tenga cambios sin stage
#
# Flujo: genera con Claude, en una sola pasada, el mensaje de commit
#        de BACK (si aplica), el de WEB (si aplica) y UNA única
#        entrada de changelog de cara al usuario (o ninguna si el
#        cambio es puramente interno) -> se valida/edita todo junto
#        -> normaliza los mensajes de commit -> actualiza
#        WEB/public/assets/changelog.json -> commit en cada repo con
#        cambios -> pregunta si hacer git push
# ═══════════════════════════════════════════════════════════════

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$BackRoot = Split-Path $PSScriptRoot -Parent
$WebRoot = Join-Path (Split-Path $BackRoot -Parent) "WEB"
$ChangelogJsonPath = Join-Path $WebRoot "public\assets\changelog.json"

$AllowedTypes = @("feat", "fix", "refactor", "perf", "chore", "docs", "test", "style", "build", "ci", "revert")
$ChangelogTypes = @("feature", "fix", "improvement")

# ── Comprobaciones previas (SRP: cada función hace una sola cosa) ──

function Import-LocalEnvFile {
    param([Parameter(Mandatory)][string]$Path)

    if (-not (Test-Path $Path)) { return }

    Get-Content $Path | ForEach-Object {
        if ($_ -match '^\s*([^#=]+)=(.*)$') {
            $key = $Matches[1].Trim()
            # No pisa una variable ya presente en la sesión (p.ej. exportada a mano)
            if (-not [System.Environment]::GetEnvironmentVariable($key, "Process")) {
                [System.Environment]::SetEnvironmentVariable($key, $Matches[2].Trim(), "Process")
            }
        }
    }
}

function Assert-ApiKey {
    Import-LocalEnvFile -Path (Join-Path $BackRoot ".env.local")

    if (-not $env:ANTHROPIC_API_KEY) {
        Write-Error "Falta ANTHROPIC_API_KEY. Defínela con `$env:ANTHROPIC_API_KEY = '...' (solo esta sesión), 'setx ANTHROPIC_API_KEY ...' (persistente), o crea BACK\.env.local con la línea ANTHROPIC_API_KEY=sk-ant-..."
        exit 1
    }
}

function Test-GitRepo {
    param([Parameter(Mandatory)][string]$RepoPath)

    if (-not (Test-Path $RepoPath -PathType Container)) { return $false }
    git -C $RepoPath rev-parse --is-inside-work-tree *> $null
    return ($LASTEXITCODE -eq 0)
}

function Confirm-RepoStaged {
    param(
        [Parameter(Mandatory)][string]$RepoPath,
        [Parameter(Mandatory)][string]$Label
    )

    $staged = git -C $RepoPath diff --cached --name-only
    if ($staged) { return $true }

    $status = git -C $RepoPath status --porcelain
    if (-not $status) { return $false }

    Write-Host "[commit-ai] $Label tiene cambios sin stage." -ForegroundColor Yellow
    $answer = Read-Host "¿Añadir todos los cambios de $Label con 'git add -A'? (s/N)"
    if ($answer -notmatch '^[sS]') { return $false }

    git -C $RepoPath add -A
    return [bool](git -C $RepoPath diff --cached --name-only)
}

# ── Generación del cambio con Claude ────────────────────────────

function Get-RepoContext {
    param(
        [Parameter(Mandatory)][string]$RepoPath,
        [Parameter(Mandatory)][string]$Label
    )

    $stat = git -C $RepoPath diff --cached --stat | Out-String
    $diff = git -C $RepoPath diff --cached | Out-String

    $maxDiffChars = 12000
    $truncated = $false
    if ($diff.Length -gt $maxDiffChars) {
        $diff = $diff.Substring(0, $maxDiffChars)
        $truncated = $true
    }

    $recentLog = git -C $RepoPath log -15 --pretty=format:"%s" | Out-String

    [PSCustomObject]@{
        Label     = $Label
        Stat      = $stat.Trim()
        Diff      = $diff.Trim()
        Truncated = $truncated
        RecentLog = $recentLog.Trim()
    }
}

function Get-ChangeSummarySchema {
    @{
        type       = "object"
        properties = @{
            back_commit = @{ anyOf = @(@{ type = "string" }, @{ type = "null" }) }
            web_commit  = @{ anyOf = @(@{ type = "string" }, @{ type = "null" }) }
            changelog   = @{
                anyOf = @(
                    @{ type = "null" },
                    @{
                        type                 = "object"
                        properties           = @{
                            type        = @{ type = "string"; enum = $ChangelogTypes }
                            title       = @{ type = "string" }
                            description = @{ type = "string" }
                        }
                        required             = @("type", "title", "description")
                        additionalProperties = $false
                    }
                )
            }
        }
        required             = @("back_commit", "web_commit", "changelog")
        additionalProperties = $false
    }
}

function Invoke-ClaudeChangeSummary {
    param(
        [PSCustomObject]$BackContext,
        [PSCustomObject]$WebContext
    )

    $systemPrompt = @"
Eres un asistente que redacta cambios para MapIt, formado por dos repositorios git
independientes: el backend (Spring Boot / Java, MongoDB) y el frontend (Angular).
Vas a recibir el diff en staging de uno o ambos repositorios para UN MISMO cambio
lógico (una feature, un fix, etc.). Devuelve ÚNICAMENTE un objeto JSON, sin
explicaciones ni bloques de código markdown, con esta forma exacta:

{"back_commit": <string o null>, "web_commit": <string o null>, "changelog": <objeto o null>}

Reglas para back_commit / web_commit (rellena solo el campo del repositorio del que
recibas diff; el otro debe ser null):
- Formato Conventional Commits: "<tipo>(<alcance-opcional>): <resumen en minúsculas,
  imperativo, sin punto final>".
- Tipos permitidos: feat, fix, refactor, perf, chore, docs, test, style, build, ci, revert.
- Si el cambio lo justifica, añade cuerpo tras una línea en blanco con viñetas '- '.
- No inventes cambios que no estén reflejados en el diff correspondiente.
- Usa español, coherente con el resto de mensajes recientes de cada repositorio.

Reglas para "changelog" (objeto {"type","title","description"}, o null):
- Es una entrada de cara al usuario final de la app (no a desarrolladores), en
  español, tono cercano y claro, SIN jerga técnica (nada de nombres de clases,
  servicios, endpoints, tablas, etc.).
- "type" es exactamente uno de: "feature" (funcionalidad nueva), "fix" (corrección
  visible para el usuario) o "improvement" (mejora de algo existente).
- "title" es un título corto (2-6 palabras). "description" es una frase explicando
  el beneficio para el usuario.
- Si el cambio es puramente interno (refactor, tests, chores, CI, documentación
  técnica, etc.) y no tiene ningún efecto observable para el usuario final, usa
  "changelog": null.
- Aunque el cambio afecte a backend Y frontend a la vez, es UN SOLO cambio de cara
  al usuario: genera UNA sola entrada de changelog, nunca una por repositorio.
"@

    $parts = New-Object System.Collections.Generic.List[string]

    if ($BackContext) {
        $backTrunc = if ($BackContext.Truncated) { "`n[diff de backend truncado por longitud]" } else { "" }
        $parts.Add(@"
### Repositorio BACKEND
Commits recientes (referencia de estilo):
$($BackContext.RecentLog)

Archivos modificados (staged):
$($BackContext.Stat)

Diff (staged):
``````
$($BackContext.Diff)
``````
$backTrunc
"@)
    }

    if ($WebContext) {
        $webTrunc = if ($WebContext.Truncated) { "`n[diff de frontend truncado por longitud]" } else { "" }
        $parts.Add(@"
### Repositorio FRONTEND
Commits recientes (referencia de estilo):
$($WebContext.RecentLog)

Archivos modificados (staged):
$($WebContext.Stat)

Diff (staged):
``````
$($WebContext.Diff)
``````
$webTrunc
"@)
    }

    $userPrompt = ($parts -join "`n`n") + "`n`nGenera el JSON para este cambio."

    $body = @{
        model         = "claude-opus-4-8"
        max_tokens    = 1536
        system        = $systemPrompt
        messages      = @(@{ role = "user"; content = $userPrompt })
        output_config = @{ format = @{ type = "json_schema"; schema = (Get-ChangeSummarySchema) } }
    } | ConvertTo-Json -Depth 12

    # Se usa HttpClient en vez de Invoke-RestMethod: en Windows PowerShell 5.1
    # Invoke-RestMethod no siempre rellena $_.ErrorDetails.Message con el cuerpo
    # real en errores HTTP, lo que deja errores 4xx sin diagnóstico posible.
    Add-Type -AssemblyName System.Net.Http
    $httpClient = New-Object System.Net.Http.HttpClient
    try {
        $httpClient.DefaultRequestHeaders.Add("x-api-key", $env:ANTHROPIC_API_KEY)
        $httpClient.DefaultRequestHeaders.Add("anthropic-version", "2023-06-01")

        $content = New-Object System.Net.Http.StringContent($body, [System.Text.Encoding]::UTF8, "application/json")
        $httpResponse = $httpClient.PostAsync("https://api.anthropic.com/v1/messages", $content).GetAwaiter().GetResult()
        $responseText = $httpResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    }
    finally {
        $httpClient.Dispose()
    }

    if (-not $httpResponse.IsSuccessStatusCode) {
        Write-Error "[commit-ai] La API de Anthropic ha devuelto un error ($([int]$httpResponse.StatusCode)):`n$responseText"
        exit 1
    }

    $response = $responseText | ConvertFrom-Json

    $text = ($response.content | Where-Object { $_.type -eq "text" } | Select-Object -First 1).text
    $text = ($text -replace '^```(json)?\s*', '' -replace '```\s*$', '').Trim()
    $parsed = $text | ConvertFrom-Json

    [PSCustomObject]@{
        BackCommit = $parsed.back_commit
        WebCommit  = $parsed.web_commit
        Changelog  = if ($parsed.changelog) {
            [PSCustomObject]@{
                Type        = $parsed.changelog.type
                Title       = $parsed.changelog.title
                Description = $parsed.changelog.description
            }
        } else { $null }
    }
}

# ── Edición interactiva (BACK + WEB + changelog en un solo texto) ──

function Read-MessageFromEditor {
    param([string]$InitialContent = "")

    $tempFile = [System.IO.Path]::GetTempFileName()
    try {
        $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
        [System.IO.File]::WriteAllText($tempFile, $InitialContent, $utf8NoBom)

        $editor = if ($env:EDITOR) { $env:EDITOR } else { "notepad.exe" }
        Start-Process -FilePath $editor -ArgumentList $tempFile -Wait

        (Get-Content -Path $tempFile -Raw -Encoding UTF8) -replace "`r`n", "`n"
    }
    finally {
        Remove-Item -Path $tempFile -ErrorAction SilentlyContinue
    }
}

function Format-DraftForEditing {
    param([Parameter(Mandatory)][PSCustomObject]$Draft)

    $backText = if ($Draft.BackCommit) { $Draft.BackCommit } else { "" }
    $webText = if ($Draft.WebCommit) { $Draft.WebCommit } else { "" }
    $chType = if ($Draft.Changelog) { $Draft.Changelog.Type } else { "" }
    $chTitle = if ($Draft.Changelog) { $Draft.Changelog.Title } else { "" }
    $chDesc = if ($Draft.Changelog) { $Draft.Changelog.Description } else { "" }

    @"
# Revisa y edita lo que necesites. Deja una sección vacía para omitirla.
# tipo del changelog: feature | fix | improvement (vacío = sin entrada de changelog)

=== COMMIT BACK ===
$backText

=== COMMIT WEB ===
$webText

=== CHANGELOG ===
tipo: $chType
titulo: $chTitle
descripcion: $chDesc
"@
}

function ConvertFrom-DraftText {
    param([Parameter(Mandatory)][string]$Text)

    $sections = @{}
    $currentKey = $null
    $buffer = New-Object System.Collections.Generic.List[string]

    foreach ($rawLine in (($Text -replace "`r`n", "`n") -split "`n")) {
        if ($rawLine -match '^===\s*(?<key>[A-Z ]+?)\s*===\s*$') {
            if ($currentKey) { $sections[$currentKey] = ($buffer -join "`n").Trim() }
            $currentKey = $Matches['key']
            $buffer.Clear()
            continue
        }
        if ($rawLine -match '^\s*#') { continue }
        if ($currentKey) { $buffer.Add($rawLine) }
    }
    if ($currentKey) { $sections[$currentKey] = ($buffer -join "`n").Trim() }

    $backCommit = if ($sections.ContainsKey('COMMIT BACK') -and $sections['COMMIT BACK']) { $sections['COMMIT BACK'] } else { $null }
    $webCommit = if ($sections.ContainsKey('COMMIT WEB') -and $sections['COMMIT WEB']) { $sections['COMMIT WEB'] } else { $null }

    $changelog = $null
    if ($sections.ContainsKey('CHANGELOG') -and $sections['CHANGELOG']) {
        $chType = ""; $chTitle = ""; $chDesc = ""
        foreach ($line in ($sections['CHANGELOG'] -split "`n")) {
            if ($line -match '^\s*tipo\s*:\s*(?<v>.*)$') { $chType = $Matches['v'].Trim().ToLowerInvariant() }
            elseif ($line -match '^\s*titulo\s*:\s*(?<v>.*)$') { $chTitle = $Matches['v'].Trim() }
            elseif ($line -match '^\s*descripcion\s*:\s*(?<v>.*)$') { $chDesc = $Matches['v'].Trim() }
        }
        if ($ChangelogTypes -contains $chType -and $chTitle -and $chDesc) {
            $changelog = [PSCustomObject]@{ Type = $chType; Title = $chTitle; Description = $chDesc }
        }
    }

    [PSCustomObject]@{
        BackCommit = $backCommit
        WebCommit  = $webCommit
        Changelog  = $changelog
    }
}

function Confirm-Draft {
    param(
        [PSCustomObject]$BackContext,
        [PSCustomObject]$WebContext
    )

    $draft = Invoke-ClaudeChangeSummary -BackContext $BackContext -WebContext $WebContext

    while ($true) {
        Write-Host "`n[commit-ai] Propuesta:" -ForegroundColor Cyan
        Write-Host "----------------------------------------"
        Write-Host (Format-DraftForEditing -Draft $draft)
        Write-Host "----------------------------------------"

        $answer = Read-Host "¿Es válido? (s = sí / e = editar / r = regenerar / n = cancelar)"
        switch -Regex ($answer) {
            '^[sS]' { return $draft }
            '^[eE]' {
                $edited = Read-MessageFromEditor -InitialContent (Format-DraftForEditing -Draft $draft)
                $draft = ConvertFrom-DraftText -Text $edited
            }
            '^[rR]' {
                Write-Host "[commit-ai] Regenerando con Claude..." -ForegroundColor Cyan
                $draft = Invoke-ClaudeChangeSummary -BackContext $BackContext -WebContext $WebContext
            }
            default {
                Write-Host "[commit-ai] Cancelado." -ForegroundColor Yellow
                exit 0
            }
        }
    }
}

# ── Normalización de mensajes de commit ─────────────────────────

function ConvertTo-NormalizedCommitMessage {
    param([Parameter(Mandatory)][string]$Message)

    $lines = ($Message -replace "^``````\s*", "" -replace "``````\s*$", "").Trim() -split "`n"
    $subject = $lines[0].Trim()
    $body = if ($lines.Count -gt 1) { ($lines[1..($lines.Count - 1)] -join "`n").Trim() } else { "" }

    if ($subject -match '^(?<type>[a-zA-Z]+)(\((?<scope>[^)]+)\))?:\s*(?<desc>.+)$') {
        $type = $Matches['type'].ToLowerInvariant()
        if ($AllowedTypes -notcontains $type) { $type = "chore" }
        $scope = $Matches['scope']
        $desc = $Matches['desc'].Trim().TrimEnd('.')
        $desc = $desc.Substring(0, 1).ToLowerInvariant() + $desc.Substring(1)

        $subject = if ($scope) { "$type($scope): $desc" } else { "$type`: $desc" }
    }
    else {
        # No sigue el formato Conventional Commits: se prefija como chore
        $type = "chore"
        $subject = "chore: " + $subject.TrimEnd('.')
    }

    [PSCustomObject]@{
        Subject = $subject
        Body    = $body
        Type    = $type
        Full    = if ($body) { "$subject`n`n$body" } else { $subject }
    }
}

# ── Changelog de cara al usuario (WEB/public/assets/changelog.json) ──

function ConvertTo-ChangelogJsonText {
    param([Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Entries)

    # Serializado a mano (2 espacios de indentado, igual que el fichero original) en vez
    # de ConvertTo-Json: en Windows PowerShell 5.1 su formato difiere del de Prettier y
    # reescribiría el fichero entero en cada ejecución. ConvertTo-Json -Compress sobre un
    # único string sigue dando el escapado JSON correcto para cada valor.
    $blocks = foreach ($e in $Entries) {
        $date = $e.date | ConvertTo-Json -Compress
        $type = $e.type | ConvertTo-Json -Compress
        $title = $e.title | ConvertTo-Json -Compress
        $description = $e.description | ConvertTo-Json -Compress
        "  {`n    ""date"": $date,`n    ""type"": $type,`n    ""title"": $title,`n    ""description"": $description`n  }"
    }

    "[`n" + ($blocks -join ",`n") + "`n]`n"
}

function Update-ChangelogJson {
    param(
        [Parameter(Mandatory)][string]$Type,
        [Parameter(Mandatory)][string]$Title,
        [Parameter(Mandatory)][string]$Description
    )

    if (-not (Test-Path $ChangelogJsonPath)) {
        Write-Error "[commit-ai] No se encuentra $ChangelogJsonPath"
        exit 1
    }

    # OJO: @() debe envolver el valor ya asignado, no la tubería — ConvertFrom-Json
    # emite el array completo como un único objeto de tubería, y @(tubería) lo anidaría
    # como un array de un elemento en vez de aplanarlo.
    $entries = Get-Content -Path $ChangelogJsonPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $entries = @($entries)
    $newEntry = [PSCustomObject]@{
        date        = (Get-Date -Format "yyyy-MM-dd")
        type        = $Type
        title       = $Title
        description = $Description
    }
    $updated = @($newEntry) + $entries

    $json = ConvertTo-ChangelogJsonText -Entries $updated

    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($ChangelogJsonPath, $json, $utf8NoBom)

    git -C $WebRoot add "public/assets/changelog.json"
}

# ── Commit y push ────────────────────────────────────────────────

function New-RepoCommit {
    param(
        [Parameter(Mandatory)][string]$RepoPath,
        [Parameter(Mandatory)][string]$FullMessage
    )

    $tempFile = [System.IO.Path]::GetTempFileName()
    try {
        $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
        [System.IO.File]::WriteAllText($tempFile, $FullMessage, $utf8NoBom)
        git -C $RepoPath commit --file $tempFile
        if ($LASTEXITCODE -ne 0) {
            Write-Error "[commit-ai] 'git commit' ha fallado en $RepoPath."
            exit 1
        }
    }
    finally {
        Remove-Item -Path $tempFile -ErrorAction SilentlyContinue
    }
}

function Confirm-PushRepos {
    param([Parameter(Mandatory)][string[]]$RepoPaths)

    $answer = Read-Host "`n¿Quieres hacer 'git push' en los repos con commits nuevos? (s/N)"
    if ($answer -notmatch '^[sS]') { return }

    foreach ($repoPath in $RepoPaths) {
        $branch = (git -C $repoPath rev-parse --abbrev-ref HEAD).Trim()
        git -C $repoPath rev-parse --abbrev-ref --symbolic-full-name '@{u}' *> $null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "[commit-ai] git push ($repoPath)" -ForegroundColor Cyan
            git -C $repoPath push
        }
        else {
            Write-Host "[commit-ai] git push -u origin $branch ($repoPath)" -ForegroundColor Cyan
            git -C $repoPath push -u origin $branch
        }
    }
}

# ── Ejecución ────────────────────────────────────────────────────

Assert-ApiKey

if (-not (Test-GitRepo -RepoPath $BackRoot)) {
    Write-Error "'$BackRoot' no es un repositorio git."
    exit 1
}

$webAvailable = Test-GitRepo -RepoPath $WebRoot
if (-not $webAvailable) {
    Write-Host "[commit-ai] No se encontró el repositorio WEB en '$WebRoot'; se trabaja solo con BACK." -ForegroundColor DarkGray
}

$backHasStaged = Confirm-RepoStaged -RepoPath $BackRoot -Label "BACK"
$webHasStaged = if ($webAvailable) { Confirm-RepoStaged -RepoPath $WebRoot -Label "WEB" } else { $false }

if (-not $backHasStaged -and -not $webHasStaged) {
    Write-Host "[commit-ai] No hay cambios que commitear en BACK ni en WEB." -ForegroundColor Yellow
    exit 0
}

$backContext = if ($backHasStaged) { Get-RepoContext -RepoPath $BackRoot -Label "BACK" } else { $null }
$webContext = if ($webHasStaged) { Get-RepoContext -RepoPath $WebRoot -Label "WEB" } else { $null }

$final = Confirm-Draft -BackContext $backContext -WebContext $webContext

$normBack = if ($final.BackCommit) { ConvertTo-NormalizedCommitMessage -Message $final.BackCommit } else { $null }
$normWeb = if ($final.WebCommit) { ConvertTo-NormalizedCommitMessage -Message $final.WebCommit } else { $null }
$webCommitFull = if ($normWeb) { $normWeb.Full } else { $null }

if ($final.Changelog) {
    Update-ChangelogJson -Type $final.Changelog.Type -Title $final.Changelog.Title -Description $final.Changelog.Description
    Write-Host "[commit-ai] changelog.json actualizado ($($final.Changelog.Type)): $($final.Changelog.Title)" -ForegroundColor Green

    if (-not $webCommitFull) {
        $webCommitFull = "docs: registrar en el changelog `"$($final.Changelog.Title)`""
    }
}
else {
    Write-Host "[commit-ai] Sin entrada de changelog (cambio interno)." -ForegroundColor DarkGray
}

$committedRepos = New-Object System.Collections.Generic.List[string]

if ($normBack) {
    New-RepoCommit -RepoPath $BackRoot -FullMessage $normBack.Full
    $committedRepos.Add($BackRoot)
    Write-Host "[commit-ai] Commit creado en BACK." -ForegroundColor Green
}

if ($webCommitFull) {
    New-RepoCommit -RepoPath $WebRoot -FullMessage $webCommitFull
    $committedRepos.Add($WebRoot)
    Write-Host "[commit-ai] Commit creado en WEB." -ForegroundColor Green
}

if ($committedRepos.Count -eq 0) {
    Write-Host "[commit-ai] No se ha creado ningún commit." -ForegroundColor Yellow
    exit 0
}

Confirm-PushRepos -RepoPaths $committedRepos
