$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$buildDir = Join-Path $root "build\classes"

if (-not (Test-Path (Join-Path $buildDir "BizAnalysisServer.class"))) {
    & (Join-Path $root "build.ps1")
}

if (-not $env:PORT) {
    $env:PORT = "8080"
}

Set-Location $root
java -cp $buildDir BizAnalysisServer
