# Convert the six 1.20.1 item models that use Forge's "forge:separate_transforms"
# loader into plain vanilla 1.18.2 item models.
#
# Why: the loader is Forge-only. Vanilla ModelBakery ignores unknown "loader" fields
# and then finds no "parent"/"textures"/"elements" at the top level, so it bakes an
# EMPTY model. That is exactly "the model is fine in hand but the inventory icon is
# gone": this port's InHandModelUnbaked already supplies the 3D in-hand model, and
# the *flat* model is what became empty.
#
# The Forge file is a union of two models:
#   base               -> the 3D held model  (our InHandModelUnbaked builds it)
#   perspectives.gui   -> the 2D inventory icon  (this is what we keep)
# So the faithful 1.18.2 translation is just "parent: item/generated" + that layer0.
#
# The originals are saved under port/upstream-forge-item-models/ so the transform is
# auditable and reversible.

$ErrorActionPreference = 'Stop'

$models  = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo\src\main\resources\assets\touhou_little_maid\models\item'
$keepDir = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo\port\upstream-forge-item-models'
$log     = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo\port\_convert.txt'
$utf8    = New-Object System.Text.UTF8Encoding($false)

if (-not (Test-Path $keepDir)) { New-Item -ItemType Directory -Path $keepDir | Out-Null }

$names = @('camera', 'extinguisher', 'hakurei_gohei', 'sanae_gohei', 'maid_beacon', 'snack_cabinet')
$sb = New-Object System.Text.StringBuilder

foreach ($n in $names) {
    $path = Join-Path $models "$n.json"
    if (-not (Test-Path $path)) { throw "missing $n.json" }
    $raw = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)

    if (-not $raw.Contains('forge:separate_transforms')) {
        [void]$sb.AppendLine("$n : already vanilla, skipped")
        continue
    }

    # archive the upstream file once
    $keep = Join-Path $keepDir "$n.json"
    if (-not (Test-Path $keep)) { [System.IO.File]::WriteAllText($keep, $raw, $utf8) }

    $j = $raw | ConvertFrom-Json
    $gui = $j.perspectives.gui
    if ($null -eq $gui) { throw "$n has no perspectives.gui" }
    $layer0 = $gui.textures.layer0
    if (-not $layer0) { throw "$n has no gui layer0" }

    # one object per line, matching the style vanilla itself uses.
    # NOTE: PowerShell has no \" escape -- use single-quoted literals and join.
    $q = [string][char]0x22
    $nl = "`n"
    $lines = @(
        '{',
        ('  ' + $q + 'parent' + $q + ': ' + $q + 'item/generated' + $q + ','),
        ('  ' + $q + 'textures' + $q + ': {'),
        ('    ' + $q + 'layer0' + $q + ': ' + $q + $layer0 + $q),
        '  }',
        '}'
    )
    $out = ($lines -join $nl) + $nl
    [System.IO.File]::WriteAllText($path, $out, $utf8)

    $base = $j.base.parent
    [void]$sb.AppendLine("$n : forge -> vanilla   layer0=$layer0   (3D held model stays `"$base`")")
}

# verify: no forge loader left, every layer0 resolves to a real png
$assets = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo\src\main\resources\assets\touhou_little_maid'
$left = 0
$bad = 0
foreach ($f in Get-ChildItem $models -File -Filter *.json) {
    $t = [System.IO.File]::ReadAllText($f.FullName, [System.Text.Encoding]::UTF8)
    if ($t.Contains('"loader"')) { $left++; [void]$sb.AppendLine('  STILL HAS loader: ' + $f.Name) }
    foreach ($m in [regex]::Matches($t, '"touhou_little_maid:([a-z0-9_/]+)"')) {
        $p = $m.Groups[1].Value
        $cand = (Join-Path $assets ((Join-Path 'textures' ($p -replace '/', '\')))) + '.png'
        if (-not (Test-Path $cand)) {
            # only complain for layer0-ish icon paths
            if ($t.Contains('"layer0"')) { $bad++; [void]$sb.AppendLine("  MISSING TEX: $($f.Name) -> $p") }
        }
    }
}
[void]$sb.AppendLine('')
[void]$sb.AppendLine("forge loaders left: $left")
[void]$sb.AppendLine("missing icon textures: $bad")

if ($left -ne 0 -or $bad -ne 0) {
    [System.IO.File]::WriteAllText($log, $sb.ToString(), $utf8)
    throw "verification failed ($left loaders, $bad textures) - see $log"
}

[System.IO.File]::WriteAllText($log, $sb.ToString(), $utf8)
Write-Host 'converted'
