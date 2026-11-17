#Requires -Version 7.0
# Análisis sintáctico únicamente: Node no evalúa ni importa módulos k6.
$ErrorActionPreference = 'Stop'
if (!(Get-Command node -ErrorAction SilentlyContinue)) {
    throw 'Se necesita Node.js para analizar sintaxis, no para ejecutar los escenarios.'
}
$files = @(Get-ChildItem -LiteralPath $PSScriptRoot -Recurse -File |
    Where-Object { $_.Extension -in '.js', '.mjs' })
$previousEncoding = $OutputEncoding
try {
    $OutputEncoding = [System.Text.UTF8Encoding]::new($false)
    foreach ($file in $files) {
        Get-Content -LiteralPath $file.FullName -Raw -Encoding utf8 | node --input-type=module --check
        if ($LASTEXITCODE -ne 0) { throw "Sintaxis inválida: $($file.Name)" }
    }
} finally {
    $OutputEncoding = $previousEncoding
}
Write-Output "Sintaxis ESM comprobada en $($files.Count) archivos. No se han ejecutado scripts ni peticiones."
