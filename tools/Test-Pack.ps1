# Validates a built CustomItems resource pack BEFORE players download it.
# Catches the failure modes that are silent in-game: missing noteblock variants (invisible
# blocks), a font/default.json that dropped vanilla glyphs (all text becomes tofu boxes),
# and textures referenced by config but absent from the pack.
#
#   .\tools\Test-Pack.ps1 -Pack ..\..\plugins\CustomItems\pack.zip
#   .\tools\Test-Pack.ps1 -Pack pack.zip -ConfigDir ..\..\plugins\CustomItems
param(
    [Parameter(Mandatory = $true)][string]$Pack,
    [string]$ConfigDir
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$script:Fail = 0
$script:Warn = 0
function Ok   ($m) { Write-Host "  [ OK ] $m"   -ForegroundColor Green }
function Bad  ($m) { Write-Host "  [FAIL] $m"   -ForegroundColor Red;    $script:Fail++ }
function Warn ($m) { Write-Host "  [WARN] $m"   -ForegroundColor Yellow; $script:Warn++ }
function Head ($m) { Write-Host "`n$m" -ForegroundColor Cyan }

if (-not (Test-Path $Pack)) { throw "pack not found: $Pack" }
$zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path $Pack).Path)
$names = @($zip.Entries | ForEach-Object { $_.FullName })

function Read-Entry ($n) {
    $e = $zip.Entries | Where-Object { $_.FullName -eq $n }
    if (-not $e) { return $null }
    $sr = New-Object System.IO.StreamReader($e.Open())
    try { return $sr.ReadToEnd() } finally { $sr.Close() }
}

Write-Host "pack: $Pack  ($(([math]::Round((Get-Item $Pack).Length / 1KB, 1))) KB, $($names.Count) entries)"

# --- 1. pack.mcmeta ---
Head 'pack.mcmeta'
$mc = Read-Entry 'pack.mcmeta'
if (-not $mc) { Bad 'pack.mcmeta missing' }
else {
    try {
        $j = $mc | ConvertFrom-Json
        if ($j.pack.pack_format) { Ok "pack_format = $($j.pack.pack_format)" } else { Bad 'pack_format missing' }
        # 1.21.9+ reads min/max_format; older clients ignore them. Keep them consistent.
        if ($null -ne $j.pack.min_format) {
            if ($j.pack.min_format[0] -eq $j.pack.pack_format) { Ok 'min_format matches pack_format' }
            else { Warn "min_format $($j.pack.min_format[0]) != pack_format $($j.pack.pack_format)" }
        }
    } catch { Bad "pack.mcmeta is not valid JSON: $($_.Exception.Message)" }
}

# --- 2. note_block blockstate must cover EVERY instrument (else vanilla blocks go invisible) ---
Head 'note_block blockstate coverage'
$bs = Read-Entry 'assets/minecraft/blockstates/note_block.json'
if (-not $bs) { Ok 'no note_block override (no custom blocks defined)' }
else {
    try {
        $j = $bs | ConvertFrom-Json
        $variants = @($j.variants.PSObject.Properties.Name)
        Ok "$($variants.Count) variants"

        $instruments = @{
            harp = 'PIANO'; basedrum = 'BASS_DRUM'; snare = 'SNARE_DRUM'; hat = 'STICKS'
            bass = 'BASS_GUITAR'; flute = 'FLUTE'; bell = 'BELL'; guitar = 'GUITAR'
            chime = 'CHIME'; xylophone = 'XYLOPHONE'; iron_xylophone = 'IRON_XYLOPHONE'
            cow_bell = 'COW_BELL'; didgeridoo = 'DIDGERIDOO'; bit = 'BIT'; banjo = 'BANJO'
            pling = 'PLING'; zombie = 'ZOMBIE'; skeleton = 'SKELETON'; creeper = 'CREEPER'
            dragon = 'DRAGON'; wither_skeleton = 'WITHER_SKELETON'; piglin = 'PIGLIN'
            custom_head = 'CUSTOM_HEAD'
        }
        $expected = $instruments.Count * 25 * 2
        if ($variants.Count -eq $expected) { Ok "variant count == $expected (23 instruments x 25 notes x 2 powered)" }
        else { Bad "variant count $($variants.Count) != $expected - missing states render as MISSING MODEL (invisible blocks)" }

        $missing = @()
        foreach ($inst in $instruments.Keys) {
            if (-not ($variants -contains "instrument=$inst,note=0,powered=false")) { $missing += $inst }
        }
        if ($missing.Count -eq 0) { Ok 'all 23 instrument names present' }
        else { Bad "instrument names missing: $($missing -join ', ')" }

        # the vanilla default noteblock must never fall through to a missing model
        if ($variants -contains 'instrument=harp,note=0,powered=false') { Ok 'vanilla default (harp, note 0, unpowered) covered' }
        else { Bad 'vanilla default noteblock state NOT covered -> plain note blocks turn invisible' }

        # Vanilla models (minecraft:block/note_block) live in the client and are deliberately NOT
        # shipped. Only the custom cblock_* models must exist inside the pack.
        $dangling = @()
        foreach ($v in $j.variants.PSObject.Properties) {
            $m = ([string]$v.Value.model) -replace '^minecraft:', ''
            if ($m -like 'block/cblock_*' -and -not ($names -contains "assets/minecraft/models/$m.json")) { $dangling += $m }
        }
        if ($dangling.Count -eq 0) { Ok 'every custom cblock_* variant model exists in pack' }
        else { Bad "variant points at missing model: $(($dangling | Select-Object -Unique) -join ', ')" }
    } catch { Bad "note_block.json is not valid JSON: $($_.Exception.Message)" }
}

