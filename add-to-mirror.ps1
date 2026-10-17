# Fetches artifacts from dl.google.com into google-mirror via SSO proxy.
# Usage: add-to-mirror.ps1 -GA "com.android:signflinger" -V "8.13.2" [-Kinds pom,module,jar]
param(
  [Parameter(Mandatory=$true)][string]$GA,
  [Parameter(Mandatory=$true)][string]$V,
  [string[]]$Kinds = @('pom','module','jar')
)
$ErrorActionPreference = 'Continue'
[System.Net.WebRequest]::DefaultWebProxy.Credentials = [System.Net.CredentialCache]::DefaultCredentials

$mirror = 'C:\Users\NMSoldatov\Documents\CB\google-mirror'
$repo = 'https://dl.google.com/dl/android/maven2'
$g, $a = $GA -split ':', 2
$gpath = $g -replace '\.', '/'
$dir = Join-Path $mirror ($gpath -replace '/', '\')
$dir = Join-Path $dir $a; $dir = Join-Path $dir $V
New-Item -ItemType Directory -Force -Path $dir | Out-Null

foreach ($k in $Kinds) {
  $fname = "$a-$V.$k"
  $dest = Join-Path $dir $fname
  if (Test-Path $dest) { Write-Output "skip (exists): $fname"; continue }
  $url = "$repo/$gpath/$a/$V/$fname"
  try {
    Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 300 -OutFile $dest
    $sz = (Get-Item $dest).Length
    Write-Output ("OK: $fname ($sz bytes)")
  } catch {
    Write-Output ("FAIL: $fname :: " + $_.Exception.Message)
    if (Test-Path $dest) { Remove-Item $dest -Force }
  }
}
