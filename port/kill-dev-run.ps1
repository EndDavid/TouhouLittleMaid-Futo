# Stop ONLY the java processes started by this project's Gradle dev runs.
#
# Why this exists: an earlier cleanup matched the word "KnotClient", which is
# present in EVERY Fabric client -- including the user's normal launcher
# instance -- so it killed games that had nothing to do with this build.
#
# The pattern below requires the Loom dev-run config path, which only exists for
# processes launched by `gradlew runClient` / `runServer` in THIS repository.
# A launcher-started game can never match it.
param([switch]$DryRun)

$ErrorActionPreference = 'Stop'
$needle = 'touhou_little_maid_futo\.gradle\loom-cache\launch.cfg'

$targets = Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object {
    $_.CommandLine -and $_.CommandLine.Contains($needle)
}

if (-not $targets) {
    Write-Host 'no dev-run java processes'
    exit 0
}

foreach ($p in $targets) {
    $kind = if ($p.CommandLine -match 'KnotClient') { 'client' } else { 'server' }
    if ($DryRun) {
        Write-Host ("would stop {0,-6} {1}" -f $p.ProcessId, $kind)
    } else {
        try {
            Stop-Process -Id $p.ProcessId -Force -ErrorAction Stop
            Write-Host ("stopped {0,-6} {1}" -f $p.ProcessId, $kind)
        } catch {
            Write-Host ("failed  {0,-6} {1}: {2}" -f $p.ProcessId, $kind, $_.Exception.Message)
        }
    }
}