# --- 3. font/default.json REPLACES vanilla; vanilla references must survive ---
Head 'font/default.json (vanilla glyph preservation)'
$font = Read-Entry 'assets/minecraft/font/default.json'
if (-not $font) { Ok 'no font override (no ranks/emojis defined)' }
else {
    try {
        $j = $font | ConvertFrom-Json
        $refs = @($j.providers | Where-Object { $_.type -eq 'reference' } | ForEach-Object { $_.id })
        foreach ($need in @('minecraft:include/space', 'minecraft:include/default', 'minecraft:include/unifont')) {
            if ($refs -contains $need) { Ok "keeps $need" }
            else { Bad "MISSING $need - this file replaces vanilla, so ordinary text will render as tofu boxes" }
        }
        $bitmaps = @($j.providers | Where-Object { $_.type -eq 'bitmap' })
        Ok "$($bitmaps.Count) custom bitmap provider(s)"
        foreach ($p in $bitmaps) {
            $f = ([string]$p.file) -replace '^minecraft:', ''
            if (-not ($names -contains "assets/minecraft/textures/$f")) { Bad "font provider file missing: $f" }
            if ($null -eq $p.height) { Warn "provider for $f has no 'height' - PNG height must equal 'ascent'" }
        }
    } catch { Bad "font/default.json is not valid JSON: $($_.Exception.Message)" }
}

# --- 4. config -> pack consistency ---
if ($ConfigDir -and (Test-Path $ConfigDir)) {
    Head "config vs pack ($ConfigDir)"
    $texDir = Join-Path $ConfigDir 'textures'

    $items = Join-Path $ConfigDir 'items.yml'
    if (Test-Path $items) {
        # entries live at 2-space indent under `items:`; their properties (base/name/lore/texture)
        # sit at 4 spaces, so only 2-space keys are item names.
        $inSection = $false
        foreach ($line in Get-Content $items) {
            if ($line -match '^\s*items:\s*$') { $inSection = $true; continue }
            if ($inSection -and $line -match '^\s{2}(\S+):\s*$') {
                $key = $Matches[1]
                if ($names -contains "assets/minecraft/textures/item/$key.png") { Ok "item $key texture packed" }
                elseif ((Test-Path (Join-Path $texDir "$key.png"))) { Bad "item $key texture exists on disk but is NOT in pack - run /ci reload" }
                else { Bad "item $key texture missing: $texDir\$key.png" }
            }
        }
    }

    foreach ($pair in @(@('emojis.yml', 'emojis', 'emoji'), @('ranks.yml', 'ranks', 'rank'))) {
        $file = Join-Path $ConfigDir $pair[0]
        if (-not (Test-Path $file)) { continue }
        foreach ($line in Get-Content $file) {
            if ($line -match '^\s{2}(\S+):\s*$') {
                $key = $Matches[1]
                $inPack = $names -contains "assets/minecraft/textures/font/$($pair[2])_$key.png"
                if ($inPack) { Ok "$($pair[2]) $key packed" }
                else { Bad "$($pair[2]) $key texture not in pack - run /ci reload" }
            }
        }
    }
}
elseif ($ConfigDir) { Warn "config dir not found: $ConfigDir" }

$zip.Dispose()

Head 'result'
if ($script:Fail -eq 0) {
    Write-Host "  PASS - $script:Warn warning(s)" -ForegroundColor Green
    exit 0
} else {
    Write-Host "  $script:Fail FAILURE(S), $script:Warn warning(s)" -ForegroundColor Red
    exit 1
}