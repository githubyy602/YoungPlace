param(
    [string]$BaseUrl = "http://localhost:9002",
    [switch]$TriggerRefresh
)

$ErrorActionPreference = "Stop"

if ($TriggerRefresh) {
    Write-Host "[INFO] trigger user-service /actuator/refresh"
    Invoke-RestMethod -Method Post -Uri "$BaseUrl/actuator/refresh" | Out-Null
}

Write-Host "[INFO] query /api/users/config/debug"
$result = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/users/config/debug"
$result | ConvertTo-Json -Depth 8
