[CmdletBinding()]
param([Parameter(Mandatory=$true)][string] $Directory)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $Directory).Path
$manifest = Get-Content -LiteralPath (Join-Path $root 'release-manifest.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if ($manifest.platform -ne 'windows-x64' -or $manifest.protocolVersion -ne 1 -or $manifest.projectFormatVersion -ne 1) {
    throw 'Unexpected release environment/format metadata'
}
if ($manifest.releaseStatus -ne 'NOT READY' -or $manifest.signed -ne $false) {
    throw 'This verification script is for the current unsigned evaluation package'
}
if ($manifest.artifacts.Count -ne 4) { throw 'Expected image ZIP, EXE, MSI and Companion JAR' }
$names = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($artifact in $manifest.artifacts) {
    if ([System.IO.Path]::GetFileName($artifact.name) -ne $artifact.name -or $artifact.name.Contains('..') -or -not $names.Add($artifact.name)) {
        throw 'Unsafe or duplicated artifact name'
    }
    $file = Get-Item -LiteralPath (Join-Path $root $artifact.name)
    if ($file.Length -ne $artifact.bytes) { throw "Artifact length mismatch: $($artifact.name)" }
    $actual = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $artifact.sha256 -or $actual -notmatch '^[a-f0-9]{64}$') { throw "Artifact checksum mismatch: $($artifact.name)" }
    Write-Output ("VERIFIED {0} ({1} bytes)" -f $artifact.name, $file.Length)
}
Write-Output ("Verified {0} artifacts for {1}; release status remains {2}. Hash verification does not imply a trusted signature or runtime compatibility." -f $names.Count, $manifest.version, $manifest.releaseStatus)
