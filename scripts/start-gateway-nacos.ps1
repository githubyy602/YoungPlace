param(
    [string]$NacosAddr = "127.0.0.1:8848",
    [string]$NacosNamespace = "youngplace-dev",
    [string]$NacosGroup = "DEFAULT_GROUP"
)

$ErrorActionPreference = "Stop"

Write-Host "[INFO] start Gateway in nacos profile"
Write-Host "[INFO] NACOS_ADDR=$NacosAddr"
Write-Host "[INFO] NACOS_NAMESPACE=$NacosNamespace"
Write-Host "[INFO] NACOS_GROUP=$NacosGroup"

$env:NACOS_ADDR = $NacosAddr
$env:NACOS_NAMESPACE = $NacosNamespace
$env:NACOS_GROUP = $NacosGroup

Write-Host "[INFO] check nacos availability"
try {
    Invoke-WebRequest -UseBasicParsing -Uri ("http://{0}/nacos" -f $NacosAddr) -TimeoutSec 5 | Out-Null
} catch {
    throw ("Nacos is unreachable at http://{0}/nacos. Please start Nacos first." -f $NacosAddr)
}

mvn -pl ev-gateway -DskipTests spring-boot:run "-Dspring-boot.run.profiles=nacos"
