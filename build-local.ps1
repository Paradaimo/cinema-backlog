# One-button build: gradle + sdk from downloaded zips -> assembleDebug
# Usage: powershell -ExecutionPolicy Bypass -File build-local.ps1 [-SkipSdk]
param([switch]$SkipSdk)

$ErrorActionPreference = 'Stop'
$root     = 'C:\Users\NMSoldatov\Documents\CB'
$work     = 'C:\Users\NMSoldatov\AppData\Local\Temp\opencode'
$sdkDir   = "$work\android-sdk"
$sdkDl    = "$work\sdk-dl"
$dl       = "C:\Users\NMSoldatov\Downloads"
$gradleRoot = "$work\gradle-dist"
$jdk      = 'C:\Users\NMSoldatov\Documents\GachaCount\.android-tools\jdk\jdk-17.0.20.1+1'
$gradleSha = '7988ed071b2a07900e2ec715fca15c6ab72bce6db433af301fa7fa7e9407bd24'

function Find-File($names, $dirs) {
  foreach ($d in $dirs) {
    foreach ($n in $names) {
      $p = Join-Path $d $n
      if (Test-Path $p) { return $p }
    }
  }
  return $null
}

function Expand-ZipTo($zip, $destDir) {
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  if (-not (Test-Path $destDir)) { New-Item -ItemType Directory -Force -Path $destDir | Out-Null }
  [System.IO.Compression.ZipFile]::ExtractToDirectory($zip, $destDir)
}

Write-Output '=== 1. Gradle ==='
$gz = Find-File @('gradle-8.14.5-bin.zip') @($dl, $sdkDl)
if (-not $gz) { throw 'gradle-8.14.5-bin.zip not found in Downloads or sdk-dl' }
Write-Output ("zip: " + $gz)

$sha = (Get-FileHash $gz -Algorithm SHA256).Hash.ToLower()
if ($sha -ne $gradleSha) { throw ("SHA256 mismatch: " + $sha) }
Write-Output 'sha256 OK'

$gdir = "$gradleRoot\gradle-8.14.5"
if (-not (Test-Path "$gdir\bin\gradle.bat")) {
  if (Test-Path $gradleRoot) { Remove-Item $gradleRoot -Recurse -Force }
  Expand-ZipTo $gz $gradleRoot
}
if (-not (Test-Path "$gdir\bin\gradle.bat")) { throw 'gradle.bat missing after unpack' }
Write-Output ("gradle ready: " + $gdir)

if (-not $SkipSdk) {
  Write-Output '=== 2. SDK platform ==='
  $pz = Find-File @('platform-36_r02.zip', 'platform-36.zip') @($dl, $sdkDl)
  if (-not $pz) { throw 'platform-36 zip not found' }
  Write-Output ("zip: " + $pz)

  $pOk = Test-Path "$sdkDir\platforms\android-36\android.jar"
  if (-not $pOk) {
    $tmp = "$work\p36-extract"
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
    Expand-ZipTo $pz $tmp
    # find the folder that contains android.jar
    $aj = Get-ChildItem $tmp -Recurse -Filter android.jar | Select-Object -First 1
    if (-not $aj) { throw 'android.jar not found in platform zip' }
    $srcRoot = $aj.Directory.FullName
    New-Item -ItemType Directory -Force -Path "$sdkDir\platforms\android-36" | Out-Null
    Copy-Item "$srcRoot\*" "$sdkDir\platforms\android-36\" -Recurse -Force
    Remove-Item $tmp -Recurse -Force
  }
  Write-Output ("platform ok: " + (Test-Path "$sdkDir\platforms\android-36\android.jar"))

  Write-Output '=== 3. Build tools ==='
  $bz = Find-File @('build-tools_r36_windows.zip', 'build-tools-36.zip') @($dl, $sdkDl)
  if (-not $bz) { throw 'build-tools zip not found' }
  Write-Output ("zip: " + $bz)

  $bOk = Test-Path "$sdkDir\build-tools\36.0.0\aapt2.exe"
  if (-not $bOk) {
    $tmp = "$work\bt36-extract"
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
    Expand-ZipTo $bz $tmp
    $a2 = Get-ChildItem $tmp -Recurse -Filter aapt2.exe | Select-Object -First 1
    if (-not $a2) { throw 'aapt2.exe not found in build-tools zip' }
    $srcRoot = $a2.Directory.FullName
    New-Item -ItemType Directory -Force -Path "$sdkDir\build-tools\36.0.0" | Out-Null
    Copy-Item "$srcRoot\*" "$sdkDir\build-tools\36.0.0\" -Recurse -Force
    Remove-Item $tmp -Recurse -Force
  }
  Write-Output ("build-tools ok: " + (Test-Path "$sdkDir\build-tools\36.0.0\aapt2.exe"))
}

Write-Output '=== 4. Licenses ==='
New-Item -ItemType Directory -Force -Path "$sdkDir\licenses" | Out-Null
Set-Content -Path "$sdkDir\licenses\android-sdk-license" -Value "24333f8a63b6825ea9c5514f83c2829b004d1fee`n8933bad161af4178b1185d1a37fbf41ea5269c55`nd56f5187479451eabf01fb78af6dfcb131a6481e" -Encoding ASCII
Set-Content -Path "$sdkDir\licenses\android-sdk-preview-license" -Value "84831b9409646a918e30573bab4c9c91346d8abd" -Encoding ASCII

Write-Output '=== 5. local.properties ==='
Set-Content -Path "$root\local.properties" -Value ("sdk.dir=C\:\\Users\\NMSoldatov\\AppData\\Local\\Temp\\opencode\\android-sdk`nsupabase.url=`nsupabase.anonKey=`ntmdb.apiKey=") -Encoding ASCII

Write-Output '=== 6. gradle assembleDebug ==='
$env:JAVA_HOME = $jdk
$env:GRADLE_OPTS = '-Dfile.encoding=UTF-8'
& "$gdir\bin\gradle.bat" -p $root :app:assembleDebug --no-daemon 2>&1 | Tee-Object -FilePath "$work\build.log"
Write-Output ("gradle exit: " + $LASTEXITCODE)

$apk = Get-ChildItem "$root\app\build\outputs\apk\debug\*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1
if ($apk) {
  Write-Output ("APK: " + $apk.FullName + " (" + [math]::Round($apk.Length/1MB,1) + " MB)")
} else {
  Write-Output 'APK not produced - see build.log'
}
