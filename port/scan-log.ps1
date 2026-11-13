# Scan the dev-client log for model / texture problems.
$ErrorActionPreference = 'Stop'
$log = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo\run\logs\latest.log'
$out = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo\port\_logscan.txt'
$sb = New-Object System.Text.StringBuilder

if (-not (Test-Path $log)) { [System.IO.File]::WriteAllText($out, 'no log'); exit }

$lines = [System.IO.File]::ReadAllLines($log, [System.Text.Encoding]::UTF8)
[void]$sb.AppendLine("lines=" + $lines.Count)
[void]$sb.AppendLine('')

$pats = @(
    'Unable to load model',
    'Failed to load',
    'Missing texture',
    'missing model',
    'texture does not exist',
    'Exception',
    'ERROR',
    'atlas',
    'Model loading time',
    'BookContentResourceListenerLoader'
)
foreach ($p in $pats) {
    $hits = $lines | Where-Object { $_ -like "*$p*" }
    [void]$sb.AppendLine("--- '$p' : " + $hits.Count)
    $i = 0
    foreach ($h in $hits) {
        if ($i -ge 25) { [void]$sb.AppendLine('    ...'); break }
        [void]$sb.AppendLine('    ' + $h.Trim())
        $i++
    }
}

[System.IO.File]::WriteAllText($out, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Host 'scanned'

