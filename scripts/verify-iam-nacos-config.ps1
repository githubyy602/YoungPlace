param(
    [string]$BaseUrl = "http://localhost:9001",
    [switch]$TriggerRefresh
)

$ErrorActionPreference = "Stop"

if ($TriggerRefresh) {
    Write-Host "[INFO] trigger /actuator/refresh"
    Invoke-RestMethod -Method Post -Uri "$BaseUrl/actuator/refresh" | Out-Null
}

Write-Host "[INFO] query /api/iam/config/debug"
$result = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/iam/config/debug"
$result | ConvertTo-Json -Depth 8
