# UrbanPulse - lanzador local de las pruebas de Grafana k6
# Guardar este archivo en la RAIZ del repositorio, junto a compose.yaml.
# PowerShell 7. Uso: .\run-all-k6.ps1          (lecturas básicas)
#                  .\run-all-k6.ps1 -Full    (toda la batería, incluye escritura)
# IMPORTANTE: usar EXCLUSIVAMENTE un backend y PostgreSQL locales y aislados.
[CmdletBinding()]
param([switch]$Full)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
if ($PSVersionTable.PSVersion.Major -lt 7) { throw 'Se necesita PowerShell 7 para Read-Host -MaskInput.' }
$root = $PSScriptRoot
$scenarios = Join-Path $root 'k6/scenarios'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$results = Join-Path $root "k6/results/$stamp"
New-Item -ItemType Directory -Force -Path $results | Out-Null
$records = [System.Collections.Generic.List[object]]::new()

foreach ($command in @('k6', 'node')) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "Falta '$command'. Instálalo y abre una nueva consola PowerShell 7."
    }
}
if (-not (Test-Path (Join-Path $scenarios 'smoke-read.js'))) {
    throw 'No se encuentra k6/scenarios: guarda este archivo en la raíz del repositorio original.'
}

$env:BASE_URL = 'http://127.0.0.1:8080'
$env:K6_ENVIRONMENT = 'isolated-test'
$env:K6_WEB_DASHBOARD = 'true'
$env:K6_WEB_DASHBOARD_HOST = '127.0.0.1'
# Configuraciones de autorización se habilitan únicamente para las pruebas previstas.
Remove-Item Env:K6_ALLOW_HEAVY, Env:K6_ENABLE_WRITES, Env:RUN_ID, Env:DURATION_SECONDS -ErrorAction SilentlyContinue

function Read-RequiredEnv([string]$Name, [string]$Prompt, [switch]$Masked) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($Name))) {
        $value = if ($Masked) { Read-Host $Prompt -MaskInput } else { Read-Host $Prompt }
        if ([string]::IsNullOrWhiteSpace($value)) { throw "Falta $Name." }
        [Environment]::SetEnvironmentVariable($Name, $value, 'Process')
    }
}

function Invoke-K6Case([string]$Name, [string]$File, [switch]$MustPass) {
    $env:K6_WEB_DASHBOARD_EXPORT = Join-Path $results "$Name.html"
    $rawFile = Join-Path $results "$Name.json"
    $logFile = Join-Path $results "$Name.log"
    Write-Host "`n=== $Name ===" -ForegroundColor Cyan
    Write-Host 'Dashboard en directo: http://127.0.0.1:5665'
    & k6 run --out "json=$rawFile" (Join-Path $scenarios $File) 2>&1 |
        Tee-Object -FilePath $logFile
    $code = [int]$LASTEXITCODE
    $records.Add([pscustomobject]@{ Name=$Name; Code=$code; Html="$Name.html"; Json="$Name.json"; Log="$Name.log" })
    if ($code -ne 0) {
        Write-Warning "Finalizó con código ${code}: $Name. Consultar HTML, JSON y log."
        if ($MustPass) { throw "La prueba de preflight $Name falló; se detiene el resto de la batería." }
    }
}

