$ErrorActionPreference = 'Stop'
$root = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo'
$rel  = 'src/main/resources/assets/touhou_little_maid/models/entity/mononobe_no_futo.json'
$newText = [System.IO.File]::ReadAllText("$root\$rel", [System.Text.Encoding]::UTF8)
$oldText = (git -C $root show "HEAD:$rel") -join "`n"

$new = $newText | ConvertFrom-Json
$old = $oldText | ConvertFrom-Json
$nb = $new.'geometry.model'.bones
$ob = $old.'geometry.model'.bones

function Fail($m) { throw "ABORT: $m" }

function CubeKeys($geom) {
    $keys = New-Object System.Collections.ArrayList
    foreach ($b in $geom.bones) {
        if (-not $b.cubes) { continue }
        foreach ($c in $b.cubes) {
            $o = $c.origin -join ','
            $s = $c.size -join ','
            $u = $c.uv -join ','
            $key = $o + '|' + $s + '|' + $u
            $null = $keys.Add($key)
        }
    }
    return $keys
}

if ($nb.Count -ne 43) { Fail "expected 43 bones, got $($nb.Count)" }
if ($ob.Count -ne 42) { Fail "expected 42 old bones, got $($ob.Count)" }

$body  = $nb | Where-Object { $_.name -eq 'body' }
$skirt = $nb | Where-Object { $_.name -eq 'sittingRotationSkirt' }
if ($body.cubes.Count -ne 10) { Fail "body cubes = $($body.cubes.Count)" }
if ($skirt.cubes.Count -ne 5)  { Fail "skirt cubes = $($skirt.cubes.Count)" }
if ($skirt.parent -ne 'body')  { Fail "skirt parent = $($skirt.parent)" }
if (($skirt.pivot -join ',') -ne '0,12,0') { Fail "skirt pivot = $($skirt.pivot -join ',')" }

foreach ($n in @('clothFront', 'clothFront2', 'clothBack', 'clothBack2')) {
    $b = $nb | Where-Object { $_.name -eq $n }
    if ($b.parent -ne 'sittingRotationSkirt') { Fail "$n parent = $($b.parent)" }
}

$oldCubes = CubeKeys $old.'geometry.model'
$newCubes = CubeKeys $new.'geometry.model'
if ($oldCubes.Count -ne $newCubes.Count) { Fail "cube count $($oldCubes.Count) -> $($newCubes.Count)" }
$diff = Compare-Object -ReferenceObject $oldCubes -DifferenceObject $newCubes
if ($diff) { Fail "cube set changed: $($diff.Count) entries" }

$touched = @('body', 'sittingRotationSkirt', 'clothFront', 'clothFront2', 'clothBack', 'clothBack2')
$oldMap = @{}
foreach ($b in $ob) { $oldMap[$b.name] = ($b | ConvertTo-Json -Depth 20 -Compress) }
$changed = New-Object System.Collections.ArrayList
foreach ($b in $nb) {
    if ($touched -contains $b.name) { continue }
    if (-not $oldMap.ContainsKey($b.name)) { $null = $changed.Add($b.name + ' (new)'); continue }
    if ($oldMap[$b.name] -ne ($b | ConvertTo-Json -Depth 20 -Compress)) { $null = $changed.Add($b.name) }
}
if ($changed.Count -gt 0) { Fail "unexpected bone changes: $($changed -join ', ')" }

'OK  bones 42 -> 43'
'OK  body cubes 15 -> 10, new bone carries 5 layers, pivot 0,12,0'
'OK  cube set identical: ' + $newCubes.Count + ' cubes'
'OK  only the intended six bones were touched'

