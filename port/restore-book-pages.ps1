# Move the four book entries that the shrine-revival milestone unblocked back
# into the manual:
#   maid/film.json             -> film item + the reborn_maid altar recipe page
#   maid/substitute_jizo.json  -> substitute jizo item
#   other/maid_bed.json        -> maid bed block + its altar recipe page
#   other/shrine.json          -> shrine block
# Everything they reference is now registered.

$ErrorActionPreference = 'Stop'

$repo = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo'
$src  = Join-Path $repo 'port\book_pages_held_back'
$dst  = Join-Path $repo 'src\main\resources\assets\touhou_little_maid\patchouli_books\memorizable_gensokyo\en_us\entries'

$files = @('maid\film.json', 'maid\substitute_jizo.json', 'other\maid_bed.json', 'other\shrine.json')

foreach ($f in $files) {
    $from = Join-Path $src $f
    $to   = Join-Path $dst $f
    if (-not (Test-Path $from)) { throw ("missing held-back page: " + $f) }
    if (Test-Path $to) { throw ("destination already exists, refusing to overwrite: " + $f) }
    Copy-Item $from $to
    Write-Host ("restored " + $f)
}

$texture = Join-Path $repo 'src\main\resources\assets\touhou_little_maid\textures\book\bed.png'
Write-Host ("bed.png present: " + (Test-Path $texture))

