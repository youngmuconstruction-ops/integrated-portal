$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$buildDir = Join-Path $root "build\classes"

New-Item -ItemType Directory -Force -Path $buildDir | Out-Null
javac -encoding UTF-8 -d $buildDir (Join-Path $root "src\BizAnalysisServer.java")

Write-Host "Build complete: $buildDir"
