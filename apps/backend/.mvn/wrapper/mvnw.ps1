$ErrorActionPreference = 'Stop'

$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$propertiesPath = Join-Path $PSScriptRoot 'maven-wrapper.properties'
$properties = Get-Content -Raw -Encoding UTF8 $propertiesPath | ConvertFrom-StringData
$distributionUrl = $properties.distributionUrl
if (-not $distributionUrl) { throw "distributionUrl ausente em $propertiesPath" }

$archiveName = $distributionUrl -replace '^.*/', ''
$distributionName = $archiveName -replace '\.[^.]*$', '' -replace '-bin$', ''
$mavenUserHome = if ($env:MAVEN_USER_HOME) { $env:MAVEN_USER_HOME } else { Join-Path $env:USERPROFILE '.m2' }
$hashBytes = [Text.Encoding]::UTF8.GetBytes($distributionUrl)
$distributionHash = ([Security.Cryptography.SHA256]::Create().ComputeHash($hashBytes) |
  ForEach-Object { $_.ToString('x2') }) -join ''
$mavenHome = Join-Path $mavenUserHome "wrapper\dists\$distributionName\$distributionHash"
$mavenCommand = Join-Path $mavenHome 'bin\mvn.cmd'

if (-not (Test-Path -LiteralPath $mavenCommand -PathType Leaf)) {
  $tempRoot = Join-Path ([IO.Path]::GetTempPath()) ("enhara-maven-" + [Guid]::NewGuid().ToString('N'))
  try {
    New-Item -ItemType Directory -Path $tempRoot | Out-Null
    $archivePath = Join-Path $tempRoot $archiveName
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    (New-Object Net.WebClient).DownloadFile($distributionUrl, $archivePath)
    if ($properties.distributionSha256Sum) {
      $actualHash = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()
      if ($actualHash -ne $properties.distributionSha256Sum.ToLowerInvariant()) {
        throw 'Checksum SHA-256 da distribuição Maven não confere.'
      }
    }
    Expand-Archive -LiteralPath $archivePath -DestinationPath $tempRoot
    $extracted = Get-ChildItem -LiteralPath $tempRoot -Directory |
      Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\mvn.cmd') } |
      Select-Object -First 1
    if (-not $extracted) { throw 'A distribuição Maven extraída não contém bin\mvn.cmd.' }
    New-Item -ItemType Directory -Force -Path (Split-Path $mavenHome) | Out-Null
    Move-Item -LiteralPath $extracted.FullName -Destination $mavenHome
  } finally {
    if (Test-Path -LiteralPath $tempRoot) { Remove-Item -LiteralPath $tempRoot -Recurse -Force }
  }
}

Push-Location $projectRoot
try {
  & $mavenCommand @args
  exit $LASTEXITCODE
} finally {
  Pop-Location
}
