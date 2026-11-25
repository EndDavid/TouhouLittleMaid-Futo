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

