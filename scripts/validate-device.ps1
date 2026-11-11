$ErrorActionPreference = 'Stop'
& "$PSScriptRoot\build.ps1" -Tasks @(':data:connectedDebugAndroidTest', ':app:connectedValidationAndroidTest')
exit $LASTEXITCODE
