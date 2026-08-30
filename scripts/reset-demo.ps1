[CmdletBinding()]
param(
  [switch]$Build
)

$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'stop-demo.ps1')
& (Join-Path $PSScriptRoot 'start-demo.ps1') -Build:$Build

$health = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/actuator/health'
$vehicles = @(Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/vehicles')
if ($health.status -ne 'UP' -or $vehicles.Count -eq 0) {
  throw 'O reset terminou sem health UP ou sem veiculo disponivel.'
}

Write-Host 'RESET concluido: banco H2 limpo, veiculo disponivel e cenario NORMAL ativo.'
