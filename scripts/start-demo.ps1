[CmdletBinding()]
param(
  [switch]$Build
)

$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$stateDirectory = Join-Path $workspace '.data\demo'
$backendDirectory = Join-Path $workspace 'apps\backend'
$webDirectory = Join-Path $workspace 'apps\web'
$backendJar = Join-Path $backendDirectory 'target\enhara-api-0.0.1-SNAPSHOT.jar'
$backendJarRelative = 'target\enhara-api-0.0.1-SNAPSHOT.jar'
$mavenWrapper = Join-Path $backendDirectory 'mvnw.cmd'
$viteCommand = Join-Path $webDirectory 'node_modules\.bin\vite.cmd'

function Wait-Endpoint {
  param(
    [Parameter(Mandatory = $true)][string]$Uri,
    [int]$TimeoutSeconds = 40
  )

  $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
  do {
    try {
      $response = Invoke-WebRequest -UseBasicParsing -Uri $Uri -TimeoutSec 2
      if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
        return
      }
    } catch {
      Start-Sleep -Milliseconds 500
    }
  } while ([DateTime]::UtcNow -lt $deadline)

  throw "Tempo esgotado aguardando $Uri"
}

function Test-PortInUse {
  param([Parameter(Mandatory = $true)][int]$Port)
  return $null -ne (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}

if ((Test-PortInUse 8080) -or (Test-PortInUse 5173)) {
  try {
    $health = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/actuator/health' -TimeoutSec 2
    Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:5173' -TimeoutSec 2 | Out-Null
    if ($health.status -eq 'UP') {
      Write-Host 'A demonstracao Enhara ja esta disponivel:'
      Write-Host '  Dashboard: http://127.0.0.1:5173'
      Write-Host '  Health:    http://127.0.0.1:8080/actuator/health'
      exit 0
    }
  } catch {
    throw 'As portas 8080 ou 5173 ja estao ocupadas por outro processo. Libere-as antes de iniciar a demo.'
  }
}

if ($Build -or -not (Test-Path -LiteralPath $backendJar -PathType Leaf)) {
  Write-Host 'Gerando o JAR do backend...'
  & $mavenWrapper package '-DskipTests'
  if ($LASTEXITCODE -ne 0) { throw 'Falha ao gerar o backend.' }
}

if (-not (Test-Path -LiteralPath $viteCommand -PathType Leaf)) {
  throw 'Dependencias web ausentes. Execute uma vez: npm ci --prefix apps/web'
}

$javaCommand = (Get-Command java -ErrorAction Stop).Source
New-Item -ItemType Directory -Force -Path $stateDirectory | Out-Null

$backendOut = Join-Path $stateDirectory 'backend.out.log'
$backendErr = Join-Path $stateDirectory 'backend.err.log'
$webOut = Join-Path $stateDirectory 'web.out.log'
$webErr = Join-Path $stateDirectory 'web.err.log'

$backend = Start-Process -FilePath $javaCommand `
  -ArgumentList @('-jar', $backendJarRelative, '--spring.profiles.active=demo') `
  -WorkingDirectory $backendDirectory -WindowStyle Hidden -PassThru `
  -RedirectStandardOutput $backendOut -RedirectStandardError $backendErr
$backend.Id | Set-Content -LiteralPath (Join-Path $stateDirectory 'backend.pid') -Encoding ascii

try {
  Wait-Endpoint -Uri 'http://127.0.0.1:8080/actuator/health'

  $web = Start-Process -FilePath $viteCommand -ArgumentList @('--host', '127.0.0.1') `
    -WorkingDirectory $webDirectory -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput $webOut -RedirectStandardError $webErr
  $web.Id | Set-Content -LiteralPath (Join-Path $stateDirectory 'web.pid') -Encoding ascii
  Wait-Endpoint -Uri 'http://127.0.0.1:5173'

  $vehicles = @(Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/vehicles')
  if ($vehicles.Count -eq 0) { throw 'O backend iniciou, mas nenhum veiculo de demonstracao foi criado.' }
  $vehicleId = $vehicles[0].id
  Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:8080/api/vehicles/$vehicleId/simulation/scenario/NORMAL" | Out-Null
  Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:8080/api/vehicles/$vehicleId/simulation/start" | Out-Null

  Write-Host ''
  Write-Host 'Enhara pronta para a demonstracao em modo local (H2).'
  Write-Host '  Dashboard: http://127.0.0.1:5173'
  Write-Host '  Health:    http://127.0.0.1:8080/actuator/health'
  Write-Host "  Veiculo:   $($vehicles[0].name)"
  Write-Host '  Cenario:   NORMAL (simulacao ativa)'
  Write-Host "  Logs:      $stateDirectory"
} catch {
  & (Join-Path $PSScriptRoot 'stop-demo.ps1')
  throw
}
