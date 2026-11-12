# Copy the altar / shrine block-entity models and renderers from upstream.
#
# Both models and both renderers only touch vanilla 1.18.2 API, so they are
# byte-identical copies. The one exception is the shrine renderer, which reads
# the facing from BlockGomoku.FACING upstream (the shrine reuses that block's
# property); here the shrine has its own FACING, so that is rewritten.

$ErrorActionPreference = 'Stop'

$repo = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo'
$ref  = 'D:\codename_EndDavid\hello java\_ref\tlm-1182'
$pkg  = 'src\main\java\com\github\tartaricacid\touhoulittlemaid'

$pairs = @(
    @('client\model\AltarModel.java',                  'client\model\AltarModel.java'),
    @('client\model\ShrineModel.java',                 'client\model\ShrineModel.java'),
    @('client\renderer\tileentity\TileEntityAltarRenderer.java',  'client\renderer\tileentity\TileEntityAltarRenderer.java'),
    @('client\renderer\tileentity\TileEntityShrineRenderer.java', 'client\renderer\tileentity\TileEntityShrineRenderer.java')
)

$utf8 = New-Object System.Text.UTF8Encoding($false)

foreach ($pair in $pairs) {
    $src = Join-Path $ref  (Join-Path $pkg $pair[0])
    $dst = Join-Path $repo (Join-Path $pkg $pair[1])
    $dir = Split-Path $dst -Parent
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $text = [System.IO.File]::ReadAllText($src, [System.Text.Encoding]::UTF8)

    if ($pair[0] -like '*TileEntityShrineRenderer*') {
        $text = $text.Replace('import com.github.tartaricacid.touhoulittlemaid.block.BlockGomoku;',
                              'import com.github.tartaricacid.touhoulittlemaid.block.BlockShrine;')
        $text = $text.Replace('BlockGomoku.FACING', 'BlockShrine.FACING')
        if ($text.Contains('BlockGomoku')) { throw 'BlockGomoku reference left in the shrine renderer' }
    }

    [System.IO.File]::WriteAllText($dst, $text, $utf8)
    Write-Host ("copied " + $pair[0])
}

