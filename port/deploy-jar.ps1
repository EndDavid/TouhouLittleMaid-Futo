# Copy the freshly built jar into the launcher instance, keeping a backup.
$ErrorActionPreference = 'Stop'
$repo = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo'
# Resolve the jar instead of hard-coding a version: the artifact name is
# derived from mod_version in gradle.properties and changes every release.
# Newest non-sources jar wins, so a stale artifact from an older version is
# never deployed by accident as long as build/ was refreshed.
$libs = Join-Path $repo 'build\libs'
$jars = @(Get-ChildItem -Path $libs -Filter 'touhoulittlemaid-fabric-*.jar' -ErrorAction SilentlyContinue |
          Where-Object { $_.Name -notlike '*-sources.jar' } | Sort-Object LastWriteTime -Descending)
if ($jars.Count -eq 0) { throw "no built jar under $libs - run ./gradlew.bat build first" }
$src  = $jars[0].FullName
$md   = 'D:\MCHMCL\.minecraft\versions\1.18.2-Fabric-test\mods'
$bak  = 'D:\MCHMCL\.minecraft\versions\1.18.2-Fabric-test\mods_backup_tlm'
$out  = Join-Path $repo 'port\_deploy.txt'
$sb = New-Object System.Text.StringBuilder

# ---------------------------------------------------------------------------
# REFUSE TO DEPLOY INTO A RUNNING GAME.
#
# Replacing the jar under a live JVM does not fail loudly: the JVM keeps its
# open ZipFile handle, the bytes underneath change, and the next class it tries
# to load dies with
#     ZipException: ZipFile invalid LOC header (bad signature)
# wrapped as a hard crash ("Failed to load class file for ..."). That is
# exactly what happened on 2026-11-28 11:16: the jar was deployed over a game
# that had been running since 11:04, and the crash landed on whatever class was
# loaded next - it looked like "placing a cake crashes the game".
#
# Windows lets the copy through (the JVM opens jars with FILE_SHARE_WRITE), so
# the only reliable guard is to look for a live process of that instance first.
# --gameDir is on the command line of both the HMCL launcher and this script's
# sibling runServer/runClient, and it is the same regardless of which java.exe
# or which arguments the launcher picked.
# ---------------------------------------------------------------------------
$instanceTag = '1.18.2-Fabric-test'
$running = @()
try {
    $running = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" -ErrorAction Stop |
                 Where-Object { $_.CommandLine -and $_.CommandLine.Contains($instanceTag) })
} catch {
    # Get-CimInstance can be unavailable (very old PowerShell / locked down box).
    # Do not block the deploy on that: fall back to a plain process-name scan,
    # which can only over-report (and therefore only err on the safe side).
    $running = @(Get-Process java, javaw -ErrorAction SilentlyContinue)
    if ($running.Count -gt 0) {
        [void]$sb.AppendLine('WARN: could not inspect command lines; assuming a game may be running.')
    }
}
if ($running.Count -gt 0) {
    $ids = ($running | ForEach-Object { $_.ProcessId }) -join ', '
    # The fallback path cannot tell instances apart, so it warns instead of
    # aborting - otherwise every dev runClient would block the deploy.
    if ($running[0].PSObject.Properties['CommandLine']) {
        throw "a game of $instanceTag is still running (pid $ids) - close it first, or the JVM will read a half-replaced jar"
    } else {
        [void]$sb.AppendLine("WARN: java processes present (pid $ids) but unidentifiable; deploying anyway.")
    }
}
if (-not (Test-Path $src)) { throw "no jar at $src" }
if (-not (Test-Path $md))  { throw "no mods dir at $md" }
if (-not (Test-Path $bak)) { New-Item -ItemType Directory -Path $bak | Out-Null }

$dst = Join-Path $md (Split-Path $src -Leaf)
if (Test-Path $dst) {
    $old = Get-Item $dst
    $stamp = $old.LastWriteTime.ToString('yyyyMMdd-HHmmss')
    $keep = Join-Path $bak ($old.Name + '.' + $stamp + '.bak')
    Copy-Item $dst $keep -Force
    [void]$sb.AppendLine("backed up old jar -> " + $keep + "  " + $old.Length + " bytes  " + $stamp)
}

Copy-Item $src $dst -Force
$new = Get-Item $dst
[void]$sb.AppendLine("deployed         -> " + $dst)
[void]$sb.AppendLine("  size  = " + $new.Length)
[void]$sb.AppendLine("  mtime = " + $new.LastWriteTime)
$h1 = (Get-FileHash $src -Algorithm SHA256).Hash
$h2 = (Get-FileHash $dst -Algorithm SHA256).Hash
[void]$sb.AppendLine("  sha256 match = " + ($h1 -eq $h2))

# Purge OTHER versions of this mod from mods/.
# The artifact name carries mod_version, so every release leaves the previous
# jar behind under a different name. Two jars with the same mod id make Fabric
# Loader refuse to start (or, worse, silently load the old one).
$siblings = @(Get-ChildItem -Path $md -Filter 'touhoulittlemaid-fabric-*.jar' |
              Where-Object { $_.FullName -ne $new.FullName })
foreach ($s in $siblings) {
    $stamp2 = $s.LastWriteTime.ToString('yyyyMMdd-HHmmss')
    $keep2 = Join-Path $bak ($s.Name + '.' + $stamp2 + '.bak')
    Move-Item $s.FullName $keep2 -Force
    [void]$sb.AppendLine("removed stale    -> " + $s.Name + "  (moved to " + $keep2 + ")")
}

# list what else is in the mods dir so a stale duplicate cannot hide
[void]$sb.AppendLine('')
[void]$sb.AppendLine('mods dir:')
foreach ($f in Get-ChildItem $md | Sort-Object Name) {
    [void]$sb.AppendLine(("  {0,-64} {1,10}  {2}" -f $f.Name, $f.Length, $f.LastWriteTime))
}

[System.IO.File]::WriteAllText($out, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Host 'deployed'

