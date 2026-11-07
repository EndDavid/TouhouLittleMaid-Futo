# check-encoding.ps1 -- guard against U+FFFD (replacement character) corruption.
#
# Why: comments in this project are Chinese, and some file-write paths
# re-encode content, turning Chinese characters into U+FFFD. Inside comments
# that only hurts readability, but inside string literals / lang files it
# silently changes runtime behaviour. Run this after any bulk write.
#
# NOTE: intentionally ASCII-only. Windows PowerShell 5.1 reads .ps1 files as
# ANSI unless they carry a BOM, so non-ASCII source here would break parsing
# on machines whose ANSI code page is not UTF-8.
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\check-encoding.ps1
#   powershell ... -File .\check-encoding.ps1 -Root .\src\main\resources
param(
    [string]$Root = $PSScriptRoot,
    [string[]]$Extensions = @("*.java", "*.md", "*.json", "*.ps1", "*.gradle", "*.properties")
)
$total = 0
$files = 0
$badFiles = 0
$fffd = [char]0xFFFD
foreach ($ext in $Extensions) {
    Get-ChildItem -Path $Root -Recurse -Include $ext -File -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -notmatch '\\build\\' -and $_.FullName -notmatch '\\port\\' } |
        ForEach-Object {
            $files++
            $text = [System.IO.File]::ReadAllText($_.FullName, [System.Text.Encoding]::UTF8)
            $n = ([regex]::Matches($text, "[\uFFFD]")).Count
            if ($n -gt 0) {
                $badFiles++
                $total += $n
                $rel = $_.FullName
                if ($rel.StartsWith($Root)) { $rel = $rel.Substring($Root.Length).TrimStart('\', '/') }
                $lines = $text -split "`r?`n"
                for ($i = 0; $i -lt $lines.Count; $i++) {
                    if ($lines[$i].IndexOf($fffd) -ge 0) {
                        Write-Output ("BAD  {0}:{1}  {2}" -f $rel, ($i + 1), $lines[$i].Trim())
                    }
                }
            }
        }
}
Write-Output ""
Write-Output "SCANNED_FILES=$files  BAD_FILES=$badFiles  FFFD_CHARS=$total"
if ($total -gt 0) {
    Write-Output "FAIL: corrupted characters found; fix the file:line entries listed above."
    exit 1
}
Write-Output "OK: no U+FFFD found."
exit 0