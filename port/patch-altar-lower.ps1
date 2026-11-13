# Lower the altar model by 3 blocks.
#
# WHY 3, and why it is not a magic number:
#
#   MultiBlockAltar#getCenterPos returns NORTH(-3,-3,-7) SOUTH(-4,-3,0)
#   EAST(0,-3,-3) WEST(-7,-3,-4) -- note the Y component is -3 in every case.
#   MultiBlockAltar#build sets the *rendering* block entity to
#       posStart.subtract(getCenterPos(direction))
#   i.e. posStart + (…, 3, …): the anchor is THREE BLOCKS ABOVE posStart, while
#   posStart is the bottom layer of the structure (that is also why the offering
#   logs are at blockInfo.pos().getY() == 2).
#
#   The bedrock model's origin is the structure's bottom, so drawing it at the
#   anchor puts the whole altar 3 blocks too high. Subtracting the anchor's own
#   height (getCenterPos's Y, negated) fixes it.
#
# Only the Y component changes: the report was "exactly 3 too high, horizontally
# fine", and the X/Z of the direction table are what already place it correctly.

$ErrorActionPreference = 'Stop'
$repo = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo'
$p   = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo\src\main\java\com\github\tartaricacid\touhoulittlemaid\client\renderer\tileentity\TileEntityAltarRenderer.java'
$log = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo\port\_lower.log'
$utf8 = New-Object System.Text.UTF8Encoding($false)

$t = [System.IO.File]::ReadAllText($p, [System.Text.Encoding]::UTF8)
$before = $t.Length

if ($t.Contains(', -3, ')) { throw 'already applied' }

# the four entries currently read ", 0, " in the Y slot
$n = ([regex]::Matches($t, ', 0, ')).Count
if ($n -ne 4) { throw "expected 4 direction entries with ', 0, ', found $n" }
$t = $t.Replace(', 0, ', ', -3, ')

# place the explanation above the switch (Chinese text comes from the UTF-8 data file:
# a BOM-less .ps1 is parsed as ANSI, so CJK literals here would be a syntax error)
$notePath = Join-Path $repo 'port\_lowernote.txt'
$noteLines = [System.IO.File]::ReadAllLines($notePath, [System.Text.Encoding]::UTF8)
$note = ($noteLines -join "`r`n")

$anchor = '    private void setTranslateAndPose(TileEntityAltar te, PoseStack poseStack) {'
if (-not $t.Contains($anchor)) { throw 'setTranslateAndPose not found' }
$t = $t.Replace($anchor, $note + "`r`n" + $anchor)

if ($t.Contains([char]0xFFFD)) { throw 'replacement char' }
$o = ([regex]::Matches($t, '\{')).Count
$c = ([regex]::Matches($t, '\}')).Count
if ($o -ne $c) { throw "brace mismatch $o/$c" }
if (([regex]::Matches($t, ', -3, ')).Count -ne 4) { throw 'Y offsets not applied' }

[System.IO.File]::WriteAllText($p, $t, $utf8)
if ([System.IO.File]::ReadAllText($p, [System.Text.Encoding]::UTF8) -ne $t) { throw 'read-back mismatch' }

[System.IO.File]::WriteAllText($log, "len $before -> $($t.Length)`nbraces $o/$c`nok", $utf8)
Write-Host 'lowered'
