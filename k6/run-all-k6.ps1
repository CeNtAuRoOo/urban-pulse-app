#requires -Version 7
[CmdletBinding()]
param()

# Forzar UTF-8 para la salida de Node.js y k6
$utf8 = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding = $utf8
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
if (-not (Get-Command k6 -CommandType Application -ErrorAction SilentlyContinue)) {
    if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
        throw 'Instala Grafana k6 y abre de nuevo PowerShell 7.'
    }
    if ((Read-Host '¿Instalar Grafana k6 con winget? (S/N)') -ine 'S') {
        Write-Host 'Puedes instalarlo con winget install --id GrafanaLabs.k6 --exact --source winget.'
        return
    }
    winget install --id GrafanaLabs.k6 --exact --source winget
    if ($LASTEXITCODE) { throw "winget terminó con código $LASTEXITCODE." }
    Write-Host 'cierra completamente Visual Studio Code, vuelve a abrirlo y vuelve a ejecutar ./k6/run-all-k6.ps1 para actualizar PATH'
    return
}
Get-Command node,docker -CommandType Application | Out-Null
$root = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $root 'compose.yaml'
$scenarios = Join-Path $PSScriptRoot 'scenarios'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$results = Join-Path $PSScriptRoot "results/$stamp"
New-Item -ItemType Directory -Path $results | Out-Null
$records = [System.Collections.Generic.List[object]]::new()
$k6Version = 'Versión sin comprobar'
$base = 'http://127.0.0.1:8080'
$prefix = '029b0000-0000-7000-8000-000000000'
$previous = @{}

function Set-LocalEnv([string]$Name, [string]$Value) {
    if (-not $previous.ContainsKey($Name)) { $previous[$Name] = [Environment]::GetEnvironmentVariable($Name) }
    if ($Value) { [Environment]::SetEnvironmentVariable($Name, $Value) }
    else { Remove-Item "Env:$Name" -ErrorAction SilentlyContinue }
}

function Read-Docker([string[]]$Arguments) {
    $result = & docker @Arguments
    if ($LASTEXITCODE) { throw "Falló Docker durante la comprobación de aislamiento (código $LASTEXITCODE)." }
    return $result
}

function Assert-Compose([switch]$BeforeLogin) {
    $context = Read-Docker @('context','show')
    $endpoint = Read-Docker @('context','inspect',$context,'--format','{{.Endpoints.docker.Host}}')
    if ($env:DOCKER_HOST -or $endpoint -notmatch '^(npipe:|unix:)') { throw 'Se necesita un motor Docker local.' }
    $compose = @('compose','-f',$composeFile)
    $port = Read-Docker ($compose + @('port','backend','8080'))
    if ($port -ne '127.0.0.1:8080') { throw 'El puerto local no pertenece al backend de este Compose.' }
    $seed = Read-Docker ($compose + @('ps','-a','-q','db-seed'))
    if (-not $seed -or (Read-Docker @('inspect','-f','{{.State.Status}}/{{.State.ExitCode}}',$seed)) -ne 'exited/0') {
        throw 'db-seed debe haber terminado correctamente.'
    }
    $db = Read-Docker ($compose + @('ps','-q','postgres'))
    $mounted = Read-Docker @('inspect','-f','{{index .HostConfig.Tmpfs "/var/lib/postgresql"}}',$db)
    if ($mounted -ne 'size=512m') { throw 'El almacenamiento PostgreSQL debe ser desechable.' }
    $url = Read-Docker ($compose + @('exec','-T','backend','printenv','SPRING_DATASOURCE_URL'))
    if ($url -cne 'jdbc:postgresql://postgres:5432/urban-pulse?ApplicationName=urbanpulse-compose') {
        throw 'El backend debe usar la conexión de desarrollo de Compose.'
    }
    $backend = Read-Docker ($compose + @('ps','-q','backend'))
    $address = Read-Docker @('inspect','-f','{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}',$backend)
    $parsed = [ipaddress]::Parse($address)
    $sql = "SELECT current_setting('urbanpulse.environment', true) = 'compose-dev' AND EXISTS (SELECT FROM public.dev_seed_completed)"
    if (-not $BeforeLogin) {
        $token = [Environment]::GetEnvironmentVariable("$($env:ACTOR)_TOKEN")
        $null = Invoke-RestMethod -NoProxy -MaximumRedirection 0 -Uri "$base/api/v1/users/me" -Headers @{Authorization="Bearer $token"}
        $sql += @"
 AND EXISTS (SELECT FROM pg_stat_activity WHERE datname=current_database()
               AND application_name='urbanpulse-compose' AND client_addr=inet('$parsed'))
"@
    }
    $verified = Read-Docker ($compose + @('exec','-T','postgres','psql','-XAt','-v','ON_ERROR_STOP=1','-U','urbanpulse','-d','urban-pulse','-c',$sql))
    if ($verified -ne 't') { throw 'La BD local no acredita una conexión activa del backend y la carga confirmada.' }
}

