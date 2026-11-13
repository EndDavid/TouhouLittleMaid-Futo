# Adopt the 1.20.1 (TLM 1.5.3) item / block assets over the 1.18.2 ones.
#
# Scope of this pass -- only what 1.18.2 can render as-is:
#
#   * every models/item/*.json          (vanilla item models, portable 1:1)
#   * every textures/item/*.png + .mcmeta  (includes the new animated ones)
#   * models/block/maid_beacon_{down,up}.json   (real vanilla JSON in both)
#   * models/block/scarecrow_{lower,upper}.json (real vanilla JSON in both)
#   * textures/block/{maid_beacon,scarecrow_lower,scarecrow_upper}.png
#
# Deliberately NOT copied:
#   * models/block/maid_bed_{head,foot}.json -- 1.20.1 turned the bed into a
#     particle-only stub drawn by a GeckoLib bedrock model; copying it now would
#     make the bed invisible until a GeoBlockRenderer exists.
#   * altar / shrine / statue / bookshelf / ... -- already particle-only stubs on
#     both sides, their geometry is a block entity renderer.
#   * sounds.json / lang/*.json -- the 1.20.1 versions would drop our own keys.
#
# Files are copied byte for byte (no re-encoding) so the JSON formatting stays as
# upstream shipped it.

$ErrorActionPreference = 'Stop'

$new  = 'D:\codename_EndDavid\hello java\_ref\_1201assets'
$mine = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo\src\main\resources\assets\touhou_little_maid'
$log  = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo\port\_import.txt'

$sb = New-Object System.Text.StringBuilder
function Log([string]$m) { [void]$sb.AppendLine($m) }

# --- 1. whole models/item + textures/item ---------------------------------
foreach ($rel in @('models\item', 'textures\item')) {
    $src = Join-Path $new $rel
    $dst = Join-Path $mine $rel
    $n = 0
    foreach ($f in Get-ChildItem $src -File) {
        Copy-Item $f.FullName (Join-Path $dst $f.Name) -Force
        $n++
    }
    Log ("$rel : copied $n files")
}

# --- 2. the two blocks that are real vanilla JSON in both versions --------
$blockFiles = @(
    'models\block\maid_beacon_down.json',
    'models\block\maid_beacon_up.json',
    'models\block\scarecrow_lower.json',
    'models\block\scarecrow_upper.json',
    'textures\block\maid_beacon.png',
    'textures\block\scarecrow_lower.png',
    'textures\block\scarecrow_upper.png'
)
foreach ($rel in $blockFiles) {
    $src = Join-Path $new $rel
    if (-not (Test-Path $src)) { throw "missing in 1.20.1: $rel" }
    $dst = Join-Path $mine $rel
    Copy-Item $src $dst -Force
    Log ("copied $rel")
}

# --- 3. drop the textures 1.20.1 retired ----------------------------------
foreach ($rel in @('textures\item\camera_2d.png', 'textures\block\scarecrow.png')) {
    $dst = Join-Path $mine $rel
    if (Test-Path $dst) {
        Remove-Item $dst -Force
        Log ("removed $rel")
    }
}

# --- 4. verify every item model parent resolves ---------------------------
# A parent is either "builtin/entity" (vanilla), "item/generated" (implicit
# minecraft namespace) or "<ns>:<path>" which lives at models/<path>.json.
$unresolved = New-Object System.Collections.Generic.List[string]
foreach ($f in Get-ChildItem (Join-Path $mine 'models\item') -File -Filter *.json) {
    $t = [System.IO.File]::ReadAllText($f.FullName)
    foreach ($m in [regex]::Matches($t, '"parent"\s*:\s*"([^"]+)"')) {
        $p = $m.Groups[1].Value
        if (-not $p.Contains(':')) { continue }        # implicit minecraft:
        if ($p.StartsWith('builtin/')) { continue }    # vanilla special
        $ns, $path = $p.Split(':', 2)
        if ($ns -eq 'minecraft') { continue }
        $cand = (Join-Path $mine ((Join-Path 'models' ($path -replace '/', '\')))) + '.json'
        if (-not (Test-Path $cand)) { $unresolved.Add("$($f.Name) -> parent $p") }
    }
    # every "#x" referenced in faces must be defined in this file's textures map
    foreach ($m in [regex]::Matches($t, '"texture"\s*:\s*"#([^"]+)"')) {
        $key = $m.Groups[1].Value
        if ($t -notmatch ('"' + [regex]::Escape($key) + '"\s*:\s*"')) {
            $unresolved.Add("$($f.Name) -> undefined texture #$key")
        }
    }
}
Log ''
Log ("item model problems: " + $unresolved.Count)
foreach ($u in $unresolved) { Log ("  " + $u) }

# --- 5. verify every referenced texture file exists -----------------------
# Only the values of the "textures" map count; parents are not textures.
$missingTex = New-Object System.Collections.Generic.List[string]
foreach ($f in Get-ChildItem (Join-Path $mine 'models\item') -File -Filter *.json) {
    $t = [System.IO.File]::ReadAllText($f.FullName)
    $mm = [regex]::Match($t, '"textures"\s*:\s*\{(.*?)\}', 'Singleline')
    if (-not $mm.Success) { continue }
    foreach ($v in [regex]::Matches($mm.Groups[1].Value, '"touhou_little_maid:([a-z0-9_/]+)"')) {
        $p = $v.Groups[1].Value
        $cand = (Join-Path $mine ((Join-Path 'textures' ($p -replace '/', '\')))) + '.png'
        if (-not (Test-Path $cand)) { $missingTex.Add("$($f.Name) -> $p") }
    }
}
Log ''
Log ("missing item textures: " + $missingTex.Count)
foreach ($u in $missingTex) { Log ("  " + $u) }

# --- 6. block models: same checks ----------------------------------------
$missingBlockTex = New-Object System.Collections.Generic.List[string]
foreach ($n in @('maid_beacon_down', 'maid_beacon_up', 'scarecrow_lower', 'scarecrow_upper')) {
    $p = Join-Path $mine "models\block\$n.json"
    $t = [System.IO.File]::ReadAllText($p)
    foreach ($m in [regex]::Matches($t, '"touhou_little_maid:([a-z0-9_/]+)"')) {
        $pp = $m.Groups[1].Value
        $cand = (Join-Path $mine ((Join-Path 'textures' ($pp -replace '/', '\')))) + '.png'
        if (-not (Test-Path $cand)) { $missingBlockTex.Add("$n -> $pp") }
    }
}
Log ''
Log ("missing block textures: " + $missingBlockTex.Count)
foreach ($u in $missingBlockTex) { Log ("  " + $u) }

[System.IO.File]::WriteAllText($log, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Host 'imported'
