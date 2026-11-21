param([switch]$Quiet)
# Every registered EntityType / BlockEntityType must have a client renderer.
#
# Why this check exists:
#   A missing renderer does NOT fail at registration. The entity spawns and ticks
#   perfectly on the server; only the CLIENT dies, and it dies inside
#   LevelRenderer.renderLevel -> EntityRenderDispatcher.shouldRender with
#       NullPointerException: ... because "entityRenderer" is null
#   which says nothing about the entity that caused it. That is exactly how the
#   broom shipped: the EntityType existed, the renderer registration had been
#   lost in transit, and placing the broom crashed the client.
#
# ASCII only on purpose: Windows PowerShell 5.1 parses a BOM-less .ps1 as ANSI,
# so any non-ASCII byte can swallow the closing quote of the next line.

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$src = Join-Path $repo 'src\main\java'
if (-not (Test-Path $src)) { throw "no source at $src" }

function Read-All([string]$filter) {
    Get-ChildItem -Recurse -Filter $filter $src |
        ForEach-Object { Get-Content $_.FullName -Raw }
}

$java = Read-All '*.java'
$joined = $java -join "`n"

# ---------------------------------------------------------------- declarations

# "public static final EntityType<Foo> NAME =" / "EntityType<?> NAME =" (wrapped lines)
$entityDecls = [regex]::Matches($joined,
    'public static final EntityType<[^>]*(?:<[^>]*>)?[^>]*>\s+([A-Z][A-Z0-9_]*)\s*=') |
    ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique

# "public static final BlockEntityType<Foo> NAME ="
$beDecls = [regex]::Matches($joined,
    'public static final BlockEntityType<[^>]*(?:<[^>]*>)?[^>]*>\s+([A-Z][A-Z0-9_]*)\s*=') |
    ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique

# ---------------------------------------------------------------- registrations

# EntityRendererRegistry.register(InitEntities.X, ) -- also FullyQualified references
$entityRegs = [regex]::Matches($joined,
    'EntityRendererRegistry\.register\(\s*(?:com\.[A-Za-z0-9_.]*\.)?InitEntities\.([A-Z][A-Z0-9_]*)') |
    ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique

$beRegs = [regex]::Matches($joined,
    'BlockEntityRendererRegistry\.register\(\s*(?:com\.[A-Za-z0-9_.]*\.)?InitBlocks\.([A-Z][A-Z0-9_]*)') |
    ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique

# ---------------------------------------------------------------- compare

# Entity types that legitimately have no renderer (none right now -- every type
# we register is something the player can see, so an empty list is deliberate).
$entityAllow = @()

# Block entity types with no renderer: these blocks are ENTITYBLOCK_ANIMATED only
# through a renderer, so they all need one too.
#
# Two exceptions, both because the block draws itself from blockstates/ and the
# block entity carries data only:
#   MAID_BEACON_TE  - BlockMaidBeacon#getRenderShape is MODEL (storage power / effect)
#   MODEL_SWITCHER_TE - BlockModelSwitcher#getRenderShape is MODEL (six-sided texture;
#                       the block entity only holds the bound maid UUID + mode list)
$beAllow = @('MAID_BEACON_TE', 'MODEL_SWITCHER_TE')

$problems = 0

foreach ($d in $entityDecls) {
    if ($entityAllow -contains $d) { continue }
    if (-not ($entityRegs -contains $d)) {
        Write-Output ("NO ENTITY RENDERER      InitEntities.{0}" -f $d)
        $problems++
    }
}
foreach ($d in $beDecls) {
    if ($beAllow -contains $d) { continue }
    if (-not ($beRegs -contains $d)) {
        Write-Output ("NO BLOCK ENTITY RENDERER InitBlocks.{0}" -f $d)
        $problems++
    }
}

# A registration for a name that is not declared means the field was renamed /
# deleted but the client still points at it -- also worth knowing about.
foreach ($r in $entityRegs) {
    if (-not ($entityDecls -contains $r)) {
        Write-Output ("DANGLING ENTITY REG     InitEntities.{0}" -f $r)
        $problems++
    }
}
foreach ($r in $beRegs) {
    if (-not ($beDecls -contains $r)) {
        Write-Output ("DANGLING BE REG         InitBlocks.{0}" -f $r)
        $problems++
    }
}

if (-not $Quiet) {
    Write-Output ("entity types = {0}  renderers = {1}" -f $entityDecls.Count, $entityRegs.Count)
    Write-Output ("block entity types = {0}  renderers = {1}" -f $beDecls.Count, $beRegs.Count)
}

if ($problems -eq 0) { Write-Output 'ALL OK' } else { Write-Output ("problems={0}" -f $problems) }
exit $problems