function Connect-Actor([string]$Role, [string]$Suffix) {
    $email = "k6.$($Role.ToLowerInvariant())@urbanpulse.test"
    $body = @{email=$email; password='demo-password'} | ConvertTo-Json -Compress
    $token = (Invoke-RestMethod -NoProxy -MaximumRedirection 0 -Method Post -Uri "$base/api/v1/auth/login" -ContentType 'application/json' -Body $body).accessToken
    $me = Invoke-RestMethod -NoProxy -MaximumRedirection 0 -Uri "$base/api/v1/users/me" -Headers @{Authorization="Bearer $token"}
    $expected = if ($Role -eq 'OPERATOR') { 'CITIZEN,OPERATOR' } else { 'CITIZEN' }
    if (-not $token -or $me.id -ne "$prefix$Suffix" -or ($me.roles -join ',') -ne $expected -or
        -not $me.active -or -not $me.emailVerified) { throw "Perfil de pruebas inesperado para $Role." }
    Set-LocalEnv "${Role}_TOKEN" $token
}

function Invoke-K6Case([string]$Name, [string]$File, [switch]$MustPass) {
    Assert-Compose
    $html = Join-Path $results "$Name.html"
    Set-LocalEnv K6_WEB_DASHBOARD_EXPORT $html
    $period = if ($File.StartsWith('smoke-')) { '50ms' } else { '1s' }
    Set-LocalEnv K6_WEB_DASHBOARD_PERIOD $period
    $raw = Join-Path $results "$Name.json"
    & k6 run --no-color --out "json=$raw" (Join-Path $scenarios $File) 2>&1 |
        Tee-Object -FilePath (Join-Path $results "$Name.log") | Out-Host
    $code = $LASTEXITCODE
    $records.Add([pscustomobject]@{ Name=$Name; Code=$code; Html="$Name.html"; Json="$Name.json"; Log="$Name.log" })
    if ($code -and ($MustPass -or $code -ne 99)) { throw "$Name terminó con código $code. Consulta su registro." }
}

function Write-CaseSummary([string]$Name, [int]$Code) {
    $html = Join-Path $results "$Name.html"
    if (-not (Test-Path -LiteralPath $html)) {
        # k6 omite el dashboard cuando la ejecución termina antes de reunir muestras suficientes.
        $summary = [System.Net.WebUtility]::HtmlEncode((Get-Content -Raw -LiteralPath (Join-Path $results "$Name.log")))
        $samples = if (Test-Path -LiteralPath (Join-Path $results "$Name.json")) {
            "<a href='$Name.json'>Muestras JSON</a> · "
        }
        $page = @"
<!doctype html><html lang="es"><meta charset="utf-8"><title>$Name</title>
<h1>$Name</h1><p>k6 terminó con código $code y no exportó el dashboard.</p>
<p>$samples<a href="$Name.log">Registro original</a></p>
<pre>$summary</pre></html>
"@
        Set-Content -LiteralPath $html -Value $page -Encoding utf8
    }
}