function Write-ResultsIndex {
    $rows = foreach ($record in $records) {
        $name = [System.Net.WebUtility]::HtmlEncode($record.Name)
        $status = if ($record.Code -eq 0) { 'OK' } else { "REVISAR (exit $($record.Code))" }
        "<tr><td>$name</td><td>$status</td><td><a href='$($record.Html)'>Gráficos HTML</a></td><td><a href='$($record.Json)'>Muestras JSON</a></td><td><a href='$($record.Log)'>Consola</a></td></tr>"
    }
    $version = [System.Net.WebUtility]::HtmlEncode((& k6 version | Out-String).Trim())
    $index = @"
<!doctype html>
<html lang="es"><head><meta charset="utf-8"><title>UrbanPulse - resultados k6</title>
<style>body{font:16px system-ui,Arial;margin:3rem;max-width:1100px}table{border-collapse:collapse;width:100%}th,td{text-align:left;border-bottom:1px solid #ddd;padding:.8rem}th{background:#f3f5f9}a{color:#1858af}</style>
</head><body><h1>UrbanPulse - resultados k6</h1>
<p>Sesión: $stamp · $version</p>
<p>Cada prueba se ejecutó separadamente. Un estado REVISAR indica errores de ejecución o umbrales incumplidos; no atribuirlo automáticamente al backend.</p>
<p>Ver también los <a href="offline-syntax.log">análisis de sintaxis</a> y las <a href="offline-tests.log">regresiones offline de Node.js</a>.</p>
<table><thead><tr><th>Prueba</th><th>Estado</th><th>Informe</th><th>Datos detallados</th><th>Registro</th></tr></thead><tbody>
$($rows -join "`n")
</tbody></table></body></html>
"@
    Set-Content -LiteralPath (Join-Path $results 'index.html') -Value $index -Encoding utf8
    $records | Export-Csv -LiteralPath (Join-Path $results 'index.csv') -NoTypeInformation -Encoding utf8
    Write-Host "`nÍndice: $(Join-Path $results 'index.html')" -ForegroundColor Green
}

try {
    Write-Host 'Comprueba ANTES que localhost:8080 apunta a la BD local exclusiva de k6.' -ForegroundColor Yellow
    Write-Host 'No ejecutar contra producción ni contra una base de desarrollo que quieras conservar.' -ForegroundColor Yellow
    Write-Host "Resultados en: $results"

    # Tests offline: no hacen solicitudes HTTP y se ejecutan antes de la carga.
    Write-Host "`n=== Comprobaciones offline ===" -ForegroundColor Cyan
    & (Join-Path $root 'k6/check-syntax.ps1') 2>&1 |
        Tee-Object -FilePath (Join-Path $results 'offline-syntax.log')
    & node --experimental-vm-modules --test (Join-Path $root 'k6/tests/offline.test.mjs') 2>&1 |
        Tee-Object -FilePath (Join-Path $results 'offline-tests.log')
    if ($LASTEXITCODE -ne 0) { throw 'Fallaron las regresiones offline: no se lanza carga.' }

    Read-RequiredEnv 'CITIZEN_TOKEN' 'JWT de una cuenta CITIZEN de pruebas ya verificada' -Masked
    Read-RequiredEnv 'CITIZEN_INCIDENT_ID' 'UUID real de una incidencia propia del ciudadano'
    $env:ACTOR = 'CITIZEN'

    Invoke-K6Case '01-smoke-ciudadano' 'smoke-read.js' -MustPass
    $env:READ_PROFILE = 'load'
    Invoke-K6Case '02-load' 'read.js'
    Invoke-K6Case '03-arrival' 'arrival-rate-read.js'
    Invoke-K6Case '04-mapas' 'map-read.js'

    if ($Full) {
        # Las pruebas pesadas corren una detrás de otra, nunca en paralelo.
        $env:K6_ALLOW_HEAVY = '1'
        foreach ($profile in @('stress', 'spike', 'soak')) {
            $env:READ_PROFILE = $profile
            Invoke-K6Case ("05-$profile") 'read.js'
        }
        Remove-Item Env:K6_ALLOW_HEAVY -ErrorAction SilentlyContinue

        Read-RequiredEnv 'OPERATOR_TOKEN' 'JWT de cuenta OPERATOR de pruebas ya verificada' -Masked
        Read-RequiredEnv 'OPERATOR_INCIDENT_ID' 'UUID real de expediente visible al operador'
        $env:ACTOR = 'OPERATOR'
        Invoke-K6Case '08-smoke-operador' 'smoke-read.js' -MustPass
        foreach ($flow in @('processing', 'duplicates')) {
            $env:STAFF_FLOW = $flow
            Invoke-K6Case ("09-$flow") 'staff-read.js'
        }

        # Ultima prueba, porque es la única que crea y modifica registros.
        $env:ACTOR = 'CITIZEN'
        $env:K6_ENABLE_WRITES = '1'
        $env:RUN_ID = [guid]::NewGuid().ToString()
        Invoke-K6Case '11-smoke-escritura' 'smoke-incidents-write.js'
    }
}
finally {
    Write-ResultsIndex
    foreach ($name in @('CITIZEN_TOKEN','CITIZEN_INCIDENT_ID','OPERATOR_TOKEN','OPERATOR_INCIDENT_ID',
                       'ACTOR','READ_PROFILE','STAFF_FLOW','K6_ALLOW_HEAVY','K6_ENABLE_WRITES','RUN_ID',
                       'K6_ENVIRONMENT','K6_WEB_DASHBOARD','K6_WEB_DASHBOARD_HOST',
                       'K6_WEB_DASHBOARD_EXPORT')) {
        Remove-Item -Path "Env:$name" -ErrorAction SilentlyContinue
    }
}
