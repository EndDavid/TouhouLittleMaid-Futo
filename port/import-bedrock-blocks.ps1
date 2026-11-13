# Copy the GeckoLib bedrock block models + textures that the port now uses.
#
# Only altar and shrine are wired up so far (they are the two blocks whose
# renderer exists in this port). The other bedrock block models are copied as
# well so the blocks can be switched over one by one without another extraction
# round -- unused files in models/bedrock/block are inert.

$ErrorActionPreference = 'Stop'

$new  = 'D:\codename_EndDavid\hello java\_ref\_1201assets'
$mine = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo\src\main\resources\assets\touhou_little_maid'
$log  = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo\port\_import2.txt'
$sb = New-Object System.Text.StringBuilder

foreach ($rel in @('models\bedrock\block', 'textures\bedrock\block')) {
    $src = Join-Path $new $rel
    $dst = Join-Path $mine $rel
    if (-not (Test-Path $dst)) { New-Item -ItemType Directory -Path $dst -Force | Out-Null }
    $n = 0
    foreach ($f in Get-ChildItem $src -File) {
        Copy-Item $f.FullName (Join-Path $dst $f.Name) -Force
        $n++
    }
    [void]$sb.AppendLine("$rel : copied top-level $n files")
}

# maid_bed subfolder (one model + one texture per colour)
foreach ($rel in @('models\bedrock\block\maid_bed', 'textures\bedrock\block\maid_bed')) {
    $src = Join-Path $new $rel
    $dst = Join-Path $mine $rel
    if (-not (Test-Path $src)) { continue }
    if (-not (Test-Path $dst)) { New-Item -ItemType Directory -Path $dst -Force | Out-Null }
    $n = 0
    foreach ($f in Get-ChildItem $src -File) {
        Copy-Item $f.FullName (Join-Path $dst $f.Name) -Force
        $n++
    }
    [void]$sb.AppendLine("$rel : copied $n files")
}

# verify the two models we actually use parse-relevant: format + first bone name
foreach ($n in @('altar', 'shrine')) {
    $p = Join-Path $mine "models\bedrock\block\$n.json"
    if (-not (Test-Path $p)) { throw "missing $n.json" }
    $t = [System.IO.File]::ReadAllText($p)
    $fv = [regex]::Match($t, '"format_version"\s*:\s*"([^"]+)"').Groups[1].Value
    $tw = [regex]::Match($t, '"texture_width"\s*:\s*(\d+)').Groups[1].Value
    $th = [regex]::Match($t, '"texture_height"\s*:\s*(\d+)').Groups[1].Value
    $bones = ([regex]::Matches($t, '"name"\s*:')).Count
    [void]$sb.AppendLine("$n.json  format=$fv  texture=${tw}x$th  name-count=$bones  bytes=$($t.Length)")
}

[System.IO.File]::WriteAllText($log, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Host 'bedrock assets copied'

