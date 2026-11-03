# Regenerate only the Category SVG fragment from its draw.io source.
# Existing layout, relationships and unrelated cells are retained.
$ErrorActionPreference = 'Stop'
$pairs = @(
    @('relacional.drawio', 'relacional-01.svg', 'n3'),
    @('clases-simplificado.drawio', 'clases-simplificado.svg', 'SQorblinmm3DdDP2t3pF-25')
)
foreach ($pair in $pairs) {
    [xml]$source = Get-Content -Encoding UTF8 -Raw (Join-Path $PSScriptRoot $pair[0])
    $cell = $source.SelectNodes('//mxCell') | Where-Object {
        $_.id -eq $pair[2] -and $_.value -match '>Category'
    } | Select-Object -First 1
    if (-not $cell) { throw 'Category source missing' }
    [xml]$html = $cell.value
    $labels = @($html.div.div | ForEach-Object { $_.InnerText })
    $svgPath = Join-Path $PSScriptRoot $pair[1]
    [xml]$svg = Get-Content -Encoding UTF8 -Raw $svgPath
    $switch = $svg.SelectNodes('//*[local-name()="switch"]') | Where-Object {
        $_.OuterXml -match '>Category(?:&nbsp;|\s|<)' -or $_.OuterXml -match 'data-category-export'
    } | Select-Object -First 1
    if (-not $switch) { throw 'Category export target missing' }
    $image = $switch.SelectSingleNode('*[local-name()="image"]')
    if ($image) { $x = [double]$image.x; $y = [double]$image.y }
    else { $x = [double]$switch.GetAttribute('data-x'); $y = [double]$switch.GetAttribute('data-y') }
    $switch.RemoveAll()
    $switch.SetAttribute('data-category-export', 'true')
    $switch.SetAttribute('data-x', "$x")
    $switch.SetAttribute('data-y', "$y")
    $group = $svg.CreateElement('g', 'http://www.w3.org/2000/svg')
    for ($i = 0; $i -lt $labels.Count; $i++) {
        $text = $svg.CreateElement('text', 'http://www.w3.org/2000/svg')
        $text.SetAttribute('x', "$($x + 7)")
        $text.SetAttribute('y', "$($y + 17 + $i * 15)")
        $text.SetAttribute('font-family', 'Arial')
        $text.SetAttribute('font-size', '10')
        $text.SetAttribute('fill', '#2e4050')
        if ($i -eq 0) { $text.SetAttribute('font-weight', 'bold') }
        $text.InnerText = $labels[$i]
        [void]$group.AppendChild($text)
    }
    [void]$switch.AppendChild($group)
    $settings = [System.Xml.XmlWriterSettings]::new()
    $settings.Indent = $false
    $settings.Encoding = [System.Text.UTF8Encoding]::new($false)
    $temporaryPath = "$svgPath.tmp"
    $writer = [System.Xml.XmlWriter]::Create($temporaryPath, $settings)
    try { $svg.Save($writer) } finally { $writer.Dispose() }
    Move-Item -LiteralPath $temporaryPath -Destination $svgPath -Force
}