function Write-ResultsIndex {
    $rows = foreach ($record in $records) {
        Write-CaseSummary $record.Name $record.Code
        $name = [System.Net.WebUtility]::HtmlEncode($record.Name)
        $status = if ($record.Code -eq 0) { 'OK' } else { "REVISAR (exit $($record.Code))" }
        $links = foreach ($file in @($record.Html, $record.Json, $record.Log)) {
            if (Test-Path -LiteralPath (Join-Path $results $file)) { "<td><a href='$file'>$file</a></td>" }
            else { '<td>Sin archivo</td>' }
        }
        "<tr><td>$name</td><td>$status</td>$($links -join '')</tr>"
    }
    $version = [System.Net.WebUtility]::HtmlEncode($k6Version)
    $index = @"
<!doctype html>
<html lang="es"><head><meta charset="utf-8"><title>UrbanPulse - resultados k6</title>
<style>body{font:16px system-ui,Arial;margin:3rem;max-width:1100px}table{border-collapse:collapse;width:100%}th,td{text-align:left;border-bottom:1px solid #ddd;padding:.8rem}th{background:#f3f5f9}a{color:#1858af}</style>
</head><body><h1>UrbanPulse - resultados k6</h1>
<p>Sesión: $stamp · $version</p>
<p>Cada prueba se ejecutó separadamente. Un estado REVISAR indica errores de ejecución o umbrales incumplidos; no atribuirlo automáticamente al backend.</p>
<p>Ver las <a href="offline-tests.log">regresiones offline de Node.js</a>.</p>
<table><thead><tr><th>Prueba</th><th>Estado</th><th>Informe</th><th>Datos detallados</th><th>Registro</th></tr></thead><tbody>
$($rows -join "`n")
</tbody></table></body></html>
"@
    Set-Content -LiteralPath (Join-Path $results 'index.html') -Value $index -Encoding utf8
    $records | Export-Csv -LiteralPath (Join-Path $results 'index.csv') -NoTypeInformation -Encoding utf8
    Write-Host "`nÍndice: $(Join-Path $results 'index.html')" -ForegroundColor Green
}


try {
    & node --experimental-vm-modules --test (Join-Path $PSScriptRoot 'tests/offline.test.mjs') 2>&1 |
        Tee-Object -FilePath (Join-Path $results 'offline-tests.log') | Out-Host
    if ($LASTEXITCODE) { throw "Pruebas offline fallidas (Node $LASTEXITCODE)." }

    # Descarta opciones heredadas que puedan redirigir tráfico, registrar cabeceras o activar escenarios.
    foreach ($var in @(Get-ChildItem Env:K6_*)) { Set-LocalEnv $var.Name '' }
    $k6Version = & k6 version
    if ($LASTEXITCODE) { throw "No se pudo consultar la versión de k6 ($LASTEXITCODE)." }
    foreach ($name in @('RUN_ID','HTTP_PROXY','HTTPS_PROXY','ALL_PROXY')) { Set-LocalEnv $name '' }
    Set-LocalEnv NO_PROXY '127.0.0.1,localhost'
    Set-LocalEnv BASE_URL $base
    Set-LocalEnv K6_ENVIRONMENT 'isolated-test'
    Set-LocalEnv K6_WEB_DASHBOARD 'true'
    Set-LocalEnv K6_WEB_DASHBOARD_HOST '127.0.0.1'
    Set-LocalEnv CITIZEN_INCIDENT_ID "${prefix}403"
    Set-LocalEnv OPERATOR_INCIDENT_ID "${prefix}401"
    Set-LocalEnv DUPLICATE_CANDIDATE_ID "${prefix}402"
    Set-LocalEnv CATEGORY_ID "${prefix}104"
    Assert-Compose -BeforeLogin
    Connect-Actor CITIZEN '001'
    Set-LocalEnv ACTOR CITIZEN
    Invoke-K6Case '01-smoke-ciudadano' 'smoke-read.js' -MustPass
    Set-LocalEnv READ_PROFILE load
    Invoke-K6Case '02-load' 'read.js'
    Invoke-K6Case '03-arrival' 'arrival-rate-read.js'
    Invoke-K6Case '04-mapas' 'map-read.js'

    Set-LocalEnv K6_ALLOW_HEAVY '1'
    foreach ($profile in @('stress','spike','soak')) {
        Set-LocalEnv READ_PROFILE $profile
        Invoke-K6Case $profile 'read.js'
    }
    Set-LocalEnv K6_ALLOW_HEAVY ''
    Connect-Actor OPERATOR '003'
    Set-LocalEnv ACTOR OPERATOR
    Invoke-K6Case '08-smoke-operador' 'smoke-read.js' -MustPass
    foreach ($flow in @('processing','duplicates')) {
        Set-LocalEnv STAFF_FLOW $flow
        Invoke-K6Case $flow 'staff-read.js'
    }
    Connect-Actor CITIZEN '001'
    Set-LocalEnv ACTOR CITIZEN
    Set-LocalEnv K6_ENABLE_WRITES '1'
    Set-LocalEnv RUN_ID ([guid]::NewGuid().ToString())
    Invoke-K6Case '11-smoke-escritura' 'smoke-incidents-write.js'
    if ($records.Code -contains 99) { throw 'Hay umbrales incumplidos. Consulta el índice de la sesión.' }
}
finally {
    foreach ($name in $previous.Keys) {
        if ($null -eq $previous[$name]) { Remove-Item "Env:$name" -ErrorAction SilentlyContinue }
        else { [Environment]::SetEnvironmentVariable($name, $previous[$name]) }
    }
    try { Write-ResultsIndex }
    catch { Write-Warning "No se pudo guardar el índice de la sesión: $_" }
}
