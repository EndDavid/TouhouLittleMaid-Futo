# Copy the freshly built jar into the launcher instance, keeping a backup.
$ErrorActionPreference = 'Stop'
$repo = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo'
$src  = Join-Path $repo 'build\libs\touhoulittlemaid-fabric-1.18.2-futo-1.2.2-futo.1.jar'
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

# list what else is in the mods dir so a stale duplicate cannot hide
[void]$sb.AppendLine('')
[void]$sb.AppendLine('mods dir:')
foreach ($f in Get-ChildItem $md | Sort-Object Name) {
    [void]$sb.AppendLine(("  {0,-64} {1,10}  {2}" -f $f.Name, $f.Length, $f.LastWriteTime))
}

[System.IO.File]::WriteAllText($out, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Host 'deployed'

