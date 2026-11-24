param([string]$JavaHome = $env:JAVA_HOME)

$ErrorActionPreference = 'Stop'
$checkRoot = Split-Path -Parent $PSScriptRoot
$checkOutput = Join-Path $checkRoot 'build/static-mesh-upload-gl-check'
$checkClasses = Join-Path $checkOutput 'classes'
New-Item -ItemType Directory -Path $checkClasses -Force | Out-Null
$checkClasspath = @(
    (Join-Path $checkRoot 'build/classes/java/main'),
    (Join-Path $checkRoot 'build/resources/main')
) + @(Get-Content -LiteralPath (Join-Path $checkRoot 'build/classpath/runClient_minecraftClasspath.txt') | Where-Object {
    $_ -notmatch '-sources\.jar$|-javadoc\.jar$|-natives-windows-(x86|arm64)\.jar$'
})
$checkClasspath = ($checkClasspath -join [System.IO.Path]::PathSeparator).Replace('\', '/')
$checkJava = if ($JavaHome) { Join-Path $JavaHome 'bin/java.exe' } else { 'java' }
$checkJavac = if ($JavaHome) { Join-Path $JavaHome 'bin/javac.exe' } else { 'javac' }
$encoding = [System.Text.UTF8Encoding]::new($false)
$compilerArguments = Join-Path $checkOutput 'javac.args'
[System.IO.File]::WriteAllLines($compilerArguments, @('--release', '17', '-encoding', 'UTF-8', '-proc:none',
    '-cp', ('"' + $checkClasspath + '"'), '-d', ('"' + $checkClasses.Replace('\', '/') + '"'),
    ('"' + (Join-Path $PSScriptRoot 'StaticMeshUploadGlCheck.java').Replace('\', '/') + '"')), $encoding)
& $checkJavac "@$compilerArguments"
if ($LASTEXITCODE -ne 0) { throw 'GL check compilation failed; specify a JDK 17 with -JavaHome.' }
$runtimeArguments = Join-Path $checkOutput 'java.args'
[System.IO.File]::WriteAllLines($runtimeArguments, @('-Xms128m', '-Xmx128m',
    ('"-Dorg.lwjgl.system.SharedLibraryExtractPath=' + (Join-Path $checkOutput 'natives').Replace('\', '/') + '"'),
    '-cp', ('"' + $checkClasses.Replace('\', '/') + ';' + $checkClasspath + '"'),
    'com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu.StaticMeshUploadGlCheck'), $encoding)
& $checkJava "@$runtimeArguments"
if ($LASTEXITCODE -ne 0) { throw 'GL check failed.' }
