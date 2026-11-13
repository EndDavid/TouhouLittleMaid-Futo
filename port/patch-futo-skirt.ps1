$ErrorActionPreference = 'Stop'

# Add a sittingRotationSkirt bone to mononobe_no_futo.json, move the skirt layers
# into it, and reparent the four cloth panels to it.
#
# Why the bone is named "sittingRotationSkirt": GeckoModelLoader#normalizeBones
# renames it to "Skirt" for legacy models, and maid_legacy.animation.json's
# "sit" / "chair" states already rotate "Skirt" by -32.5 degrees.

$path = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo\src\main\resources\assets\touhou_little_maid\models\entity\mononobe_no_futo.json'
$lines = [System.IO.File]::ReadAllLines($path, [System.Text.Encoding]::UTF8)

function Fail($msg) { throw "ABORT: $msg" }

# ---------------------------------------------------------------- locate skirt layers
$skirtOrigins = @(
    '{"origin": [-3, 10, -3], "size": [6, 2, 6], "uv": [0, 62]}',
    '{"origin": [-3.5, 8.75, -3.5], "size": [7, 2, 7], "uv": [0, 70]}',
    '{"origin": [-4, 7.5, -4], "size": [8, 2, 8], "uv": [0, 79]}',
    '{"origin": [-4.5, 6.25, -4.5], "size": [9, 2, 9], "uv": [0, 89]}',
    '{"origin": [-5, 6, -5], "size": [10, 1, 10], "uv": [0, 100]}'
)

$skirtIdx = @()
for ($i = 0; $i -lt $lines.Length; $i++) {
    $t = $lines[$i].Trim().TrimEnd(',')
    if ($skirtOrigins -contains $t) { $skirtIdx += $i }
}
if ($skirtIdx.Count -ne 5) { Fail "expected 5 skirt cube lines, found $($skirtIdx.Count)" }
for ($k = 0; $k -lt 5; $k++) {
    if ($skirtIdx[$k] -ne $skirtIdx[0] + $k) { Fail 'skirt cube lines are not contiguous' }
}

# ---------------------------------------------------------------- locate the body block
$bodyName = -1
for ($i = 0; $i -lt $lines.Length; $i++) {
    if ($lines[$i] -eq '        "name": "body",') { $bodyName = $i; break }
}
if ($bodyName -lt 0) { Fail 'body bone not found' }
if ($lines[$bodyName + 1] -ne '        "pivot": [0, 10.5, 0],') { Fail 'unexpected body pivot line' }
if ($lines[$bodyName + 2] -ne '        "cubes": [') { Fail 'unexpected body cubes header' }

$bodyClose = -1
for ($i = $skirtIdx[4] + 1; $i -lt $lines.Length; $i++) {
    if ($lines[$i] -eq '        ]') { $bodyClose = $i; break }
}
if ($bodyClose -lt 0) { Fail 'body cubes close bracket not found' }
if ($lines[$bodyClose + 1] -ne '      },') { Fail 'unexpected body bone terminator' }
if ($bodyClose -ne $skirtIdx[4] + 1) { Fail 'unexpected content after the last skirt cube' }

$lastCubeBeforeSkirt = $skirtIdx[0] - 1
if (-not $lines[$lastCubeBeforeSkirt].Trim().EndsWith('"uv": [40, 30]},')) {
    Fail 'unexpected line before the skirt layers'
}

# ---------------------------------------------------------------- rebuild the line list
$new = New-Object System.Collections.Generic.List[string]

# body: everything up to the first skirt layer, with the trailing comma dropped
for ($i = 0; $i -le $lastCubeBeforeSkirt; $i++) { $new.Add($lines[$i]) }
$new[$new.Count - 1] = $new[$new.Count - 1].Replace('"uv": [40, 30]},', '"uv": [40, 30]}')
$new.Add('        ]')
$new.Add('      },')
$bodyEnd = $new.Count - 1

# the new helper bone, carrying the five skirt layers verbatim
$generated = New-Object System.Collections.Generic.List[string]
$generated.Add('      {')
$generated.Add('        "name": "sittingRotationSkirt",')
$generated.Add('        "parent": "body",')
$generated.Add('        "pivot": [0, 12, 0],')
$generated.Add('        "cubes": [')
for ($k = 0; $k -lt 5; $k++) {
    $cube = $lines[$skirtIdx[$k]]
    if ($k -lt 4) { $generated.Add($cube) } else { $generated.Add($cube.TrimEnd(',')) }
}
$generated.Add('        ]')
$generated.Add('      },')

$new.InsertRange($bodyEnd + 1, $generated)

# everything after the body block, starting at clothFront
for ($i = $bodyClose + 2; $i -lt $lines.Length; $i++) { $new.Add($lines[$i]) }

# ---------------------------------------------------------------- reparent the cloth panels
$cloth = @('clothFront', 'clothFront2', 'clothBack', 'clothBack2')
$patched = 0
for ($i = 0; $i -lt $new.Count; $i++) {
    foreach ($name in $cloth) {
        if ($new[$i] -eq "        `"name`": `"$name`",") {
            if ($new[$i + 1] -ne '        "parent": "body",') { Fail "unexpected parent for $name" }
            $new[$i + 1] = '        "parent": "sittingRotationSkirt",'
            $patched++
        }
    }
}
if ($patched -ne 4) { Fail "expected to reparent 4 cloth bones, patched $patched" }

# ---------------------------------------------------------------- sanity checks on the text
$joined = ($new -join "`n")
$open = ([regex]::Matches($joined, '\{')).Count
$close = ([regex]::Matches($joined, '\}')).Count
if ($open -ne $close) { Fail "brace mismatch: { = $open, } = $close" }

[System.IO.File]::WriteAllText($path, $joined, (New-Object System.Text.UTF8Encoding($false)))
"written: $($lines.Length) -> $($new.Count) lines"
