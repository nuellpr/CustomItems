# Generates the placeholder pixel-art textures CustomItems ships without.
# Style follows the existing rank tags: crisp pixel art, 8px tall, transparency where unused.
#   .\tools\GenerateTextures.ps1 -OutDir ..\textures
param(
    [string]$OutDir = (Join-Path $PSScriptRoot '..\textures')
)

Add-Type -AssemblyName System.Drawing

# Keys must be unique case-insensitively (PowerShell hashtables ignore case).
$Palette = @{
    '.' = $null                 # transparent
    # ruby sword
    'd' = @(122, 11, 20)        # dark ruby outline
    'r' = @(214, 40, 40)        # ruby body
    'l' = @(255, 120, 120)      # ruby highlight
    'y' = @(232, 179, 60)       # light gold guard
    'x' = @(168, 122, 31)       # dark gold guard
    'n' = @(107, 74, 43)        # light wood handle
    # marble block
    'E' = @(128, 124, 118)      # edge
    'M' = @(233, 231, 226)      # marble white
    'i' = @(184, 180, 173)      # light vein
    'j' = @(145, 140, 132)      # dark vein
    # smile emoji
    'O' = @(107, 74, 0)         # face outline
    'A' = @(255, 204, 51)       # amber face
}

$Art = @{}

$Art['ruby_sword.png'] = @(
    '.............dd.'
    '............dlld'
    '...........dlrd.'
    '..........dlrd..'
    '.........dlrd...'
    '........dlrd....'
    '.......dlrd.....'
    '......dlrd......'
    '.....dlrd.......'
    '....dlrd........'
    '...dlrd.........'
    '..xdlrd.........'
    '.xxydd..........'
    'nnny............'
    'nnn.............'
    '.n..............'
)

$Art['marble_block.png'] = @(
    'EEEEEEEEEEEEEEEE'
    'EMMMMMMMMMMMMMME'
    'EMMMjMMMMMMMMMME'
    'EMMjMMMMMMjMMMME'
    'EMMMjMMMMjMMMMME'
    'EMMMMjMMjMMMMMME'
    'EMMjMMjjMMMjMMME'
    'EMjMMMMMMMjMMMME'
    'EMMMMMMMMjMMMMME'
    'EMMjMMMMjMMjMMME'
    'EMjMMMMMMMMMjMME'
    'EMMMjMMMMMMMMMME'
    'EMMMMMjMMMjMMMME'
    'EMMMMMMMMjMMMMME'
    'EMMMMMMMMMMMMMME'
    'EEEEEEEEEEEEEEEE'
)

# Height must equal the emoji's `ascent` (8) or the glyph renders scaled/blurred.
$Art['smile.png'] = @(
    '..OOOO..'
    '.OAAAAO.'
    'OAOAAOAO'
    'OAAAAAAO'
    'OAAAAAAO'
    'OAAAAAAO'
    '.OAOOAO.'
    '..OOOO..'
)

if (-not (Test-Path $OutDir)) { New-Item -ItemType Directory -Path $OutDir -Force | Out-Null }
$OutDir = (Resolve-Path $OutDir).Path

foreach ($name in @($Art.Keys | Sort-Object)) {
    $rows = $Art[$name]
    $h = $rows.Count
    $w = $rows[0].Length
    foreach ($row in $rows) {
        if ($row.Length -ne $w) { throw "$name : row width $($row.Length) != $w" }
    }

    $bmp = New-Object System.Drawing.Bitmap $w, $h, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $h; $y++) {
        for ($x = 0; $x -lt $w; $x++) {
            $ch = [string]$rows[$y][$x]
            if (-not $Palette.ContainsKey($ch)) { throw "$name : unknown pixel char '$ch'" }
            $rgb = $Palette[$ch]
            if ($null -eq $rgb) {
                $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
            } else {
                $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $rgb[0], $rgb[1], $rgb[2]))
            }
        }
    }

    $path = Join-Path $OutDir $name
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()

    "=== $name  ($w x $h) -> $path ==="
    foreach ($row in $rows) { "  $row" }
    ""
}

"generated $($Art.Count) textures in $OutDir"