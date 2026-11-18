param(
    [Parameter(Mandatory = $true)][string]$Java17Home,
    [string]$Java21Home = $env:JAVA_HOME,
    [string]$GtReferenceDirectory
)
$ErrorActionPreference = 'Stop'
$phase6Workspace = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$phase6ForgeDirectory = Join-Path $phase6Workspace 'minecraft-companions\forge-1.20.1'
$phase6RunId = [guid]::NewGuid().ToString()
$phase6Logs = Join-Path $phase6Workspace 'build\runtime-probes'
[IO.Directory]::CreateDirectory($phase6Logs) | Out-Null
foreach ($phase6JavaDirectory in @($Java17Home, $Java21Home)) {
    if (!$phase6JavaDirectory -or !(Test-Path -LiteralPath (Join-Path $phase6JavaDirectory 'bin\java.exe'))) { throw 'Provide existing JDK 17 and JDK 21 homes.' }
}
$phase6Arguments = "gradlew.bat runGameTestServer -PwithGameTests -PprobeRunId=$phase6RunId"
$phase6RunDirectory = Join-Path $phase6ForgeDirectory 'run-vanilla'
if ($GtReferenceDirectory) {
    $phase6Reference = (Resolve-Path -LiteralPath $GtReferenceDirectory).Path
    if ($phase6Reference -match '[&|<>^%!"\r\n]') { throw 'Use a reference path without command-shell metacharacters.' }
    $phase6Arguments += ' "-PgtReferenceDirectory=' + $phase6Reference + '"'
    $phase6RunDirectory = Join-Path $phase6ForgeDirectory 'run'
} else { $phase6Arguments += ' -PvanillaFixture' }
$phase6PriorJavaHome = $env:JAVA_HOME
try {
    $env:JAVA_HOME = $Java17Home
    $phase6Server = Start-Process -FilePath $env:ComSpec -ArgumentList @('/d', '/c', $phase6Arguments) -WorkingDirectory $phase6ForgeDirectory -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $phase6Logs "$phase6RunId-forge.log") -RedirectStandardError (Join-Path $phase6Logs "$phase6RunId-forge-errors.log")
    $env:JAVA_HOME = $Java21Home
    Push-Location -LiteralPath $phase6Workspace
    try {
        & '.\gradlew.bat' ':companion-client:test' '--tests' '*RuntimeLiveIntegrationTest' "-PruntimeSessionFile=$(Join-Path $phase6RunDirectory 'config\mre_companion\session.json')" "-PruntimeReadyFlag=$(Join-Path $phase6RunDirectory 'runtime-ready.flag')" "-PruntimeProbeMarker=$(Join-Path $phase6RunDirectory 'runtime-probe.ok')" "-PruntimeProbeRunId=$phase6RunId" '-PruntimeExpectServerStop=true'
        if ($LASTEXITCODE -ne 0) { throw "Desktop probe failed. Forge logs: $phase6Logs. The isolated GameTest server stops at its bounded timeout." }
    } finally { Pop-Location }
    if (!$phase6Server.WaitForExit(60000)) { throw 'GameTest process did not stop within 60 seconds after the successful probe.' }
    if ($phase6Server.ExitCode -ne 0) { throw "Forge GameTest failed. Read logs in $phase6Logs" }
    Write-Output "Verified actual Forge runtime. Evidence: companion-client/build/runtime-live-result.txt; logs: $phase6Logs"
} finally { $env:JAVA_HOME = $phase6PriorJavaHome }
