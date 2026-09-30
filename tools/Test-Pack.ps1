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
            # int on 1.21.9+ clients, [major, minor] accepted too
            $min = if ($j.pack.min_format -is [array]) { $j.pack.min_format[0] } else { $j.pack.min_format }
            if ($min -eq $j.pack.pack_format) { Ok 'min_format matches pack_format' }
            else { Warn "min_format $min != pack_format $($j.pack.pack_format)" }
        }
    } catch { Bad "pack.mcmeta is not valid JSON: $($_.Exception.Message)" }
}

# --- 2. blockstates/note_block.json: custom variants AND the "" fallback ---
# Custom blocks are note blocks keyed by instrument+note, the same mechanism Oraxen/ItemsAdder use.
# The "" variant is the fallback the client resolves when no key matches, so it is what keeps every
# other note block in the world rendering as vanilla. Losing it strips the model from all of them.
Head 'note_block blockstate must map custom blocks and keep the "" fallback'
$bs = Read-Entry 'assets/minecraft/blockstates/note_block.json'
if (-not $bs) { Ok 'no custom blocks in this pack - vanilla note_block.json untouched' }
else {
    # NOTE: this file cannot go through ConvertFrom-Json -- Windows PowerShell 5.1 (the only shell
    # available here) refuses a property whose name is the empty string, and the "" fallback is
    # exactly such a property. The generator emits this file without whitespace, so regex is exact.
    if ($bs -notmatch '^\{"variants":\{.*\}\}$') { Bad 'note_block.json is not a {"variants":{...}} object' }
    if ($bs -match '\{"model":"minecraft:block/note_block"\}') { Ok 'blockstate keeps the "" fallback for non-custom noteblocks' }
    else { Bad 'blockstate has no "" fallback; every vanilla noteblock in the world will lose its model' }

    $m = [regex]::Matches($bs, '"instrument=([^"]+)":\{"model":"([^":]+:block/cblock_[^"]+)"\}')
    if ($m.Count) { Ok "blockstate maps $($m.Count) custom block variants" }
    else { Bad 'blockstate has no instrument= variants; custom blocks will render as vanilla noteblocks' }

    # every variant must be a well-formed instrument=,note=,powered= key pointing at a model we ship
    $seen = @{}
    $keys = [regex]::Matches($bs, '"([^"]*)":\{"model"') | ForEach-Object { $_.Groups[1].Value }
    foreach ($k in $keys) {
        if ($k -eq '') { continue }
        if ($k -notmatch '^instrument=([^,]+),note=(\d+),powered=(true|false)$') { Bad "variant key '$k' is not in instrument=,note=,powered= form"; continue }
        $id = '{0}:{1}' -f $Matches[1], $Matches[2]
        if (-not $seen.ContainsKey($id)) { $seen[$id] = @() }
        $seen[$id] += $Matches[3]
    }
    # both powered states must be present, or a redstone-powered block falls back to the vanilla model
    $unpaired = @($seen.Keys | Where-Object { @($seen[$_] | Sort-Object -Unique).Count -lt 2 })
    if ($unpaired) { Bad "these block states are missing a powered=false or powered=true variant: $($unpaired -join ', ')" }
    elseif ($seen.Count) { Ok "every custom block state covers both powered=false and powered=true ($($seen.Count) states)" }

    # and the model each variant points at has to actually exist in the pack
    foreach ($v in $m) {
        $modelId = [regex]::Match($v.Groups[2].Value, '^([^:]+):block/(.+)$')
        if (-not $modelId.Success) { Bad "variant references invalid model ID '$($v.Groups[2].Value)'"; continue }
        $model = "assets/$($modelId.Groups[1].Value)/models/block/$($modelId.Groups[2].Value).json"
        if ($names -notcontains $model) { Bad "variant '$($v.Groups[1].Value)' references $model, which the pack does not contain" }
    }
}

