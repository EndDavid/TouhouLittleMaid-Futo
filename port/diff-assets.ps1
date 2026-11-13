# Compare the block / item assets of the two upstream releases.
#
#   1.18.2 -> [forge+1.18.2]touhoulittlemaid-1.18.2-release-1.2.2.jar
#   1.20.1 -> [forge+1.20.1]touhoulittlemaid-1.5.3-forge+mc1.20.1.jar
#
# Writes port\_assetdiff.txt.

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$root = 'D:\codename_EndDavid\hello java'
$jars = [ordered]@{
    old = Join-Path $root '[forge+1.18.2]touhoulittlemaid-1.18.2-release-1.2.2.jar'
    new = Join-Path $root '[forge+1.20.1]touhoulittlemaid-1.5.3-forge+mc1.20.1.jar'
}
$outPath = Join-Path $root 'touhou_little_maid_futo\port\_assetdiff.txt'

function Get-EntryHash([System.IO.Compression.ZipArchiveEntry]$e) {
    $s = $e.Open()
    $sha = [System.Security.Cryptography.SHA256]::Create()
    $h = [BitConverter]::ToString($sha.ComputeHash($s)).Replace('-', '')
    $sha.Dispose()
    $s.Close()
    return $h
}

# relative path -> @{ hash; size }
$sets = @{}
foreach ($k in $jars.Keys) {
    $map = @{}
    $z = [System.IO.Compression.ZipFile]::OpenRead($jars[$k])
    foreach ($e in $z.Entries) {
        if ($e.FullName.StartsWith('assets/touhou_little_maid/')) {
            $rel = $e.FullName.Substring('assets/touhou_little_maid/'.Length)
            if ($rel -match '^(blockstates|models/block|models/item|textures/block|textures/item|sounds|lang)/' -or
                $rel -match '^(sounds\.json)$') {
                $map[$rel] = [pscustomobject]@{ Hash = (Get-EntryHash $e); Size = $e.Length }
            }
        }
    }
    $z.Dispose()
    $sets[$k] = $map
}

$old = $sets['old']
$new = $sets['new']
$allKeys = @($old.Keys) + @($new.Keys) | Sort-Object -Unique

$changed = New-Object System.Collections.Generic.List[string]
$onlyOld = New-Object System.Collections.Generic.List[string]
$onlyNew = New-Object System.Collections.Generic.List[string]
$same = 0
foreach ($k in $allKeys) {
    if (-not $old.ContainsKey($k)) { $onlyNew.Add($k) }
    elseif (-not $new.ContainsKey($k)) { $onlyOld.Add($k) }
    elseif ($old[$k].Hash -ne $new[$k].Hash) { $changed.Add($k) }
    else { $same++ }
}

$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine("1.18.2 files: $($old.Count)   1.20.1 files: $($new.Count)   identical: $same")
[void]$sb.AppendLine('')
[void]$sb.AppendLine("### CHANGED ($($changed.Count))")
foreach ($k in $changed) {
    [void]$sb.AppendLine(("  {0}" -f $k))
}
[void]$sb.AppendLine('')
[void]$sb.AppendLine("### ONLY IN 1.18.2 ($($onlyOld.Count))")
foreach ($k in $onlyOld) { [void]$sb.AppendLine("  $k") }
[void]$sb.AppendLine('')
[void]$sb.AppendLine("### ONLY IN 1.20.1 ($($onlyNew.Count))")
foreach ($k in $onlyNew) { [void]$sb.AppendLine("  $k") }

[System.IO.File]::WriteAllText($outPath, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Host "wrote $outPath"

