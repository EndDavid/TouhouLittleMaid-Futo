# Confirm the rebuilt jar carries the bedrock block models and the new textures.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$repo = 'D:\codename_EndDavid\hello java\touhou_little_maid_futo'
# Resolve the jar instead of hard-coding a version: the artifact name is
# derived from mod_version in gradle.properties and changes every release.
$libs = Join-Path $repo 'build\libs'
$jars = @(Get-ChildItem -Path $libs -Filter 'touhoulittlemaid-fabric-*.jar' -ErrorAction SilentlyContinue |
          Where-Object { $_.Name -notlike '*-sources.jar' } | Sort-Object LastWriteTime -Descending)
if ($jars.Count -eq 0) { throw "no built jar under $libs - run ./gradlew.bat build first" }
$jar  = $jars[0].FullName
$out  = Join-Path $repo 'port\_jarcheck.txt'
$sb = New-Object System.Text.StringBuilder

$z = [System.IO.Compression.ZipFile]::OpenRead($jar)
[void]$sb.AppendLine("jar: " + (Split-Path $jar -Leaf) + "  " + (Get-Item $jar).Length + " bytes  " + (Get-Item $jar).LastWriteTime)
[void]$sb.AppendLine("entries=" + $z.Entries.Count)

$want = @(
    'assets/touhou_little_maid/models/bedrock/block/altar.json',
    'assets/touhou_little_maid/models/bedrock/block/shrine.json',
    'assets/touhou_little_maid/textures/bedrock/block/altar.png',
    'assets/touhou_little_maid/textures/bedrock/block/shrine.png',
    'assets/touhou_little_maid/models/block/maid_beacon_up.json',
    'assets/touhou_little_maid/textures/block/scarecrow_lower.png',
    'assets/touhou_little_maid/textures/item/spawn_box.png',
    'assets/touhou_little_maid/textures/item/camera.png',
    'com/github/tartaricacid/touhoulittlemaid/client/resource/BedrockBlockModels.class',
    'com/github/tartaricacid/touhoulittlemaid/client/renderer/tileentity/GeoBlockRenderer.class',
    # 这三个是「纯 Java、且不是 mod」的库，靠 jar { from(zipTree(...)) } 合并进来。
    # 它们缺失过一次：jar 装进正式实例后直接 NoClassDefFoundError 崩在入口点阶段，
    # 而 runClient 永远发现不了（Gradle 把它们放在开发环境的 classpath 上）。
    'org/yaml/snakeyaml/Yaml.class',
    'io/github/jaredmdobson/concentus/OpusDecoder.class',
    'org/gagravarr/ogg/OggFile.class'
)
foreach ($w in $want) {
    $e = $z.Entries | Where-Object { $_.FullName -eq $w }
    [void]$sb.AppendLine(("{0,-88} {1,10}" -f $w, $(if ($e) { $e.Length } else { 'MISSING' })))
}
# the removed ones must be gone
foreach ($w in @('assets/touhou_little_maid/textures/block/scarecrow.png',
                 'assets/touhou_little_maid/textures/item/camera_2d.png',
                 'com/github/tartaricacid/touhoulittlemaid/client/model/AltarModel.class',
                 'com/github/tartaricacid/touhoulittlemaid/client/model/ShrineModel.class')) {
    $e = $z.Entries | Where-Object { $_.FullName -eq $w }
    [void]$sb.AppendLine(("{0,-88} {1,10}" -f $w, $(if ($e) { 'STILL PRESENT' } else { 'gone' })))
}
$z.Dispose()

[System.IO.File]::WriteAllText($out, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Host 'checked'
