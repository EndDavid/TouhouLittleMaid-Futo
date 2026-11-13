# Patch TouhouLittleMaidFutoClient.java:
#   1. drop the now-unused AltarModel / ShrineModel layer registrations
#   2. register maid_beacon + scarecrow as cutout render layers
#   3. clear the bedrock block model cache on resource reload
#
# ASCII only; the Chinese comment text comes from port\_init_patch.txt (UTF-8).

$ErrorActionPreference = 'Stop'

$repo  = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo'
$java  = Join-Path $repo 'src\main\java\com\github\tartaricacid\touhoulittlemaid\client\TouhouLittleMaidFutoClient.java'
$data  = Join-Path $repo 'port\_init_patch.txt'
$log   = Join-Path $repo 'port\_initpatch.txt'

$utf8 = New-Object System.Text.UTF8Encoding($false)

# ---- read the data file --------------------------------------------------
$sections = @{}
$current = $null
foreach ($l in [System.IO.File]::ReadAllLines($data, [System.Text.Encoding]::UTF8)) {
    if ($l -match '^@@([A-Z]+)$') {
        $current = $Matches[1]
        $sections[$current] = [System.Collections.Generic.List[string]]::new()
        continue
    }
    if ($null -eq $current) { throw 'content before first marker' }
    $sections[$current].Add($l)
}
foreach ($k in @('REMOVE', 'INSERT', 'CLEAR')) {
    if (-not $sections.ContainsKey($k)) { throw "missing section $k" }
}

function Join-Lines($list) { return ($list -join "`r`n") }

$removeText = Join-Lines $sections['REMOVE']
$insertText = Join-Lines $sections['INSERT']
$clearText  = Join-Lines $sections['CLEAR']

# ---- load the java file --------------------------------------------------
$src = [System.IO.File]::ReadAllText($java, [System.Text.Encoding]::UTF8)
$before = $src.Length
$sb = New-Object System.Text.StringBuilder

# 1. remove the two layer registrations
$count = ([regex]::Matches($src, 'AltarModel|ShrineModel')).Count
if ($count -ne 4) { throw "expected 4 AltarModel/ShrineModel references, found $count" }
if (-not $src.Contains($removeText)) { throw 'AltarModel/ShrineModel registration block not found verbatim' }
$src = $src.Replace($removeText, '')
if ($src.Contains('AltarModel')) { throw 'AltarModel still referenced' }
if ($src.Contains('ShrineModel')) { throw 'ShrineModel still referenced' }

# 2. insert the cutout render layers after the shrine TE renderer registration
$anchor = 'com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity.TileEntityShrineRenderer::new);'
if (([regex]::Matches($src, [regex]::Escape($anchor))).Count -ne 1) { throw 'shrine renderer anchor not unique' }
$src = $src.Replace($anchor, $anchor + $insertText)
if (([regex]::Matches($src, 'BlockRenderLayerMap')).Count -ne 2) { throw 'cutout registration not inserted as expected' }

# 3. clear the bedrock block model cache on reload
$reloadAnchor = 'GeckoModelLoader.reload();'
if (([regex]::Matches($src, [regex]::Escape($reloadAnchor))).Count -ne 1) { throw 'reload anchor not unique' }
$src = $src.Replace($reloadAnchor, $reloadAnchor + $clearText)
if (([regex]::Matches($src, 'BedrockBlockModels\.clear')).Count -ne 1) { throw 'cache clear not inserted' }

# ---- write + verify ------------------------------------------------------
if ($src.Contains([char]0xFFFD)) { throw 'replacement char in output' }
if (([regex]::Matches($src, 'touhoull')).Count -ne 0) { throw 'mangled package name' }
# brace balance, ignoring string/char literals is overkill here; a plain count is enough
$open = ([regex]::Matches($src, '\{')).Count
$close = ([regex]::Matches($src, '\}')).Count
if ($open -ne $close) { throw "brace mismatch $open / $close" }

[System.IO.File]::WriteAllText($java, $src, $utf8)

$sbCheck = [System.IO.File]::ReadAllText($java, [System.Text.Encoding]::UTF8)
if ($sbCheck -ne $src) { throw 'read-back mismatch' }

[System.IO.File]::WriteAllText($log,
    ("len " + $before + " -> " + $src.Length + "`nbraces " + $open + "/" + $close + "`nok"),
    $utf8)
Write-Host 'patched'
