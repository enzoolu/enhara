param(
  [string]$ApiUrl = 'http://localhost:8080'
)

$ErrorActionPreference = 'Stop'
$baseUrl = $ApiUrl.TrimEnd('/')
$vehicles = Invoke-RestMethod -Uri "$baseUrl/api/vehicles"
if (-not $vehicles -or $vehicles.Count -eq 0) {
  throw 'Nenhum veículo disponível. Inicie o backend com o perfil demo.'
}

$vehicleId = @($vehicles)[0].id
Write-Host "Veículo: $(@($vehicles)[0].name) ($vehicleId)"

Invoke-RestMethod -Method Post -Uri "$baseUrl/api/vehicles/$vehicleId/simulation/scenario/NORMAL" | Out-Null
1..2 | ForEach-Object {
  Invoke-RestMethod -Method Post -Uri "$baseUrl/api/vehicles/$vehicleId/simulation/tick" | Out-Null
}
Write-Host 'Cenário NORMAL persistido.'

Invoke-RestMethod -Method Post -Uri "$baseUrl/api/vehicles/$vehicleId/simulation/scenario/OVERHEAT" | Out-Null
1..7 | ForEach-Object {
  Invoke-RestMethod -Method Post -Uri "$baseUrl/api/vehicles/$vehicleId/simulation/tick" | Out-Null
}

$latest = Invoke-RestMethod -Uri "$baseUrl/api/vehicles/$vehicleId/telemetry/latest"
$alerts = Invoke-RestMethod -Uri "$baseUrl/api/vehicles/$vehicleId/alerts?active=true"
$diagnostics = Invoke-RestMethod -Uri "$baseUrl/api/vehicles/$vehicleId/diagnostics?activeOnly=true"

Write-Host "Temperatura final: $($latest.engineTempC) °C"
Write-Host "Alertas abertos: $(@($alerts).Count)"
Write-Host "Diagnósticos ativos: $(@($diagnostics).Count)"
@($alerts) | Select-Object type, severity, title, status | Format-Table
