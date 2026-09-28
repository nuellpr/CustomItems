# Minimal Minecraft RCON client (no dependencies) for scripted server tests.
#   .\rcon.ps1 -Password test123 -Command "ci give ruby_sword"
#   .\rcon.ps1 -Password test123 -Commands @("list","ci spawn guardian_zombie")
param(
    [string]$Server = '127.0.0.1',
    [int]$Port = 25699,
    [Parameter(Mandatory = $true)][string]$Password,
    [string[]]$Commands
)

$ErrorActionPreference = 'Stop'

function Send-Packet {
    param($Stream, [int]$Id, [int]$Type, [string]$Body)
    $bodyBytes = [System.Text.Encoding]::UTF8.GetBytes($Body)
    $len = 4 + 4 + $bodyBytes.Length + 2
    $ms = New-Object System.IO.MemoryStream
    $bw = New-Object System.IO.BinaryWriter($ms)
    $bw.Write([int]$len)
    $bw.Write([int]$Id)
    $bw.Write([int]$Type)
    $bw.Write($bodyBytes)
    $bw.Write([byte]0)
    $bw.Write([byte]0)
    $bw.Flush()
    $bytes = $ms.ToArray()
    $Stream.Write($bytes, 0, $bytes.Length)
    $Stream.Flush()
    $bw.Dispose(); $ms.Dispose()
}

function Read-Packet {
    param($Stream)
    $lenBuf = New-Object byte[] 4
    $read = 0
    while ($read -lt 4) {
        $n = $Stream.Read($lenBuf, $read, 4 - $read)
        if ($n -le 0) { throw 'connection closed' }
        $read += $n
    }
    $len = [System.BitConverter]::ToInt32($lenBuf, 0)
    $buf = New-Object byte[] $len
    $read = 0
    while ($read -lt $len) {
        $n = $Stream.Read($buf, $read, $len - $read)
        if ($n -le 0) { throw 'connection closed' }
        $read += $n
    }
    $id = [System.BitConverter]::ToInt32($buf, 0)
    $bodyLen = $len - 10
    $body = if ($bodyLen -gt 0) { [System.Text.Encoding]::UTF8.GetString($buf, 8, $bodyLen) } else { '' }
    return [pscustomobject]@{ Id = $id; Body = $body }
}

$client = New-Object System.Net.Sockets.TcpClient
$client.Connect($Server, $Port)
$stream = $client.GetStream()
$stream.ReadTimeout = 10000

# auth
Send-Packet -Stream $stream -Id 1 -Type 3 -Body $Password
$auth = Read-Packet -Stream $stream
if ($auth.Id -eq -1) { throw 'RCON auth failed (wrong password?)' }
Write-Host "[rcon] authenticated" -ForegroundColor DarkGray

$id = 2
foreach ($cmd in $Commands) {
    Send-Packet -Stream $stream -Id $id -Type 2 -Body $cmd
    $resp = Read-Packet -Stream $stream
    Write-Host "`n> $cmd" -ForegroundColor Cyan
    if ([string]::IsNullOrWhiteSpace($resp.Body)) { Write-Host '  (no output)' } else { $resp.Body.TrimEnd() | Write-Host }
    $id++
}

$stream.Close()
$client.Close()