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

# --- 2. blockstates/note_block.json must NOT be overridden ---
# Vanilla ships one "" variant with no instrument keys, so any instrument-keyed override can only
# delete the model. Custom blocks ship as an ITEM model instead (items/note_block.json select).
Head 'note_block blockstate must stay vanilla'
$bs = Read-Entry 'assets/minecraft/blockstates/note_block.json'
if (-not $bs) { Ok 'no note_block blockstate override - vanilla model preserved' }
else { Bad 'note_block.json is overridden; vanilla noteblocks will lose their model' }

$nbItem = Read-Entry 'assets/minecraft/items/note_block.json'
if ($nbItem) {
    try {
        $j = $nbItem | ConvertFrom-Json
        if ($j.model.type -eq 'minecraft:select') { Ok "items/note_block.json selects on $($j.model.property)" }
        else { Warn "items/note_block.json model.type is $($j.model.type), expected minecraft:select" }
    } catch { Bad "items/note_block.json is not valid JSON: $($_.Exception.Message)" }
}

# every cblock_* model/texture referenced anywhere in the pack must exist inside the pack
$dangling = @()
foreach ($n in $names) {
    if ($n -notmatch 'assets/minecraft/models/block/(cblock_.+)\.json$') { continue }
    $id = $Matches[1]
    if (-not ($names -contains "assets/minecraft/textures/block/$id.png")) { $dangling += $id }
    if (-not ($names -contains "assets/minecraft/models/item/$($id -replace '^cblock_', '').json")) { $dangling += "$id (item model)" }
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