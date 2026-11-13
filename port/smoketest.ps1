# Launch the dev client, wait for it to finish loading, then stop only the java
# processes this script started (never blanket-kill).
$ErrorActionPreference = 'Stop'

$repo = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo'
$log  = Join-Path $repo 'port\_runclient.log'
$out  = Join-Path $repo 'port\_runresult.txt'

$before = @(Get-Process java -ErrorAction SilentlyContinue | ForEach-Object { $_.Id })
$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine('before: ' + ($before -join ','))

Push-Location $repo
# runClient blocks until the game window is closed, so give it a hard stop.
$p = Start-Process -FilePath (Join-Path $repo 'gradlew.bat') `
    -ArgumentList 'runClient', '--offline', '--console=plain' `
    -RedirectStandardOutput $log -RedirectStandardError "$log.err" `
    -PassThru -WindowStyle Hidden

$marker = Join-Path $repo 'run\logs\latest.log'
$deadline = (Get-Date).AddMinutes(6)
$ready = $false
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 5
    if (Test-Path $marker) {
        $t = Get-Content $marker -Raw -ErrorAction SilentlyContinue
        if ($t -and $t.Contains('Stopping!') -or ($t -and $t.Contains('Sound engine started'))) {
            $ready = $true
            break
        }
    }
    if ($p.HasExited) { break }
}
[void]$sb.AppendLine('ready=' + $ready + '  exited=' + $p.HasExited)

# give the resource reload a moment to log its warnings
Start-Sleep -Seconds 25

$after = @(Get-Process java -ErrorAction SilentlyContinue | ForEach-Object { $_.Id })
$spawned = @($after | Where-Object { $before -notcontains $_ })
[void]$sb.AppendLine('after: ' + ($after -join ','))
[void]$sb.AppendLine('spawned: ' + ($spawned -join ','))

# stop the game + the wrapper we started, nothing else
foreach ($id in $spawned) {
    try { Stop-Process -Id $id -Force -ErrorAction Stop; [void]$sb.AppendLine("killed $id") }
    catch { [void]$sb.AppendLine("failed to kill $id : $($_.Exception.Message)") }
}
try { if (-not $p.HasExited) { Stop-Process -Id $p.Id -Force } } catch { }

Pop-Location
[System.IO.File]::WriteAllText($out, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Host 'run done'

