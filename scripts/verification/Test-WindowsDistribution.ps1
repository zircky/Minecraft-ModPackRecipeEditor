[CmdletBinding()]
param([string] $Directory = (Join-Path $PSScriptRoot '../../build/distributions/windows'))
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $Directory).Path
$properties = Get-Content -LiteralPath (Join-Path $PSScriptRoot '../../gradle.properties')
$version = ($properties | Where-Object { $_ -match '^appVersion=' }) -replace '^appVersion=', ''
$image = Join-Path $root 'portable/MinecraftRecipeEditor'
$launcher = Join-Path $image 'MinecraftRecipeEditor.exe'
$installer = Join-Path $root "installer/MinecraftRecipeEditor-$version-setup.exe"
foreach ($file in @($launcher, $installer)) {
    $stream = [IO.File]::OpenRead($file)
    try { if ($stream.ReadByte() -ne 77 -or $stream.ReadByte() -ne 90) { throw "Not a Windows executable: $file" } }
    finally { $stream.Dispose() }
}
$runtime = Join-Path $image 'runtime/bin/java.exe'
$runtimeVersion = (& $runtime -version 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0 -or $runtimeVersion -notmatch 'version "21\.') { throw 'Bundled runtime must be executable Java 21' }
$config = Get-Content -LiteralPath (Join-Path $image 'app/MinecraftRecipeEditor.cfg') -Raw
if ($config -notmatch 'dev\.recipeeditor\.app\.Launcher') { throw 'Incorrect packaged main class' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$entries = [Collections.Generic.HashSet[string]]::new()
$buildInfo = $null
Get-ChildItem -LiteralPath (Join-Path $image 'app') -Filter '*.jar' | ForEach-Object {
    $zip = [IO.Compression.ZipFile]::OpenRead($_.FullName)
    try {
        foreach ($entry in $zip.Entries) { [void] $entries.Add($entry.FullName) }
        $entry = $zip.GetEntry('dev/recipeeditor/app/build-info.properties')
        if ($null -ne $entry) { $reader=[IO.StreamReader]::new($entry.Open()); try {$buildInfo=$reader.ReadToEnd()} finally {$reader.Dispose()} }
    } finally { $zip.Dispose() }
}
foreach ($required in @('logback.xml','dev/recipeeditor/ui/theme.css','dev/recipeeditor/app/icons/app-32.png')) {
    if (-not $entries.Contains($required)) { throw "Missing packaged resource: $required" }
}
foreach ($native in @('glass.dll','prism_d3d.dll')) {
    if (-not ($entries | Where-Object { $_.EndsWith($native) })) { throw "Missing JavaFX Windows native: $native" }
}
if ($buildInfo -notmatch ('(?m)^desktopVersion=' + [regex]::Escape($version) + '\r?$')) { throw 'Packaged version differs from Gradle' }
if (Test-Path -LiteralPath (Join-Path $image 'mods')) { throw 'User modpack must not be bundled' }
$result = [ordered]@{version=$version; portable=$launcher; installer=$installer; bundledRuntime=$runtimeVersion.Trim(); resourcesVerified=$true; guiLaunchVerified=$false; installerSha256=(Get-FileHash -LiteralPath $installer -Algorithm SHA256).Hash.ToLowerInvariant()}
$result | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $root 'package-verification.json') -Encoding UTF8
Write-Output "VERIFIED package structure, PE files, actual bundled Java 21, JavaFX natives/resources and version $version. GUI/install smoke is separate."
