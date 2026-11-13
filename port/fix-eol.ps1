$ErrorActionPreference = 'Stop'
$root = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo'
$utf8 = New-Object System.Text.UTF8Encoding($false)

$targets = @(
    'README.md',
    'src\main\resources\assets\touhou_little_maid\lang\en_us.json',
    'src\main\resources\assets\touhou_little_maid\lang\zh_cn.json'
)

foreach ($rel in $targets) {
    $path = Join-Path $root $rel
    if (-not (Test-Path $path)) { throw "missing: $rel" }
    $bytes = [System.IO.File]::ReadAllBytes($path)
    $text = [System.Text.Encoding]::UTF8.GetString($bytes)
    if ($text.IndexOf([char]0xFFFD) -ge 0) { throw "replacement char in $rel" }
    # normalise every ending to LF first, then to CRLF (handles mixed input)
    $text = $text.Replace("`r`n", "`n").Replace("`r", "`n")
    $text = $text.Replace("`n", "`r`n")
    [System.IO.File]::WriteAllText($path, $text, $utf8)
    $lf = ([regex]::Matches($text, "(?<!`r)`n")).Count
    if ($lf -ne 0) { throw "LF left in $rel : $lf" }
    Write-Output ("converted to CRLF: {0}" -f $rel)
}

