[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$stateDirectory = Join-Path $workspace '.data\demo'

function Stop-ProcessTree {
  param([Parameter(Mandatory = $true)][int]$ProcessId)

  $children = @(Get-CimInstance Win32_Process -Filter "ParentProcessId = $ProcessId" -ErrorAction SilentlyContinue)
  foreach ($child in $children) {
    Stop-ProcessTree -ProcessId ([int]$child.ProcessId)
  }
  Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
}

foreach ($name in @('web', 'backend')) {
  $pidFile = Join-Path $stateDirectory "$name.pid"
  if (-not (Test-Path -LiteralPath $pidFile -PathType Leaf)) { continue }

  $processId = 0
  if ([int]::TryParse((Get-Content -LiteralPath $pidFile -Raw).Trim(), [ref]$processId) -and $processId -gt 0) {
    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
    $pidFileInfo = Get-Item -LiteralPath $pidFile
    if ($process -and $process.StartTime -ge $pidFileInfo.LastWriteTime.AddSeconds(-10)) {
      Stop-ProcessTree -ProcessId $processId
      Write-Host "Processo $name encerrado."
    }
  }
  Remove-Item -LiteralPath $pidFile -Force
}

Write-Host 'Demonstracao Enhara encerrada. Os logs foram preservados em .data\demo.'
