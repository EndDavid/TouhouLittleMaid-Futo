$ErrorActionPreference = 'Stop'
$root = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo'
$hold = Join-Path $root 'port\book_pages_held_back'
$book = Join-Path $root 'src\main\resources\assets\touhou_little_maid\patchouli_books\memorizable_gensokyo\en_us\entries'

# pages whose every referenced item is now registered
$restore = @(
    'other\chair.json',
    'other\chair_show.json',
    'other\granite_inari_fox.json',
    'other\maid_bed.json',
    'other\shrine.json',
    'maid\film.json',
    'maid\substitute_jizo.json'
)

if (-not (Test-Path $book)) { throw "book entries dir missing: $book" }

foreach ($rel in $restore) {
    $src = Join-Path $hold $rel
    $dst = Join-Path $book $rel
    if (-not (Test-Path $src)) { Write-Output "SKIP (not held back) $rel"; continue }
    if (Test-Path $dst) { Write-Output "SKIP (already in book) $rel"; continue }
    $dir = Split-Path $dst -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    Copy-Item $src $dst -Force
    Remove-Item $src -Force
    Write-Output "RESTORED $rel"
}

# register the newly available item ids in the validator
$v = Join-Path $hold 'validate-book.ps1'
$text = Get-Content $v -Raw
if ($text -notmatch 'touhou_little_maid:chair_show') {
    $anchor = "  'touhou_little_maid:substitute_jizo'`r`n)"
    $repl = "  'touhou_little_maid:substitute_jizo',`r`n  # --- chair milestone: chair / chair_show / scarecrow ---`r`n  'touhou_little_maid:chair',`r`n  'touhou_little_maid:chair_show',`r`n  'touhou_little_maid:scarecrow'`r`n)"
    if (-not $text.Contains($anchor)) { throw 'validator anchor not found' }
    $text = $text.Replace($anchor, $repl)
    [System.IO.File]::WriteAllText($v, $text, (New-Object System.Text.UTF8Encoding($false)))
    Write-Output 'validator updated'
} else {
    Write-Output 'validator already updated'
}

