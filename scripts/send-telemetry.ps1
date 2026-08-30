param(
  [Parameter(Mandatory = $true)][string]$VehicleId,
  [string]$ApiUrl = 'http://localhost:8080'
)

$payload = @{
  vehicleId = $VehicleId
  samples = @(
    @{ speedKph = 42; rpm = 2100; engineTempC = 91; engineLoadPercent = 42; throttlePositionPercent = 28; batteryVoltage = 13.8; fuelLevelPercent = 68; source = 'API' },
    @{ speedKph = 48; rpm = 2400; engineTempC = 109; engineLoadPercent = 71; throttlePositionPercent = 46; batteryVoltage = 13.7; fuelLevelPercent = 67.9; source = 'API' }
  )
} | ConvertTo-Json -Depth 5

Invoke-RestMethod -Method Post -Uri "$($ApiUrl.TrimEnd('/'))/api/telemetry/batches" -ContentType 'application/json' -Body $payload
