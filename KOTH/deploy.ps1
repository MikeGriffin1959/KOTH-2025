<#
.SYNOPSIS
    Build KOTH and deploy it to AWS Elastic Beanstalk (koth-prod).

.DESCRIPTION
    Mirrors the GolferFest / BingmerFestFF deployment flow:
      1. mvn clean package            -> target/KOTH.war
      2. zip KOTH.war + .ebextensions/ -> KOTH-eb-bundle-<ver>.zip (forward-slash entries)
      3. upload bundle to the EB application S3 bucket
      4. create an EB application version
      5. update the koth-prod environment to that version

    Environment properties (DB creds, COMMENTARY_API_KEY) and the HTTPS listener
    are set once, out-of-band, from .secrets\eb-options.json and
    .secrets\eb-https-options.json. This script only ships new code.

.EXAMPLE
    .\deploy.ps1
    .\deploy.ps1 -VersionLabel v3
#>
param(
    [string]$VersionLabel = "v$(Get-Date -Format yyyyMMddHHmmss)"
)

# Continue (not Stop): native tools (mvn on JDK 24, aws cli progress) write to
# stderr, which under 'Stop' PowerShell turns into a terminating error. The
# explicit Test-Path / exit-code checks below catch real failures instead.
$ErrorActionPreference = "Continue"

$aws       = "C:\Program Files\Amazon\AWSCLIV2\aws.exe"
$mvn       = "C:\tools\apache-maven-3.9.9\bin\mvn.cmd"
$root      = $PSScriptRoot
$appName   = "koth"
$envName   = "koth-prod"
$bucket    = "elasticbeanstalk-us-east-1-067015264613"
$region    = "us-east-1"

$war       = Join-Path $root "target\KOTH.war"
$bundle    = Join-Path $root ".secrets\KOTH-eb-bundle-$VersionLabel.zip"
$s3key     = "$appName/KOTH-eb-bundle-$VersionLabel.zip"

Write-Host "==> Building KOTH.war" -ForegroundColor Cyan
& $mvn -f (Join-Path $root "pom.xml") clean package -q -DskipTests
if (-not (Test-Path $war)) { throw "Build failed: $war not found" }

Write-Host "==> Packaging bundle ($VersionLabel): KOTH.war + .ebextensions" -ForegroundColor Cyan
# Windows PowerShell's Compress-Archive writes zip entries with BACKSLASH
# separators, which Elastic Beanstalk does not recognise (it looks for
# ".ebextensions/"). Build the archive by hand with forward-slash entry names.
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$secretsDir = Join-Path $root ".secrets"
if (-not (Test-Path $secretsDir)) { New-Item -ItemType Directory -Path $secretsDir | Out-Null }
if (Test-Path $bundle) { Remove-Item $bundle -Force }
$fs  = [System.IO.File]::Open($bundle, [System.IO.FileMode]::Create)
$zip = New-Object System.IO.Compression.ZipArchive($fs, [System.IO.Compression.ZipArchiveMode]::Create)
function Add-ZipEntry($zip, $srcFile, $entryName) {
    $entry = $zip.CreateEntry($entryName, [System.IO.Compression.CompressionLevel]::Optimal)
    $es = $entry.Open()
    $bytes = [System.IO.File]::ReadAllBytes($srcFile)
    $es.Write($bytes, 0, $bytes.Length); $es.Close()
}
Add-ZipEntry $zip $war "KOTH.war"
Get-ChildItem (Join-Path $root ".ebextensions") -File | ForEach-Object {
    Add-ZipEntry $zip $_.FullName (".ebextensions/" + $_.Name)
}
$zip.Dispose(); $fs.Close()

Write-Host "==> Uploading bundle to s3://$bucket/$s3key" -ForegroundColor Cyan
& $aws s3 cp $bundle "s3://$bucket/$s3key" --region $region

Write-Host "==> Creating application version $VersionLabel" -ForegroundColor Cyan
& $aws elasticbeanstalk create-application-version `
    --application-name $appName `
    --version-label $VersionLabel `
    --source-bundle "S3Bucket=$bucket,S3Key=$s3key" `
    --region $region | Out-Null

Write-Host "==> Deploying $VersionLabel to $envName" -ForegroundColor Cyan
& $aws elasticbeanstalk update-environment `
    --application-name $appName `
    --environment-name $envName `
    --version-label $VersionLabel `
    --region $region | Out-Null

Write-Host "==> Done. Watch status with:" -ForegroundColor Green
Write-Host "    & `"$aws`" elasticbeanstalk describe-environments --environment-names $envName --query `"Environments[0].{Status:Status,Health:Health,Ver:VersionLabel}`" --output table"
