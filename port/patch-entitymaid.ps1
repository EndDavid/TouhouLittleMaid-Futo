# Patch EntityMaid.java:
#   1) sleep / bed / film / jizo method block (read from an UTF-8 snippet file,
#      because Windows PowerShell 5.1 parses BOM-less .ps1 as ANSI and the
#      snippet carries Chinese comments);
#   2) the jizo hook inside mobInteract;
#   3) the film drop inside die().
#
# Everything is ASCII here; the only non-ASCII text comes from the snippet file.

$ErrorActionPreference = 'Stop'

$repo    = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo'
$target  = Join-Path $repo 'src\main\java\com\github\tartaricacid\touhoulittlemaid\entity\passive\EntityMaid.java'
$snippet = Join-Path $repo 'port\patch\entitymaid-sleep-block.java.txt'

$utf8 = New-Object System.Text.UTF8Encoding($false)
$text = [System.IO.File]::ReadAllText($target, [System.Text.Encoding]::UTF8)
$before = $text.Length
$nl = if ($text.Contains("`r`n")) { "`r`n" } else { "`n" }

function Require-Once([string]$needle, [string]$label) {
    $count = ([regex]::Matches($script:text, [regex]::Escape($needle))).Count
    if ($count -ne 1) { throw ("anchor '" + $label + "' matched " + $count + " times, expected 1") }
}

# ---------------------------------------------------------------- 1. method block
$anchor1 = "    public BlockPos getBrainSearchPos() {" + $nl +
           "        return this.hasRestriction() ? this.getRestrictCenter() : this.blockPosition();" + $nl +
           "    }" + $nl
Require-Once $anchor1 'getBrainSearchPos'
$block = [System.IO.File]::ReadAllText($snippet, [System.Text.Encoding]::UTF8)
$block = $block -replace "`r`n", $nl
$i = $text.IndexOf($anchor1)
$text = $text.Insert($i + $anchor1.Length, $block)

# ---------------------------------------------------------------- 2. jizo hook
$anchor2 = "            InteractionResult xpBottleResult = this.takeXpWithBottle(player, stack);" + $nl +
           "            if (xpBottleResult != null) {" + $nl +
           "                return xpBottleResult;" + $nl +
           "            }" + $nl
Require-Once $anchor2 'xpBottleResult'
$jizo = $nl +
        "            // Substitute jizo: hand it to the maid to make her permanently invulnerable." + $nl +
        "            InteractionResult jizoResult = this.applySubstituteJizo(player, stack);" + $nl +
        "            if (jizoResult != null) {" + $nl +
        "                return jizoResult;" + $nl +
        "            }" + $nl
$i = $text.IndexOf($anchor2)
$text = $text.Insert($i + $anchor2.Length, $jizo)

# ---------------------------------------------------------------- 3. film drop on death
$anchor3 = "        super.die(source);" + $nl +
           "        if (!this.level.isClientSide) {" + $nl +
           "            this.level.broadcastEntityEvent(this, (byte) 60);" + $nl +
           "        }" + $nl
Require-Once $anchor3 'die/broadcast'
$film = "        // Film for the shrine revival (upstream puts it into an EntityTombstone)." + $nl +
        "        this.dropFilmOnDeath();" + $nl
$i = $text.IndexOf($anchor3)
$text = $text.Insert($i + "        super.die(source);".Length + $nl.Length, $film)

# ---------------------------------------------------------------- assertions
$required = @(
    'public void startSleeping(BlockPos pos)',
    'public void stopSleeping()',
    'public Direction getBedOrientation()',
    'private void dropFilmOnDeath()',
    'private InteractionResult applySubstituteJizo(Player player, ItemStack stack)',
    'this.applySubstituteJizo(player, stack)',
    'this.dropFilmOnDeath();'
)
foreach ($symbol in $required) {
    if (-not $text.Contains($symbol)) { throw ("missing symbol after patch: " + $symbol) }
}

$open = ([regex]::Matches($text, '\{')).Count
$close = ([regex]::Matches($text, '\}')).Count
if ($open -ne $close) { throw ("brace imbalance: " + $open + " vs " + $close) }
if ($text.Contains([char]0xFFFD)) { throw 'U+FFFD found after patch' }

[System.IO.File]::WriteAllText($target, $text, $utf8)
Write-Host ("patched, chars " + $before + " -> " + $text.Length + ", braces " + $open + "/" + $close)

