# Per-line mojibake detector. Pure ASCII on purpose: Windows PowerShell 5.1
# parses a BOM-less .ps1 as ANSI, so any non-ASCII literal in here would be a
# syntax error (that very mistake is what produced the corruption this hunts).
#
# A line is mojibake when its text, encoded with the GBK code page, yields a
# byte sequence that is valid UTF-8 again. Genuine Chinese does not survive that
# round trip.
#
# Two false-positive sources were removed from the first version:
#   * ko_kr / ja_jp / es_es / fr_fr / ru_ru / vi_vn / de_de - not Chinese
#     authored, so they are skipped;
#   * very short strings (2-character comments), where a chance GBK->UTF-8
#     coincidence is common. A line must carry at least $MinCjk CJK characters
#     before it can be reported.
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File port\scan-mojibake2.ps1
#   powershell -NoProfile -ExecutionPolicy Bypass -File port\scan-mojibake2.ps1 -Fix

param(
    [int]$MinCjk = 6,
    [switch]$Fix
)

$root = Split-Path -Parent $PSScriptRoot
$strictUtf8 = New-Object System.Text.UTF8Encoding($false, $true)
$gbk = [System.Text.Encoding]::GetEncoding(936)

$exts = @('.java', '.md', '.ps1', '.txt', '.gradle', '.properties', '.json')
$skipDirs = @('\build\', '\.git\', '\.gradle\', '\run\', '\port\upstream\', '\_ref\')
$skipLang = @('ko_kr', 'ja_jp', 'es_es', 'fr_fr', 'ru_ru', 'vi_vn', 'de_de', 'en_us')

$script:totalHits = 0
$script:hitFiles = 0
$script:minCjk = $MinCjk
$script:gbk = $gbk
$script:strictUtf8 = $strictUtf8

function Test-MojibakeLine([string]$l) {
    if (-not ($l.ToCharArray() | Where-Object { [int]$_ -gt 127 })) { return $null }
    $cjk = 0
    foreach ($ch in $l.ToCharArray()) {
        if ([int]$ch -ge 0x4E00 -and [int]$ch -le 0x9FFF) { $cjk++ }
    }
    if ($cjk -lt $script:minCjk) { return $null }
    try { $bytes = $script:gbk.GetBytes($l) } catch { return $null }
    try { return $script:strictUtf8.GetString($bytes) } catch { return $null }
}

Get-ChildItem -Path $root -Recurse -File | Where-Object {
    $p = $_.FullName
    if ($exts -notcontains $_.Extension) { return $false }
    if ($skipDirs | Where-Object { $p -like "*$_*" }) { return $false }
    if ($skipLang | Where-Object { $p -like "*\$_.json" }) { return $false }
    return $true
} | ForEach-Object {
    $path = $_.FullName
    $text = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
    if ($text.Length -eq 0) { return }
    if (-not ($text.ToCharArray() | Where-Object { [int]$_ -gt 127 })) { return }

    $lines = $text -split "`n"
    $bad = @()
    for ($i = 0; $i -lt $lines.Length; $i++) {
        $fixed = Test-MojibakeLine $lines[$i]
        if ($null -ne $fixed) {
            $bad += [pscustomobject]@{ Index = $i; Line = $lines[$i]; Fixed = $fixed }
        }
    }
    if ($bad.Count -eq 0) { return }

    $script:totalHits += $bad.Count
    $script:hitFiles++
    Write-Output ("== " + $path.Replace($root + '\', '') + "  (" + $bad.Count + " lines)")
    foreach ($b in $bad) {
        Write-Output ("   L" + ($b.Index + 1) + " bad: " + $b.Line.Trim())
        Write-Output ("         fix: " + $b.Fixed.Trim())
    }

    if ($Fix) {
        $out = ($lines | ForEach-Object {
            $f = Test-MojibakeLine $_
            if ($null -ne $f) { return $f }
            return $_
        }) -join "`n"
        [System.IO.File]::WriteAllText($path, $out, (New-Object System.Text.UTF8Encoding($false)))
        Write-Output "   -> rewritten"
    }
}

Write-Output ("files_with_mojibake=" + $script:hitFiles + " lines=" + $script:totalHits + " fixed=" + [bool]$Fix)

