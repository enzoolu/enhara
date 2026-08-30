param(
  [Parameter(Mandatory = $true)][string]$VehicleId,
  [string]$ApiUrl = 'http://localhost:8080'
)

Write-Host 'Pressione Ctrl+C para encerrar o stream.'
& curl.exe -N -H 'Accept: text/event-stream' "$($ApiUrl.TrimEnd('/'))/api/vehicles/$VehicleId/events"
