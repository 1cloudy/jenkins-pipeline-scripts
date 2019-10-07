﻿param(
    [string] $StagingName, 
    [string] $StoreName,
    $AzureBlobName,
    $AzureBlobKey
)

$StoreName = "vc4test"

$ErrorActionPreference = "Stop"

# Get Theme Zip File

$Path2Zip = Get-Childitem -Recurse -Path "${env:WORKSPACE}\" -File -Include *.zip

# Unzip Theme Zip File

$Path = "${env:WORKSPACE}\" + [System.IO.Path]::GetFileNameWithoutExtension($Path2Zip)
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::ExtractToDirectory($Path2Zip, $Path)

# Upload Theme Zip File to Azure

$ConnectionString = "DefaultEndpointsProtocol=https;AccountName={0};AccountKey={1};EndpointSuffix=core.windows.net"
if ($StagingName -eq "dev-vc-new-design"){
    $ConnectionString = $ConnectionString -f $AzureBlobName, $AzureBlobKey
}
$BlobContext = New-AzureStorageContext -ConnectionString $ConnectionString

$AzureBlobName = ""

Write-Host "Remove from cms-content"
Get-AzureStorageBlob -Blob ("$AzureBlobName*") -Container "cms-content" -Context $BlobContext  | ForEach-Object { Remove-AzureStorageBlob -Blob $_.Name -Container "cms-content" -Context $BlobContext } -ErrorAction Continue

Write-Host "Upload to cms-content"
Get-ChildItem -File -Recurse $Path | ForEach-Object { Set-AzureStorageBlobContent -File $_.FullName -Blob ("$AzureBlobName/" + (([System.Uri]("$Path/")).MakeRelativeUri([System.Uri]($_.FullName))).ToString()) -Container "cms-content" -Context $BlobContext }
