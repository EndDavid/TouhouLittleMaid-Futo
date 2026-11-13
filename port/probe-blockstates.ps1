# Whitespace-insensitive comparison of the blockstates/ and models/block/ JSON, so
# formatting-only differences do not mask the real ones.
$ErrorActionPreference = 'Stop'
$outPath = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo\port\_probe.txt'
$new = 'D:\codename_EndDavid\hello java\_ref\_1201assets'
$old = 'D:\codename_EndDavid\hello java\_ref\_1182assets'
$mine = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo\src\main\resources\assets\touhou_little_maid'
$sb = New-Object System.Text.StringBuilder

function Norm([string]$p) {
    if (-not (Test-Path $p)) { return $null }
    $t = [System.IO.File]::ReadAllText($p)
    return ($t -replace '\s', '')
}

[void]$sb.AppendLine('=== textures/block ===')
[void]$sb.AppendLine('our      : ' + ((Get-ChildItem "$mine\textures\block" -File | ForEach-Object { $_.Name }) -join ' '))
[void]$sb.AppendLine('1.18.2   : ' + ((Get-ChildItem "$old\textures\block" -File | ForEach-Object { $_.Name }) -join ' '))
[void]$sb.AppendLine('1.20.1   : ' + ((Get-ChildItem "$new\textures\block" -File | ForEach-Object { $_.Name }) -join ' '))

[void]$sb.AppendLine('')
[void]$sb.AppendLine('=== blockstates: normalized equality (1.20.1 vs ours) ===')
foreach ($f in Get-ChildItem "$new\blockstates" -File -Filter *.json | Sort-Object Name) {
    $a = Norm $f.FullName
    $b = Norm "$mine\blockstates\$($f.Name)"
    $r = 'DIFF'
    if ($null -eq $b) { $r = 'OURS-MISSING' }
    elseif ($a -eq $b) { $r = 'same' }
    [void]$sb.AppendLine(("{0,-24} {1}" -f $f.Name, $r))
}

[void]$sb.AppendLine('')
[void]$sb.AppendLine('=== models/block: normalized equality (1.20.1 vs ours) ===')
foreach ($f in Get-ChildItem "$new\models\block" -File -Filter *.json | Sort-Object Name) {
    $a = Norm $f.FullName
    $b = Norm "$mine\models\block\$($f.Name)"
    $r = 'DIFF'
    if ($null -eq $b) { $r = 'OURS-MISSING' }
    elseif ($a -eq $b) { $r = 'same' }
    [void]$sb.AppendLine(("{0,-28} {1}" -f $f.Name, $r))
}

[void]$sb.AppendLine('')
[void]$sb.AppendLine('=== blockstates garage_kit (both) ===')
[void]$sb.AppendLine('new:')
[void]$sb.AppendLine((Get-Content "$new\blockstates\garage_kit.json" -Raw))
[void]$sb.AppendLine('ours:')
[void]$sb.AppendLine((Get-Content "$mine\blockstates\garage_kit.json" -Raw))

[System.IO.File]::WriteAllText($outPath, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Host 'done'

