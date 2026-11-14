# Repairs UTF-8 text that was decoded as GBK and then written back as UTF-8.
# Pure ASCII on purpose: Windows PowerShell 5.1 parses a BOM-less .ps1 as ANSI,
# and that exact mistake is what produced the corruption this script undoes.
#
# Background: an earlier port step emitted .java sources from a .ps1 that
# contained Chinese string literals. The literals were already mojibake before
# they reached the file. The damage is comment-only, but it makes those files
# unreadable.
#
# Recovery path 1 (exact, no guessing):
#   mojibake text  --GBK encode-->  bytes  --UTF-8 decode-->  original text
#
# Recovery path 2 (bounded search): a few lines additionally lost one or two
# bytes, which the GBK decoder rendered as '?'. Each '?' is retried over the
# UTF-8 continuation range (0x80..0xBF). The repair is only accepted when
# EXACTLY ONE candidate validates -- otherwise it is ambiguous (0x80 and 0x9A
# both validate for one character, giving 退 instead of 通) and the line is
# reported instead of written.
#
# Recovery path 3 (none): lines containing U+FFFD lost bytes that no search can
# recover. They are listed for manual rewriting. This script never invents text.
#
# Usage:
#   ... -File port\fix-mojibake3.ps1 -DryRun    # report only
#   ... -File port\fix-mojibake3.ps1            # write repairs
#   ... -File port\fix-mojibake3.ps1 -Scan      # find every leftover U+FFFD line

param(
    [switch]$DryRun,
    [switch]$Scan
)

$root = Split-Path -Parent $PSScriptRoot
$script:strictUtf8 = New-Object System.Text.UTF8Encoding($false, $true)
$script:gbk = [System.Text.Encoding]::GetEncoding(936)

function Get-CjkCount([string]$l) {
    $n = 0
    foreach ($ch in $l.ToCharArray()) {
        $v = [int]$ch
        if ($v -ge 0x4E00 -and $v -le 0x9FFF) { $n++ }
    }
    return $n
}

function Get-ValidUtf8Text([byte[]]$bytes) {
    try { return $script:strictUtf8.GetString($bytes) } catch { return $null }
}

# Returns @{ Text = <repair>; Ambiguous = <bool> } or $null when not mojibake.
function Repair-Line([string]$l) {
    if ((Get-CjkCount $l) -lt 4) { return $null }
    try { $bytes = $script:gbk.GetBytes($l) } catch { return $null }

    $exact = Get-ValidUtf8Text $bytes
    if ($null -ne $exact) { return @{ Text = $exact; Ambiguous = $false } }

    $positions = @()
    for ($i = 0; $i -lt $bytes.Length; $i++) {
        if ($bytes[$i] -eq 0x3F) { $positions += $i }
    }
    if ($positions.Count -eq 0 -or $positions.Count -gt 2) { return $null }

    $work = @(, ([byte[]]$bytes.Clone()))
    foreach ($p in $positions) {
        $next = @()
        foreach ($buf in $work) {
            for ($b = 0x80; $b -le 0xBF; $b++) {
                $c = [byte[]]$buf.Clone()
                $c[$p] = [byte]$b
                $next += , $c
            }
        }
        $work = $next
    }

    $solutions = @()
    foreach ($t in $work) {
        $txt = Get-ValidUtf8Text $t
        if ($null -ne $txt) { $solutions += $txt }
    }
    if ($solutions.Count -eq 0) { return $null }
    if ($solutions.Count -gt 1) { return @{ Text = $solutions[0]; Ambiguous = $true; Count = $solutions.Count } }
    return @{ Text = $solutions[0]; Ambiguous = $false }
}

$targets = @(
    'client\resource\MaidModelRegistry.java',
    'entity\ai\brain\task\MaidBedTask.java',
    'entity\passive\EntityMaid.java',
    'entity\projectile\MaidFishingHook.java',
    'entity\task\TaskFeedAnimal.java',
    'entity\task\TaskTridentAttack.java',
    'init\InitBlocks.java',
    'init\InitItems.java',
    'item\ItemChisel.java',
    'client\init\InitStatueClient.java',
    'client\renderer\item\GarageKitItemRenderer.java'
) | ForEach-Object { Join-Path 'src\main\java\com\github\tartaricacid\touhoulittlemaid' $_ }

if ($Scan) {
    $hits = 0
    Get-ChildItem -Path (Join-Path $root 'src') -Recurse -File -Filter '*.java' | ForEach-Object {
        $ls = [System.IO.File]::ReadAllText($_.FullName, [System.Text.Encoding]::UTF8) -split "`n"
        for ($i = 0; $i -lt $ls.Length; $i++) {
            if ($ls[$i].IndexOf([char]0xFFFD) -ge 0) {
                Write-Output ("FFFD " + $_.FullName.Replace($root + '\', '') + " L" + ($i + 1))
                $hits++
            }
        }
    }
    Write-Output ("fffd_lines=" + $hits)
    return
}

$repaired = 0
$ambiguous = 0
$unrecoverable = 0

foreach ($rel in $targets) {
    $path = Join-Path $root $rel
    if (-not (Test-Path $path)) { Write-Output ("MISSING " + $rel); continue }
    $lines = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8) -split "`n"
    $changed = $false

    for ($i = 0; $i -lt $lines.Length; $i++) {
        $r = Repair-Line $lines[$i]
        if ($null -eq $r) {
            if ($lines[$i].IndexOf([char]0xFFFD) -ge 0) {
                Write-Output ("UNRECOVERABLE " + $rel + " L" + ($i + 1) + ": " + $lines[$i].Trim())
                $unrecoverable++
            }
            continue
        }
        if ($r.Ambiguous) {
            Write-Output ("AMBIGUOUS " + $rel + " L" + ($i + 1) + " (" + $r.Count + " candidates)");
            Write-Output ("   now: " + $lines[$i].Trim())
            $ambiguous++
            continue
        }
        Write-Output ("REPAIRED " + $rel + " L" + ($i + 1))
        Write-Output ("   old: " + $lines[$i].Trim())
        Write-Output ("   new: " + $r.Text.Trim())
        $lines[$i] = $r.Text
        $changed = $true
        $repaired++
    }

    if ($changed -and -not $DryRun) {
        [System.IO.File]::WriteAllText($path, ($lines -join "`n"), (New-Object System.Text.UTF8Encoding($false)))
    }
}

Write-Output ("repaired=" + $repaired + " ambiguous=" + $ambiguous + " unrecoverable=" + $unrecoverable + " dryRun=" + [bool]$DryRun)

