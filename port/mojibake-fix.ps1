# Detects and repairs UTF-8 text that was decoded as GBK and re-saved as UTF-8.
# Pure ASCII on purpose: Windows PowerShell 5.1 parses a BOM-less .ps1 as ANSI,
# and that exact mistake is the source of the corruption this script undoes.
#
# Detection is repair-based, not round-trip-based. The first version only tried
#   mojibake --GBK encode--> bytes --strict UTF-8 decode--> text
# which misses every line that also lost a byte (the decoder wrote '?' there and
# the byte sequence no longer validates). Those lines were silently skipped, and
# whole files (e.g. client\render\MaidAreaRenderer.java) stayed broken while the
# scanner reported "0 hits". So a line now counts as mojibake when the '?'
# positions can be retried over the UTF-8 CONTINUATION range (0x80..0xBF) and
# yield at least one validating candidate.
#
# A line must carry at least $MinCjk CJK characters first. Genuine Chinese text,
# GBK-encoded, essentially never validates as UTF-8; but very short strings can
# by coincidence, which is what that threshold guards against.
#
# Classification per line:
#   EXACT      the round trip alone recovers it (safe to write)
#   UNIQUE     exactly one substitution candidate validates (safe to write)
#   AMBIGUOUS  several candidates validate; the intended text is unknowable
#              from the bytes alone, so it is reported, never written
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File port\mojibake-fix.ps1 -Report
#   powershell -NoProfile -ExecutionPolicy Bypass -File port\mojibake-fix.ps1 -Fix
#   powershell -NoProfile -ExecutionPolicy Bypass -File port\mojibake-fix.ps1 -Report -MinCjk 3

param(
    [switch]$Report,
    [switch]$Fix,
    [int]$MinCjk = 6
)

$root = Split-Path -Parent $PSScriptRoot
$script:strictUtf8 = New-Object System.Text.UTF8Encoding($false, $true)
$script:gbk = [System.Text.Encoding]::GetEncoding(936)
$script:minCjk = $MinCjk

$exts = @('.java', '.json', '.md', '.txt', '.ps1', '.gradle', '.properties')
$skipDirs = @('\build\', '\.git\', '\.gradle\', '\run\', '\port\upstream\', '\_ref\')
$skipLang = @('ko_kr', 'ja_jp', 'es_es', 'fr_fr', 'ru_ru', 'vi_vn', 'de_de', 'en_us')

function Get-CjkCount([string]$l) {
    $n = 0
    foreach ($ch in $l.ToCharArray()) {
        $v = [int]$ch
        if ($v -ge 0x4E00 -and $v -le 0x9FFF) { $n++ }
    }
    return $n
}

function Test-ValidUtf8([byte[]]$bytes) {
    try { $null = $script:strictUtf8.GetString($bytes); return $true } catch { return $false }
}

# Returns @{ Kind='EXACT'|'UNIQUE'|'AMBIGUOUS'; Text=..; Count=.. } or $null.
function Test-Line([string]$l) {
    if ((Get-CjkCount $l) -lt $script:minCjk) { return $null }
    try { $bytes = $script:gbk.GetBytes($l) } catch { return $null }

    if (Test-ValidUtf8 $bytes) {
        return @{ Kind = 'EXACT'; Text = $script:strictUtf8.GetString($bytes); Count = 1 }
    }

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
        if (Test-ValidUtf8 $t) { $solutions += $script:strictUtf8.GetString($t) }
    }
    if ($solutions.Count -eq 0) { return $null }
    if ($solutions.Count -eq 1) { return @{ Kind = 'UNIQUE'; Text = $solutions[0]; Count = 1 } }
    return @{ Kind = 'AMBIGUOUS'; Text = $solutions[0]; Count = $solutions.Count }
}

if (-not ($Report -or $Fix)) { $Report = $true }

$files = Get-ChildItem -Path $root -Recurse -File | Where-Object {
    $p = $_.FullName
    if ($exts -notcontains $_.Extension) { return $false }
    if ($skipDirs | Where-Object { $p -like "*$_*" }) { return $false }
    if ($skipLang | Where-Object { $p -like "*\$_.json" }) { return $false }
    return $true
}

$stat = @{ EXACT = 0; UNIQUE = 0; AMBIGUOUS = 0 }
$touched = 0
# NOTE: must not be called $report -- PowerShell variable names are
# case-insensitive, so it would collide with the -Report switch parameter.
$rows = New-Object System.Collections.Generic.List[string]

foreach ($f in $files) {
    $text = [System.IO.File]::ReadAllText($f.FullName, [System.Text.Encoding]::UTF8)
    if ($text.Length -eq 0) { continue }
    $hasNonAscii = $false
    foreach ($ch in $text.ToCharArray()) { if ([int]$ch -gt 127) { $hasNonAscii = $true; break } }
    if (-not $hasNonAscii) { continue }

    $lines = $text -split "`n"
    $changed = $false
    for ($i = 0; $i -lt $lines.Length; $i++) {
        $r = Test-Line $lines[$i]
        if ($null -eq $r) { continue }
        $rel = $f.FullName.Replace($root + '\', '')
        if ($r.Kind -eq 'AMBIGUOUS') {
            $stat.AMBIGUOUS++
            $rows.Add("AMBIGUOUS`t" + $rel + "`t" + ($i + 1) + "`t" + $lines[$i].Trim())
            continue
        }
        $stat[$r.Kind]++
        $rows.Add($r.Kind + "`t" + $rel + "`t" + ($i + 1) + "`t" + $lines[$i].Trim() + "`t=> `t" + $r.Text.Trim())
        if ($Fix) {
            # $r.Text already ends with the original CR when the line had one:
            # CR is 0x0D, an ASCII byte that survives the GBK round trip unchanged.
            $lines[$i] = $r.Text
            $changed = $true
        }
    }

    if ($changed) {
        [System.IO.File]::WriteAllText($f.FullName, ($lines -join "`n"), (New-Object System.Text.UTF8Encoding($false)))
        $touched++
    }
}

$outPath = Join-Path $PSScriptRoot '_mojibake-report.tsv'
[System.IO.File]::WriteAllLines($outPath, $rows, (New-Object System.Text.UTF8Encoding($false)))

Write-Output ("exact=" + $stat.EXACT + " unique=" + $stat.UNIQUE + " ambiguous=" + $stat.AMBIGUOUS + " filesWritten=" + $touched + " fix=" + [bool]$Fix)
Write-Output ("report: " + $outPath)
