$ErrorActionPreference = 'Stop'
$root = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo\src\main\resources\assets'
$files = Get-ChildItem -Recurse -Filter '*.json' -Path $root
$rows = New-Object System.Collections.ArrayList
foreach ($f in $files) {
    $t = Get-Content $f.FullName -Raw
    if ($t -notmatch 'sittingRotationSkirt') { continue }
    $j = $null
    try { $j = $t | ConvertFrom-Json } catch { continue }
    $g = $j.'geometry.model'
    if (-not $g) { continue }
    $s = $g.bones | Where-Object { $_.name -eq 'sittingRotationSkirt' }
    if (-not $s) { continue }
    $kids = @($g.bones | Where-Object { $_.parent -eq 'sittingRotationSkirt' })
    $cubeTotal = 0
    foreach ($k in $kids) { if ($k.cubes) { $cubeTotal = $cubeTotal + $k.cubes.Count } }
    $null = $rows.Add([pscustomobject]@{
        Model = $f.BaseName
        Pivot = ($s.pivot -join ',')
        Kids  = $kids.Count
        Cubes = $cubeTotal
    })
}
"models with the bone      : $($rows.Count)"
"models with skirt geometry: $(@($rows | Where-Object { $_.Cubes -gt 0 }).Count)"
""
$rows | Where-Object { $_.Cubes -gt 0 } | Sort-Object -Property Cubes -Descending |
    Select-Object -First 30 | Format-Table -AutoSize | Out-String -Width 200

