$ErrorActionPreference = 'Stop'
$required = @('ADAPT_SIGNING_STORE_FILE','ADAPT_SIGNING_STORE_PASSWORD','ADAPT_SIGNING_KEY_ALIAS','ADAPT_SIGNING_KEY_PASSWORD')
foreach ($name in $required) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) { throw "Configure $name outside source control before signing." }
}
if (-not (Test-Path -LiteralPath $env:ADAPT_SIGNING_STORE_FILE -PathType Leaf)) { throw 'The configured team keystore does not exist.' }
& "$PSScriptRoot\build.ps1" -Tasks @(':app:assembleRelease')
exit $LASTEXITCODE
