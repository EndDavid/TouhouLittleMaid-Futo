# Add a warm-up of the two bedrock block models inside the resource-reload
# listener, so a malformed model is reported at load time instead of the first
# time the block is rendered (which would just look like "the altar is invisible").
#
# ASCII only; Chinese text is appended verbatim from port\_warm_patch.txt (UTF-8).

$ErrorActionPreference = 'Stop'

$repo  = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo'
$java  = Join-Path $repo 'src\main\java\com\github\tartaricacid\touhoulittlemaid\client\TouhouLittleMaidFutoClient.java'
$data  = Join-Path $repo 'port\_warm_patch.txt'
$log   = Join-Path $repo 'port\_warmpatch.txt'

$utf8 = New-Object System.Text.UnicodeEncoding($false, $false)
$utf8 = New-Object System.Text.UTF8Encoding($false)

$lines = [System.IO.File]::ReadAllLines($data, [System.Text.Encoding]::UTF8)
$block = ($lines -join "`r`n")

$src = [System.IO.File]::ReadAllText($java, [System.Text.Encoding]::UTF8)
$before = $src.Length

$anchor = 'com.github.tartaricacid.touhoulittlemaid.client.resource.BedrockBlockModels.clear();'
$n = ([regex]::Matches($src, [regex]::Escape($anchor))).Count
if ($n -ne 1) { throw "clear() anchor count = $n" }
if ($src.Contains('BedrockBlockModels.get("altar")')) { throw 'warm-up already present' }

$src = $src.Replace($anchor, $anchor + $block)

if ($src.Contains([char]0xFFFD)) { throw 'replacement char in output' }
$open = ([regex]::Matches($src, '\{')).Count
$close = ([regex]::Matches($src, '\}')).Count
if ($open -ne $close) { throw "brace mismatch $open/$close" }
if (([regex]::Matches($src, 'BedrockBlockModels\.get')).Count -ne 2) { throw 'expected 2 warm-up calls' }

[System.IO.File]::WriteAllText($java, $src, $utf8)
if ([System.IO.File]::ReadAllText($java, [System.Text.Encoding]::UTF8) -ne $src) { throw 'read-back mismatch' }

[System.IO.File]::WriteAllText($log, ("len " + $before + " -> " + $src.Length + "`nbraces " + $open + "/" + $close + "`nok"), $utf8)
Write-Host 'warm-up added'