# Custom item models live in the plugin namespace; vanilla item definitions must stay untouched.
$minecraftItems = @($names | Where-Object { $_ -match '^assets/minecraft/items/.+\.json$' })
if ($minecraftItems.Count -eq 0) { Ok 'no vanilla item model definitions are overridden' }
else { Bad "resource pack overrides vanilla item definitions: $($minecraftItems -join ', ')" }

# every cblock_* block model/texture and its custom item model must exist in the pack
$dangling = @()
foreach ($n in $names) {
    if ($n -notmatch '^assets/([^/]+)/models/block/(cblock_[^/]+)\.json$') { continue }
    $namespace = $Matches[1]
    $id = $Matches[2]
    $key = $id -replace '^cblock_', ''
    if (-not ($names -contains "assets/$namespace/textures/block/$id.png")) { $dangling += $id }
    if (-not ($names -contains "assets/$namespace/items/block/$key.json")) { $dangling += "$id (item definition)" }
    if (-not ($names -contains "assets/$namespace/models/item/block/$key.json")) { $dangling += "$id (item model)" }
}
if ($dangling.Count -eq 0) { Ok 'every cblock_* block model has its texture and item model' }
else { Bad "cblock_* references missing entries: $(($dangling | Select-Object -Unique) -join ', ')" }

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
        $chars = New-Object System.Collections.Generic.List[int]
        foreach ($p in $bitmaps) {
            $f = ([string]$p.file) -replace '^minecraft:', ''
            if (-not ($names -contains "assets/minecraft/textures/$f")) { Bad "font provider file missing: $f" }
            if ($null -eq $p.height) { Warn "provider for $f has no 'height' - PNG height must equal 'ascent'" }
            foreach ($c in @($p.chars)) { $chars.Add([int][char]([string]$c)[0]) }
        }
        # Ranks take U+E000+, emojis U+E100+. Only 256 codepoints exist in each range, so entry 257
        # spills into CJK (U+0F00) and shifts every later glyph. Duplicated codepoints are the same
        # bug seen from the other side: two entries claim one slot and the second one wins.
        if ($chars.Count) {
            $strays = @($chars | Where-Object { $_ -lt 0xE000 -or $_ -gt 0xE1FF } | Select-Object -Unique)
            if ($strays.Count -eq 0) {
                $ranks = @($chars | Where-Object { $_ -ge 0xE000 -and $_ -le 0xE0FF }).Count
                $emojis = @($chars | Where-Object { $_ -ge 0xE100 -and $_ -le 0xE1FF }).Count
                Ok "all $($chars.Count) glyph codepoints inside U+E000-U+E1FF ($ranks rank, $emojis emoji)"
            } else {
                $list = ($strays | ForEach-Object { 'U+{0:X4}' -f $_ }) -join ', '
                Bad "glyph codepoints outside the private use area: $list - more than 256 rank/emoji entries"
            }
            $dupes = @($chars | Group-Object | Where-Object { $_.Count -gt 1 })
            if ($dupes.Count -eq 0) { Ok 'no duplicated glyph codepoints' }
            else {
                $list = ($dupes | ForEach-Object { 'U+{0:X4}x{1}' -f [int]$_.Name, $_.Count }) -join ', '
                Bad "duplicated glyph codepoints: $list - entries overwrite each other"
            }
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
                $definition = @($names | Where-Object { $_ -match "^assets/([^/]+)/items/$([regex]::Escape($key))\.json$" } | Select-Object -First 1)
                if ($definition.Count -gt 0) {
                    $namespace = [regex]::Match($definition[0], '^assets/([^/]+)/items/').Groups[1].Value
                    if ($names -contains "assets/$namespace/textures/item/$key.png") { Ok "item $key model and texture packed" }
                    else { Bad "item $key texture is not in pack - run /ci reload" }
                }
                elseif ((Test-Path (Join-Path $texDir "$key.png"))) { Bad "item $key model/texture exists on disk but is NOT in pack - run /ci reload" }
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
