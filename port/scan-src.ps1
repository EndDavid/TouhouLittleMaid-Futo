# Guard: no U+FFFD replacement chars and no mangled package names in any Java source.
$ErrorActionPreference = 'Stop'
$root = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo\src\main\java'
$out  = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo\port\_srcscan.txt'
$sb = New-Object System.Text.StringBuilder

$files = Get-ChildItem $root -Recurse -Filter *.java
[void]$sb.AppendLine("files=" + $files.Count)
$bad = 0
foreach ($f in $files) {
    $t = [System.IO.File]::ReadAllText($f.FullName, [System.Text.Encoding]::UTF8)
    $repl = ([regex]::Matches($t, [string][char]0xFFFD)).Count
    $typo = ([regex]::Matches($t, 'touhoull')).Count
    if ($repl -gt 0 -or $typo -gt 0) {
        $bad++
        [void]$sb.AppendLine("BAD  $($f.FullName.Substring($root.Length + 1))  fffd=$repl  touhoull=$typo")
    }
}
[void]$sb.AppendLine("bad files = $bad")
[System.IO.File]::WriteAllText($out, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Host 'scanned'
