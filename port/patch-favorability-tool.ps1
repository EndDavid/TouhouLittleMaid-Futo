# Wire up the favorability tool: three item registrations, FavorabilityManager#max(),
# the EntityMaid interaction handler and its call site.
#
# ASCII-only on purpose: Windows PowerShell 5.1 parses a BOM-less .ps1 as ANSI, so a
# non-ASCII literal here would be mangled (and char + char is integer addition in
# PowerShell, so building Chinese anchors from code points is a trap). Every Chinese
# string therefore lives in the UTF-8 snippet files next to this script, and every
# anchor below is pure ASCII and checked to be unique.
#
# EntityMaid.java is 3521 lines and insert_edit_into_file has silently deleted whole
# methods from it twice, so all edits are exact-string splices with assertions.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

function Read-Utf8([string]$path) {
    $t = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
    if ($t.Length -gt 0 -and [int]$t[0] -eq 0xFEFF) { $t = $t.Substring(1) }
    return $t
}

function Load-Snippet([string]$name) {
    $t = Read-Utf8 (Join-Path $PSScriptRoot $name)
    $t = $t -replace "`r`n", "`n"
    # TrimEnd with an explicit char set: the snippet files end with CRLF CRLF, and a
    # "while endsWith" loop left one behind, which is what produced the uneven blank
    # lines on the first run.
    $t = $t.TrimEnd([char[]]@(13, 10))
    if ($t.Contains([string][char]0xFFFD)) { throw "snippet $name contains U+FFFD" }
    return $t
}

function Get-Text([string]$path) {
    $raw = Read-Utf8 $path
    $crlf = ([regex]::Matches($raw, "`r`n")).Count
    $lf = ([regex]::Matches($raw, "`n")).Count
    if ($crlf -ne $lf) { throw "mixed EOL in $path : crlf=$crlf lf=$lf" }
    return ($raw -replace "`r`n", "`n")
}

function Save-Java([string]$path, [string]$text) {
    if ($text.Contains([string][char]0xFFFD)) { throw "result contains U+FFFD: $path" }
    [System.IO.File]::WriteAllText($path, $text, (New-Object System.Text.UTF8Encoding($false)))
}

function Count-Of([string]$text, [string]$needle) {
    return ([regex]::Matches($text, [regex]::Escape($needle))).Count
}

# splice a snippet in at $at, with exactly one blank line on each side.
# -Before means $at is the start of a line (so the preceding text already ends in \n);
# otherwise $at is the index just past a token, and the following text starts with \n.
function Splice([string]$path, [int]$at, [string]$snippetName, [string]$label, [switch]$Before) {
    $text = Get-Text $path
    $linesBefore = $text.Split("`n").Count
    $snip = Load-Snippet $snippetName
    if ($Before) {
        # sub(0,at) already ends with \n because $at is a line start
        $new = $text.Substring(0, $at) + "`n" + $snip + "`n`n" + $text.Substring($at)
    } else {
        # sub(at) starts with \n because $at is just past the token
        $new = $text.Substring(0, $at) + "`n`n" + $snip + "`n" + $text.Substring($at)
    }
    # Do NOT "clean up" the result with a global 3-newline collapse: that also removes
    # blank lines that legitimately exist elsewhere in the file (it cost 14 unrelated
    # lines in EntityMaid.java the first time). With the TrimEnd above the padding
    # arithmetic is already exact.
    Save-Java $path ($new -replace "`n", "`r`n")

    Write-Output ("{0,-34} {1} -> {2} lines" -f $label, $linesBefore, $new.Split("`n").Count)
}

$initItems = Join-Path $root 'src\main\java\com\github\tartaricacid\touhoulittlemaid\init\InitItems.java'
$favMgr = Join-Path $root 'src\main\java\com\github\tartaricacid\touhoulittlemaid\entity\favorability\FavorabilityManager.java'
$maid = Join-Path $root 'src\main\java\com\github\tartaricacid\touhoulittlemaid\entity\passive\EntityMaid.java'

# ---- 1. InitItems: insert after the whole CHAIR_SHOW registration ----------------
$anchor = "    public static final Item CHAIR_SHOW = Registry.register(Registry.ITEM,`n" +
          "            TouhouLittleMaid.rl(`"chair_show`"),`n" +
          "            new com.github.tartaricacid.touhoulittlemaid.item.ItemChairShow());"
$t = Get-Text $initItems
if ((Count-Of $t $anchor) -ne 1) { throw "CHAIR_SHOW anchor not unique" }
Splice $initItems ($t.IndexOf($anchor) + $anchor.Length) '_fav_inititems.txt' 'InitItems registrations'

# ---- 2. FavorabilityManager#max(): insert before reduce(int) --------------------
$t = Get-Text $favMgr
$anchor = '    public void reduce(int reducePoint) {'
if ((Count-Of $t $anchor) -ne 1) { throw "reduce() anchor not unique" }
Splice $favMgr $t.IndexOf($anchor) '_fav_manager.txt' 'FavorabilityManager#max' -Before

# ---- 3. EntityMaid handler: insert before the javadoc of applyPotionToMaid ------
# The marker inside that javadoc is ASCII; backtrack to the "    /**" opening it so the
# existing javadoc keeps belonging to its own method.
$t = Get-Text $maid
$marker = 'ApplyPotionEffectEvent'
if ((Count-Of $t $marker) -ne 1) { throw "javadoc marker not unique" }
$k = $t.IndexOf($marker)
$docStart = $t.LastIndexOf("    /**", $k)
if ($docStart -lt 0) { throw "could not find the opening /** of the applyPotionToMaid javadoc" }
Splice $maid $docStart '_fav_method.txt' 'EntityMaid#useFavorabilityTool' -Before

# ---- 4. EntityMaid call site: right after the feedGoldenApple block -------------
$t = Get-Text $maid
$anchor = "            InteractionResult appleResult = this.feedGoldenApple(player, stack);`n" +
          "            if (appleResult != null) {`n" +
          "                return appleResult;`n" +
          "            }"
if ((Count-Of $t $anchor) -ne 1) { throw "feedGoldenApple anchor not unique" }
Splice $maid ($t.IndexOf($anchor) + $anchor.Length) '_fav_call.txt' 'EntityMaid mobInteract call'

Write-Output 'patch script done'
