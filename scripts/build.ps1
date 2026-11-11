param([string[]]$Tasks = @(':core:test', ':inference:testDebugUnitTest', ':data:testDebugUnitTest', ':app:assembleDebug', ':app:assembleRelease', ':app:lintDebug', ':app:lintRelease', ':data:assembleDebugAndroidTest', ':app:assembleValidationAndroidTest', ':core:exportCatalogue', 'wrapper'))
$ErrorActionPreference = 'Stop'
$rootPath = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $rootPath
$env:JAVA_HOME = (Get-ChildItem -LiteralPath "$rootPath\.tools\jdk" -Directory | Select-Object -First 1).FullName
$env:ANDROID_HOME = "$rootPath\.tools\android-sdk"
$env:GRADLE_USER_HOME = "$rootPath\.tools\gradle-home"
& "$rootPath\.tools\gradle\gradle-8.11.1\bin\gradle.bat" @Tasks --console=plain
exit $LASTEXITCODE
