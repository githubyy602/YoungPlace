param(
    [string]$BaseUrl = "http://localhost:8080",
    [switch]$TriggerRefresh
)

$ErrorActionPreference = "Stop"

if ($TriggerRefresh) {
    Write-Host "[INFO] trigger gateway /actuator/refresh"
    Invoke-RestMethod -Method Post -Uri "$BaseUrl/actuator/refresh" | Out-Null
}

Write-Host "[INFO] query /debug/gateway/config"
$result = Invoke-RestMethod -Method Get -Uri "$BaseUrl/debug/gateway/config"
$result | ConvertTo-Json -Depth 8
