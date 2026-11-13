# Copy the remaining 1.20.1 assets the bedrock block models need, and report on
# the item textures that 1.20.1 references but does not ship.
$ErrorActionPreference = 'Stop'
$new  = 'D:\codename_EndDavid\hello java\_ref\_1201assets'
$mine = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo\src\main\resources\assets\touhou_little_maid'
$log  = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo\port\_import3.txt'
$sb = New-Object System.Text.StringBuilder

# 1. the altar / shrine / statue item icons point at bedrock textures in 1.20.1
$icons = @(
    'textures\item\maid_beacon_in_hand.png',
    'textures\item\camera_in_hand.png',
    'textures\item\snack_cabinet_in_hand.png'
)
foreach ($rel in $icons) {
    $src = Join-Path $new $rel
    $present = Test-Path $src
    [void]$sb.AppendLine(("{0,-42} in1.20.1={1}" -f $rel, $present))
}

# 2. list every item model whose texture lives under textures/bedrock
$bedrockRefs = New-Object System.Collections.Generic.List[string]
foreach ($f in Get-ChildItem (Join-Path $mine 'models\item') -File -Filter *.json) {
    $t = [System.IO.File]::ReadAllText($f.FullName)
    $mm = [regex]::Match($t, '"textures"\s*:\s*\{(.*?)\}', 'Singleline')
    if (-not $mm.Success) { continue }
    foreach ($v in [regex]::Matches($mm.Groups[1].Value, '"touhou_little_maid:(bedrock/[a-z0-9_/]+)"')) {
        $bedrockRefs.Add("$($f.Name) -> $($v.Groups[1].Value)")
    }
}
[void]$sb.AppendLine('')
[void]$sb.AppendLine("item models pointing at a bedrock texture: " + $bedrockRefs.Count)
foreach ($r in $bedrockRefs) { [void]$sb.AppendLine("  $r") }

# 3. does that texture exist under textures/bedrock in our tree?
[void]$sb.AppendLine('')
foreach ($r in $bedrockRefs) {
    $rel = ($r -split ' -> ')[1]
    $cand = (Join-Path $mine ((Join-Path 'textures' ($rel -replace '/', '\')))) + '.png'
    [void]$sb.AppendLine(("{0,-46} exists={1}" -f $rel, (Test-Path $cand)))
}

[System.IO.File]::WriteAllText($log, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Host 'reported'

