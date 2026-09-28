# Spins up a throwaway Paper server to test CustomItems end-to-end, then tears it down.
# Reuses a Paper jar already on disk so it needs no downloads.
#
#   .\tools\Setup-TestServer.ps1                 # create .mc-test next to the repo
#   .\tools\Setup-TestServer.ps1 -PaperJar <path>
#
# The server is intentionally isolated from any production server: it runs in its own
# directory, with no other plugins, so a plugin failure here cannot touch real worlds.
param(
    [string]$Root = (Join-Path $PSScriptRoot '..\..\.mc-test'),
    [string]$PaperJar,
    [string]$Jar = (Join-Path $PSScriptRoot '..\releases\CustomItems-0.3.0.jar'),
    [string]$SeedFrom,          # optional: copy textures/*.yml from a real server's plugin folder
    [int]$Port = 25698,
    [int]$RconPort = 25699,
    [string]$RconPassword = 'test123'
)

$ErrorActionPreference = 'Stop'

if (-not $PaperJar) {
    $candidates = @(
        'C:\Users\Nuel\Desktop\ARCANIA SERVER\paper-1.21.11-132.jar'
        (Get-ChildItem 'C:\Users\Nuel\Desktop' -Directory -ErrorAction SilentlyContinue |
            ForEach-Object { Join-Path $_.FullName 'paper*.jar' } | Select-Object -First 1)
    ) | Where-Object { $_ -and (Test-Path $_) }
    if (-not $candidates) { throw "no Paper jar found; pass -PaperJar <path>" }
    $PaperJar = $candidates[0]
}
if (-not (Test-Path $PaperJar)) { throw "Paper jar not found: $PaperJar" }
if (-not (Test-Path $Jar)) { throw "plugin jar not found: $Jar (build it first)" }

New-Item -ItemType Directory -Path $Root, (Join-Path $Root 'plugins') -Force | Out-Null
Copy-Item $PaperJar (Join-Path $Root 'paper.jar') -Force
Set-Content (Join-Path $Root 'eula.txt') 'eula=true' -Encoding ASCII

@"
online-mode=false
server-port=$Port
level-name=world
level-type=minecraft\:flat
spawn-protection=0
max-players=5
view-distance=4
simulation-distance=4
motd=CustomItems test
enable-rcon=true
rcon.port=$RconPort
rcon.password=$RconPassword
"@ | Set-Content (Join-Path $Root 'server.properties') -Encoding ASCII

Copy-Item $Jar (Join-Path $Root 'plugins') -Force

$dataDir = Join-Path $Root 'plugins\CustomItems'
New-Item -ItemType Directory -Path (Join-Path $dataDir 'textures'), (Join-Path $dataDir 'sounds') -Force | Out-Null
if ($SeedFrom -and (Test-Path $SeedFrom)) {
    Copy-Item (Join-Path $SeedFrom 'textures\*.png') (Join-Path $dataDir 'textures') -Force -ErrorAction SilentlyContinue
    Copy-Item (Join-Path $SeedFrom '*.yml') $dataDir -Force -ErrorAction SilentlyContinue
    Write-Host "seeded config+textures from $SeedFrom" -ForegroundColor Green
} else {
    Write-Host "no -SeedFrom given: plugin will generate default configs" -ForegroundColor Yellow
}

Write-Host "`ntest server ready: $Root" -ForegroundColor Cyan
Write-Host "  start : java -Xms512M -Xmx1024M -jar paper.jar --nogui   (run from $Root)"
Write-Host "  rcon  : .\tools\rcon.ps1 -Port $RconPort -Password $RconPassword -Commands @('ci reload')"
Write-Host "  stop  : .\tools\rcon.ps1 -Port $RconPort -Password $RconPassword -Commands @('stop')"
Write-Host "  check : .\tools\Test-Pack.ps1 -Pack `"$Root\plugins\CustomItems\pack.zip`" -ConfigDir `"$dataDir`""