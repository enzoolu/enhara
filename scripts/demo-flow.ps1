[CmdletBinding()]
param(
  [string]$ApiUrl = 'http://127.0.0.1:8080',
  [int]$TickIntervalMilliseconds = 650
)

$ErrorActionPreference = 'Stop'
$baseUrl = $ApiUrl.TrimEnd('/')
$vehicles = @(Invoke-RestMethod -Uri "$baseUrl/api/vehicles")
if ($vehicles.Count -eq 0) {
  throw 'Nenhum veiculo disponivel. Execute reset-demo.cmd primeiro.'
}

$vehicle = $vehicles[0]
$vehicleId = $vehicle.id
Write-Host "Veiculo: $($vehicle.name) ($vehicleId)"

# Interrompe qualquer execucao anterior e remove alertas abertos para tornar a prova repetivel.
Invoke-RestMethod -Method Post -Uri "$baseUrl/api/vehicles/$vehicleId/simulation/stop" | Out-Null
$oldAlerts = @(@((Invoke-RestMethod -Uri "$baseUrl/api/vehicles/$vehicleId/alerts?active=true")) |
  Where-Object { $null -ne $_ -and $null -ne $_.id })
foreach ($alert in $oldAlerts) {
  Invoke-RestMethod -Method Patch -Uri "$baseUrl/api/alerts/$($alert.id)/acknowledge" | Out-Null
}

Invoke-RestMethod -Method Post -Uri "$baseUrl/api/vehicles/$vehicleId/simulation/scenario/NORMAL" | Out-Null
Invoke-RestMethod -Method Post -Uri "$baseUrl/api/vehicles/$vehicleId/simulation/start" | Out-Null
1..2 | ForEach-Object {
  Invoke-RestMethod -Method Post -Uri "$baseUrl/api/vehicles/$vehicleId/simulation/tick" | Out-Null
  Start-Sleep -Milliseconds $TickIntervalMilliseconds
}

$normal = Invoke-RestMethod -Uri "$baseUrl/api/vehicles/$vehicleId/telemetry/latest"
if ($normal.engineTempC -ge 100 -or $normal.batteryVoltage -lt 12.4) {
  throw "Cenario NORMAL fora da faixa esperada: $($normal.engineTempC) C / $($normal.batteryVoltage) V"
}
Write-Host "NORMAL validado: $($normal.engineTempC) C / $($normal.batteryVoltage) V"

Invoke-RestMethod -Method Post -Uri "$baseUrl/api/vehicles/$vehicleId/simulation/scenario/OVERHEAT" | Out-Null
$startedAt = [DateTimeOffset]::UtcNow
$overheatTicks = 0
$alerts = @()
while ($overheatTicks -lt 12 -and $alerts.Count -eq 0) {
  Invoke-RestMethod -Method Post -Uri "$baseUrl/api/vehicles/$vehicleId/simulation/tick" | Out-Null
  $overheatTicks++
  Start-Sleep -Milliseconds $TickIntervalMilliseconds
  $alerts = @(@((Invoke-RestMethod -Uri "$baseUrl/api/vehicles/$vehicleId/alerts?active=true")) |
    Where-Object { $null -ne $_ -and $null -ne $_.id })
}

$elapsedSeconds = [Math]::Round(([DateTimeOffset]::UtcNow - $startedAt).TotalSeconds, 1)
$latest = Invoke-RestMethod -Uri "$baseUrl/api/vehicles/$vehicleId/telemetry/latest"
$diagnostics = @(@((Invoke-RestMethod -Uri "$baseUrl/api/vehicles/$vehicleId/diagnostics?activeOnly=true")) |
  Where-Object { $null -ne $_ -and $null -ne $_.id })
$health = Invoke-RestMethod -Uri "$baseUrl/api/vehicles/$vehicleId/health"
Invoke-RestMethod -Method Post -Uri "$baseUrl/api/vehicles/$vehicleId/simulation/stop" | Out-Null
$trips = @(@((Invoke-RestMethod -Uri "$baseUrl/api/vehicles/$vehicleId/trips?limit=1")) |
  Where-Object { $null -ne $_ -and $null -ne $_.id })

$overheatAlert = @($alerts | Where-Object { $_.type -eq 'ENGINE_OVERHEAT' -and $_.severity -eq 'CRITICAL' })
$overheatDiagnostic = @($diagnostics | Where-Object { $_.code -eq 'ENGINE_TEMPERATURE_HIGH' })
if ($overheatAlert.Count -ne 1) { throw "Esperado 1 alerta critico ENGINE_OVERHEAT; encontrados $($overheatAlert.Count)." }
if ($overheatDiagnostic.Count -ne 1) { throw "Esperado 1 diagnostico ENGINE_TEMPERATURE_HIGH; encontrados $($overheatDiagnostic.Count)." }
if ($health.status -ne 'CRITICAL') { throw "Saude esperada CRITICAL; recebida $($health.status)." }
if ($trips.Count -ne 1 -or $null -eq $trips[0].endedAt) { throw 'A viagem da demonstracao nao foi finalizada.' }

Write-Host ''
Write-Host 'DEMO FALLBACK APROVADA'
Write-Host "  Temperatura final: $($latest.engineTempC) C"
Write-Host "  Tempo ate o alerta: $elapsedSeconds s ($overheatTicks ticks)"
Write-Host "  Alerta: $($overheatAlert[0].type) / $($overheatAlert[0].severity)"
Write-Host "  Diagnostico: $($overheatDiagnostic[0].code)"
Write-Host "  Saude: $($health.status) ($($health.score)/100)"
Write-Host "  Viagem: $($trips[0].distanceKm) km / score experimental $($trips[0].drivingScore)"
