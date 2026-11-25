param(
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$Uncapped,
    [switch]$SmokeTick,
    [switch]$GpuPrototypes,
    [switch]$LargeBatches,
    [switch]$CpuIsolation,
    [string]$CpuProfilerAgent,
    [string]$ShaderPack,
    [string]$ShaderPackOptions,
    [switch]$ProductionCompute,
    [switch]$FacingModes,
    [switch]$FacingTwoSided
)

$ErrorActionPreference = 'Stop'
if ($SmokeTick -and ($GpuPrototypes -or $LargeBatches -or $CpuIsolation -or $CpuProfilerAgent)) {
    throw 'Select either -SmokeTick or a GPU benchmark.'
}
if ($CpuIsolation -and $CpuProfilerAgent) { throw 'Select either -CpuIsolation or -CpuProfilerAgent.' }
if (($ShaderPack -or $ProductionCompute) -and ($GpuPrototypes -or $LargeBatches -or $CpuIsolation -or $CpuProfilerAgent)) {
    throw '-ShaderPack and -ProductionCompute select the Embeddium and self-written compute comparison.'
}
if ($ShaderPackOptions -and !$ShaderPack) { throw '-ShaderPackOptions requires -ShaderPack.' }
if ($FacingModes -and ($SmokeTick -or $GpuPrototypes -or $LargeBatches -or $CpuIsolation -or $CpuProfilerAgent)) {
    throw '-FacingModes selects the look-at orientation comparison.'
}
if ($FacingTwoSided -and !$FacingModes) { throw '-FacingTwoSided requires -FacingModes.' }
$pack = if ($ShaderPack) { Get-Item -LiteralPath $ShaderPack }
$repoRoot = Split-Path -Parent $PSScriptRoot
$encoding = [System.Text.UTF8Encoding]::new($false)
$benchmarkRoot = Join-Path $repoRoot 'build/world-particle-benchmark'
$benchmarkRun = Join-Path $benchmarkRoot ('run-' + [Guid]::NewGuid().ToString('N'))
$frameLimit = if ($Uncapped) { 260 } else { 120 }
New-Item -ItemType Directory -Path $benchmarkRun -Force | Out-Null
New-Item -ItemType File -Path (Join-Path $benchmarkRun '.sbm-particle-benchmark') | Out-Null
[System.IO.File]::WriteAllLines((Join-Path $benchmarkRun 'options.txt'), @(
    'version:3465', 'overrideWidth:1280', 'overrideHeight:720', 'fullscreen:false',
    'enableVsync:false', "maxFps:$frameLimit", 'pauseOnLostFocus:false', 'onboardAccessibility:false',
    'renderDistance:6', 'simulationDistance:5', 'renderClouds:"false"', 'tutorialStep:none'
), $encoding)
$previousJavaHome = $env:JAVA_HOME
$gradleArguments = @('--offline', "-PsbmParticleBenchmarkDir=$benchmarkRun")
$facingCases = if ($FacingTwoSided) { if ($ProductionCompute) { 12 } else { 27 } } else { if ($ProductionCompute) { 24 } else { 54 } }
if ($SmokeTick) { $gradleArguments += '-PsbmParticleBenchmarkSmokeTick' }
if ($ProductionCompute) { $gradleArguments += '-PsbmParticleBenchmarkProductionCompute' }
if ($FacingModes) { $gradleArguments += '-PsbmParticleBenchmarkFacingModes' }
if ($FacingTwoSided) { $gradleArguments += '-PsbmParticleBenchmarkFacingTwoSided' }
$expectedCases = if ($pack) {
    $packName = $pack.Name
    $shaderPacksDir = New-Item -ItemType Directory -Path (Join-Path $benchmarkRun 'shaderpacks')
    $configDir = New-Item -ItemType Directory -Path (Join-Path $benchmarkRun 'config')
    Copy-Item -LiteralPath $pack.FullName -Destination (Join-Path $shaderPacksDir.FullName $packName) -Recurse
    if ($ShaderPackOptions) {
        Copy-Item -LiteralPath $ShaderPackOptions -Destination (Join-Path $shaderPacksDir.FullName "$packName.txt")
    }
    [System.IO.File]::WriteAllLines((Join-Path $configDir.FullName 'oculus.properties'), @(
        'enableShaders=true', "shaderPack=$packName"
    ), $encoding)
    $gradleArguments += "-PsbmParticleBenchmarkShaderPack=$packName"
    if ($FacingModes) { $facingCases } elseif ($SmokeTick -and $ProductionCompute) { 14 } elseif ($SmokeTick) { 12 } else { 18 }
} elseif ($FacingModes) {
    $facingCases
} elseif ($ProductionCompute) {
    if ($SmokeTick) { 14 } else { 18 }
} elseif ($SmokeTick) {
    27
} elseif ($CpuProfilerAgent) {
    $agentPath = (Resolve-Path -LiteralPath $CpuProfilerAgent).Path
    $gradleArguments += "-PsbmParticleBenchmarkCpuProfilerAgent=$agentPath"
    24
} elseif ($CpuIsolation) {
    $gradleArguments += '-PsbmParticleBenchmarkCpuIsolation'
    30
} elseif ($LargeBatches) {
    $gradleArguments += '-PsbmParticleBenchmarkLargeBatches'
    48
} elseif ($GpuPrototypes) {
    $gradleArguments += '-PsbmParticleBenchmarkGpuPrototypes'
    48
} else { 24 }
Push-Location $repoRoot
try {
    if ($JavaHome) { $env:JAVA_HOME = $JavaHome }
    & .\gradlew.bat @gradleArguments runClient
    if ($LASTEXITCODE -ne 0) { throw 'The world particle benchmark client exited with an error.' }
    $results = Get-ChildItem -LiteralPath $benchmarkRun -Directory -Filter 'particle-results-*'
    if (!$results -or
        (Get-ChildItem -LiteralPath $results.FullName -File -Filter '*-frames.csv').Count -ne $expectedCases -or
        (Get-ChildItem -LiteralPath $results.FullName -File -Filter '*-ticks.csv').Count -ne $expectedCases) {
        throw "The world particle benchmark did not complete. See $benchmarkRun/logs/latest.log."
    }
    if ($pack -or $ProductionCompute -or $FacingModes) {
        $expectedImages = if ($FacingModes -and $ProductionCompute) { 5 } elseif ($FacingModes) { 10 } elseif ($SmokeTick) { 2 * $expectedCases + 1 } else { 13 }
        if (($pack -and (Import-Csv -LiteralPath (Join-Path $results.FullName 'shader-selection.csv') | Group-Object case).Count -ne $expectedCases) -or
            (Get-ChildItem -LiteralPath $results.FullName -File -Filter 'verify-*.png').Count -ne $expectedImages) {
            throw "The shader benchmark did not capture every shader selection or comparison image in $benchmarkRun."
        }
        if ($SmokeTick -and (Import-Csv -LiteralPath (Join-Path $results.FullName 'flipbook-snapshots.csv')).Count -ne $expectedImages - 1) {
            throw "The shader benchmark did not capture every flipbook UV sample in $benchmarkRun."
        }
    }
    if ($ProductionCompute) {
        $debugLog = Join-Path $benchmarkRun 'logs/debug.log'
        if (!(Select-String -LiteralPath $debugLog -SimpleMatch 'World particle compute submitted its first batch' -Quiet)) {
            throw "The production compute path did not submit a batch in $benchmarkRun."
        }
        if ($SmokeTick -and !(Select-String -LiteralPath $debugLog -SimpleMatch 'World particle compute is replaying 2048 staged particles in order' -Quiet)) {
            throw "The production compute path did not replay the mixed batch in $benchmarkRun."
        }
        if ($FacingModes -and !(Select-String -LiteralPath $debugLog -SimpleMatch 'World particle compute submitted its first batch with LOOKAT' -Quiet)) {
            throw "The production compute path did not submit the mixed-facing batch in $benchmarkRun."
        }
    }
    if ($CpuProfilerAgent) {
        $profile = Join-Path $benchmarkRun 'cpu.jfr'
        if (!(Test-Path -LiteralPath $profile) -or
            !(Test-Path -LiteralPath (Join-Path $results.FullName 'profile-windows.csv'))) {
            throw "The CPU profile or its sample windows are missing in $benchmarkRun."
        }
        Move-Item -LiteralPath $profile -Destination (Join-Path $results.FullName 'cpu.jfr')
    }
    Write-Output $results.FullName
} finally {
    $env:JAVA_HOME = $previousJavaHome
    Pop-Location
}
