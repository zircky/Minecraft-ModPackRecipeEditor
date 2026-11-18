[CmdletBinding()]
param(
    [string[]] $Suites = @('test', 'integrationTest'),
    [string] $Destination = 'build/reports/tests/summary.json'
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
$rows = @()
foreach ($module in Get-ChildItem -LiteralPath $repositoryRoot -Directory) {
    if (-not (Test-Path -LiteralPath (Join-Path $module.FullName 'build.gradle.kts'))) { continue }
    foreach ($suite in $Suites) {
        if ($suite -notin @('test', 'integrationTest', 'externalTest', 'performanceTest')) {
            throw "Unknown test suite: $suite"
        }
        $results = Join-Path $module.FullName "build/test-results/$suite"
        if (-not (Test-Path -LiteralPath $results)) { continue }
        $tests = 0; $failures = 0; $errors = 0; $skipped = 0
        foreach ($file in Get-ChildItem -LiteralPath $results -Filter 'TEST-*.xml') {
            $readerSettings = [System.Xml.XmlReaderSettings]::new()
            $readerSettings.DtdProcessing = [System.Xml.DtdProcessing]::Prohibit
            $readerSettings.XmlResolver = $null
            $reader = [System.Xml.XmlReader]::Create($file.FullName, $readerSettings)
            try {
                $document = [System.Xml.XmlDocument]::new()
                $document.XmlResolver = $null
                $document.Load($reader)
                $tests += [int] $document.testsuite.tests
                $failures += [int] $document.testsuite.failures
                $errors += [int] $document.testsuite.errors
                $skipped += [int] $document.testsuite.skipped
            } finally { $reader.Dispose() }
        }
        if ($tests -gt 0) {
            $rows += [pscustomobject]@{
                module = $module.Name; suite = $suite; tests = $tests
                passed = $tests - $failures - $errors - $skipped
                failures = $failures; errors = $errors; skipped = $skipped
            }
        }
    }
}
if ($rows.Count -eq 0) { throw 'No JUnit XML found. Run the selected Gradle suites first.' }
$totals = [ordered]@{}
foreach ($key in @('tests', 'passed', 'failures', 'errors', 'skipped')) {
    $totals[$key] = [int] ($rows | Measure-Object -Property $key -Sum).Sum
}
$report = [ordered]@{
    generatedAtUtc = [DateTime]::UtcNow.ToString('o')
    suites = $Suites
    note = 'Counts existing JUnit XML only. Use clean build for a fresh unit/integration report. Excluded suites are not counted as passed or skipped. This is not a runtime, performance, or coverage claim.'
    totals = $totals
    modules = $rows
}
$outputFile = if ([System.IO.Path]::IsPathRooted($Destination)) { $Destination } else { Join-Path $repositoryRoot $Destination }
[System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($outputFile)) | Out-Null
[System.IO.File]::WriteAllText($outputFile, ($report | ConvertTo-Json -Depth 6), [System.Text.UTF8Encoding]::new($false))
$rows | Format-Table
$totals | ConvertTo-Json -Compress
if ($totals.failures -gt 0 -or $totals.errors -gt 0) { exit 1 }
