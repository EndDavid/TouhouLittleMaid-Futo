# Applies line-number-addressed comment fixes from a UTF-8 TSV file.
# Pure ASCII (Windows PowerShell 5.1 parses BOM-less .ps1 as ANSI).
#
# Why line numbers instead of string replacement: the lines being replaced are
# mojibake, and reproducing that garbage in the command line is both fragile and
# unreadable. Instead each entry says "at line N of file F, put this text", and
# the script refuses to write unless the current line really is a mangled
# comment (non-ASCII present, and the trim starts with '*', '/**' or '//').
#
# Each file's own EOL is preserved: the split leaves the CR on the line, so the
# replacement re-attaches whatever terminator was there.
#
# Input format (TAB separated, one entry per line):
#   <path relative to src\main\java\com\github\tartaricacid\touhoulittlemaid>\t<line>\t<new text>
#
# Usage:
#   ... -File port\apply-line-fixes.ps1 -Data port\_fix_lines.tsv -DryRun
#   ... -File port\apply-line-fixes.ps1 -Data port\_fix_lines.tsv

param(
    [Parameter(Mandatory = $true)][string]$Data,
    [switch]$DryRun
)

$root = Split-Path -Parent $PSScriptRoot
$javaRoot = Join-Path $root 'src\main\java\com\github\tartaricacid\touhoulittlemaid'

if (-not [System.IO.Path]::IsPathRooted($Data)) { $Data = Join-Path $root $Data }
$entries = [System.IO.File]::ReadAllLines($Data, [System.Text.Encoding]::UTF8)

# path -> @{ lineNumber = text }
$byFile = @{}
$order = @()
foreach ($raw in $entries) {
    if ([string]::IsNullOrWhiteSpace($raw)) { continue }
    $parts = $raw -split "`t", 3
    if ($parts.Count -ne 3) { throw ("bad row: " + $raw) }
    $rel = $parts[0]
    $ln = [int]$parts[1]
    $text = $parts[2]
    if (-not $byFile.ContainsKey($rel)) {
        $byFile[$rel] = @{}
        $order += $rel
    }
    $byFile[$rel][$ln] = $text
}

$applied = 0
$skipped = 0
$errors = 0

foreach ($rel in $order) {
    $path = Join-Path $javaRoot $rel
    if (-not (Test-Path $path)) { Write-Output ("MISSING " + $rel); $errors++; continue }

    $lines = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8) -split "`n"
    $plan = $byFile[$rel]
    $changed = $false

    foreach ($ln in ($plan.Keys | Sort-Object)) {
        $idx = $ln - 1
        if ($idx -lt 0 -or $idx -ge $lines.Length) {
            Write-Output ("OUT OF RANGE " + $rel + " L" + $ln)
            $errors++
            continue
        }
        $cur = $lines[$idx]
        $body = $cur
        $eol = ''
        if ($body.EndsWith("`r")) { $body = $body.Substring(0, $body.Length - 1); $eol = "`r" }

        $trim = $body.Trim()
        $hasNonAscii = $false
        foreach ($ch in $body.ToCharArray()) { if ([int]$ch -gt 127) { $hasNonAscii = $true; break } }
        $looksComment = $trim.StartsWith('*') -or $trim.StartsWith('/**') -or $trim.StartsWith('//')

        if (-not $hasNonAscii -or -not $looksComment) {
            Write-Output ("REFUSED " + $rel + " L" + $ln + " (not a mangled comment): " + $trim)
            $skipped++
            continue
        }

        $lines[$idx] = $plan[$ln] + $eol
        $changed = $true
        $applied++
    }

    if ($changed) {
        if ($DryRun) {
            Write-Output ("WOULD WRITE " + $rel)
        } else {
            [System.IO.File]::WriteAllText($path, ($lines -join "`n"), (New-Object System.Text.UTF8Encoding($false)))
            Write-Output ("WROTE " + $rel)
        }
    }
}

Write-Output ("applied=" + $applied + " skipped=" + $skipped + " errors=" + $errors + " dryRun=" + [bool]$DryRun)

