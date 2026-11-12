# Copy the tombstone / box sources that only touch vanilla 1.18.2 API.
#
# MaidInfo, MaidWorldData, TombstoneModel, EntityTombstoneRenderer,
# EntityBoxModel and EntityBoxRender use no Forge API at all, so they are
# byte-identical copies. EntityTombstone and EntityBox need two rewrites
# (NetworkHooks, getNtrItem) and are written by hand elsewhere.

$ErrorActionPreference = 'Stop'

$repo = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo'
$ref  = 'D:\codename_EndDavid\hello java\_ref\tlm-1182'
$pkg  = 'src\main\java\com\github\tartaricacid\touhoulittlemaid'

$files = @(
    'world\data\MaidInfo.java',
    'world\data\MaidWorldData.java',
    'client\model\TombstoneModel.java',
    'client\renderer\entity\EntityTombstoneRenderer.java',
    'client\model\EntityBoxModel.java',
    'client\renderer\entity\EntityBoxRender.java'
)

$utf8 = New-Object System.Text.UTF8Encoding($false)

foreach ($rel in $files) {
    $src = Join-Path $ref  (Join-Path $pkg $rel)
    $dst = Join-Path $repo (Join-Path $pkg $rel)
    if (-not (Test-Path $src)) { throw "missing upstream file: $rel" }
    New-Item -ItemType Directory -Force -Path (Split-Path $dst -Parent) | Out-Null
    $text = [System.IO.File]::ReadAllText($src, [System.Text.Encoding]::UTF8)
    [System.IO.File]::WriteAllText($dst, $text, $utf8)
    Write-Host ("copied " + $rel)
}

Write-Host 'ok'

