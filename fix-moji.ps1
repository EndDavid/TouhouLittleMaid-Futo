# fix-moji.ps1 -- rewrite specific lines of a file from an external data table.
#
# ASCII-only by design: Windows PowerShell 5.1 reads .ps1 as ANSI unless the
# file carries a BOM, so non-ASCII source here would break parsing.
#
# Data file format, one record per line, tab separated:
#   <relative path> \t <1-based line number> \t <full replacement text>
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\fix-moji.ps1 `
#       -Root <dir> -Data <datafile>

param(
    [Parameter(Mandatory=$true)][string]$Root,
    [Parameter(Mandatory=$true)][string]$Data,
    [switch]$Force
)

$utf8 = New-Object System.Text.UTF8Encoding($false)
$fffd = [char]0xFFFD
$records = [System.IO.File]::ReadAllLines($Data, [System.Text.Encoding]::UTF8)

$byFile = @{}
foreach ($rec in $records) {
    if ([string]::IsNullOrWhiteSpace($rec)) { continue }
    $parts = $rec -split "`t", 3
    if ($parts.Count -lt 3) { Write-Output "SKIP(bad record): $rec"; continue }
    $rel = $parts[0]
    $ln = [int]$parts[1]
    if (-not $byFile.ContainsKey($rel)) { $byFile[$rel] = @{} }
    $byFile[$rel][$ln] = $parts[2]
}

$replaced = 0
foreach ($rel in $byFile.Keys) {
    $full = Join-Path $Root $rel
    if (-not (Test-Path $full)) { Write-Output "MISSING: $rel"; continue }
    $lines = [System.IO.File]::ReadAllLines($full, [System.Text.Encoding]::UTF8)
    $edits = $byFile[$rel]
    foreach ($ln in $edits.Keys) {
        $idx = $ln - 1
        if ($idx -lt 0 -or $idx -ge $lines.Count) { Write-Output "OUT_OF_RANGE: $rel : $ln"; continue }
        if (-not $Force -and -not $lines[$idx].Contains($fffd)) { Write-Output "ALREADY_CLEAN: $rel : $ln"; continue }
        $lines[$idx] = $edits[$ln]
        $replaced++
    }
    [System.IO.File]::WriteAllLines($full, $lines, $utf8)
}
Write-Output "LINES_REPLACED=$replaced"

