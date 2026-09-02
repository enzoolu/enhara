[CmdletBinding()]
param(
  [switch]$Build
)

$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'stop-demo.ps1')

$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$stateDirectory = [IO.Path]::GetFullPath((Join-Path $workspace '.data\demo'))
$photoDirectory = [IO.Path]::GetFullPath((Join-Path $stateDirectory 'vehicle-photos'))
$statePrefix = $stateDirectory.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
if (-not $photoDirectory.StartsWith($statePrefix, [StringComparison]::OrdinalIgnoreCase)) {
  throw 'Diretorio de fotos da demo fora do diretorio de estado esperado.'
}
foreach ($databaseFile in @('enhara.mv.db', 'enhara.trace.db')) {
  $target = Join-Path $stateDirectory $databaseFile
  if (Test-Path -LiteralPath $target -PathType Leaf) {
    Remove-Item -LiteralPath $target -Force
  }
}
if (Test-Path -LiteralPath $photoDirectory -PathType Container) {
  Remove-Item -LiteralPath $photoDirectory -Recurse -Force
}
& (Join-Path $PSScriptRoot 'start-demo.ps1') -Build:$Build

$health = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/actuator/health'
$vehicles = @(Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/vehicles')
if ($health.status -ne 'UP' -or $vehicles.Count -eq 0) {
  throw 'O reset terminou sem health UP ou sem veiculo disponivel.'
}

Write-Host 'RESET concluido: banco H2 limpo, veiculo disponivel e cenario NORMAL ativo.'
