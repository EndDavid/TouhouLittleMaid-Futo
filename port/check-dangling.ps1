# Guard: nothing may reference a texture we just deleted.
$ErrorActionPreference = 'Stop'
$assets = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo\src\main\resources\assets\touhou_little_maid'
$out    = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo\port\_dangling.txt'
$sb = New-Object System.Text.StringBuilder

$removed = @(
    'touhou_little_maid:block/scarecrow',
    'touhou_little_maid:item/camera_2d',
    'block/scarecrow',
    'item/camera_2d'
)

foreach ($needle in $removed) {
    # exact-match only: block/scarecrow must not match block/scarecrow_lower
    $hits = New-Object System.Collections.Generic.List[string]
    foreach ($f in Get-ChildItem $assets -Recurse -File -Include *.json) {
        $t = [System.IO.File]::ReadAllText($f.FullName)
        foreach ($m in [regex]::Matches($t, '"([^"]*)"')) {
            $v = $m.Groups[1].Value
            if ($v -eq $needle) {
                $hits.Add($f.FullName.Substring($assets.Length + 1))
            }
        }
    }
    [void]$sb.AppendLine("'$needle' referenced by " + $hits.Count + " file(s)")
    foreach ($h in $hits) { [void]$sb.AppendLine("    $h") }
}

# and confirm the replacements are present
foreach ($n in @('textures\block\scarecrow_lower.png','textures\block\scarecrow_upper.png',
                 'textures\item\camera.png','textures\bedrock\block\altar.png',
                 'textures\bedrock\block\shrine.png')) {
    [void]$sb.AppendLine(("{0,-46} exists={1}" -f $n, (Test-Path (Join-Path $assets $n))))
}

[System.IO.File]::WriteAllText($out, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Host 'checked'

