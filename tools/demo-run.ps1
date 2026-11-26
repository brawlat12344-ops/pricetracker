param(
    [string]$BaseUrl = 'http://localhost',
    [string]$CatalogUrl = 'http://localhost:8081',
    [string]$PriceUrl = 'http://localhost:8082',
    [string]$OutDir = 'artifacts/demo'
)

# Demo evidence run: login, three scenarios, save HTML and JSON responses.
# All request payloads are ASCII so Windows PowerShell 5.1 quoting stays reliable.
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8

$out = Join-Path (Get-Location) $OutDir
New-Item -ItemType Directory -Force -Path $out | Out-Null
$cookie = Join-Path $out 'cookies.txt'
Remove-Item -LiteralPath $cookie -ErrorAction SilentlyContinue

function Save-Response {
    param(
        [string]$Url,
        [string]$Path,
        [string]$Method = 'GET',
        [string]$DataFile = ''
    )

    $curlArgs = @('-sS', '-b', $cookie, '-c', $cookie, '-o', $Path, '-w', '%{http_code}')
    if ($Method -eq 'POST') { $curlArgs += @('-X', 'POST') }
    if ($DataFile) {
        $curlArgs += @('-H', 'Content-Type: application/json', '--data-binary', ('@' + $DataFile))
    }
    $curlArgs += $Url
    $code = & curl.exe @curlArgs
    if ($code -notmatch '^2') {
        throw "Request failed with HTTP $code : $Url"
    }
    return $code
}

function Write-Payload {
    param([string]$Path, [string]$Json)
    [IO.File]::WriteAllText($Path, $Json, [Text.Encoding]::ASCII)
}

$summary = New-Object 'System.Collections.Generic.List[string]'
$summary.Add("PriceTracker demo run: $(Get-Date -Format o)")
$summary.Add("Base URL: $BaseUrl")

$loginPath = Join-Path $out 'login.html'
$loginCode = & curl.exe -sS -L -c $cookie -b $cookie -o $loginPath -w '%{http_code}' `
    -d 'email=demo@example.com&password=demopass123' "$BaseUrl/login"
if ($loginCode -notmatch '^2') { throw "Login failed with HTTP $loginCode" }
$summary.Add("login.html : HTTP $loginCode")

$cheaperPayload = Join-Path $out 'cheaper-request.json'
Write-Payload $cheaperPayload '{"url":"https://www.dns-shop.ru/product/12345678/gaming-pc/","category":"pc","title":"Core i5 RTX 4070 32GB","limit":5,"crossStore":true}'
$cheaperCode = Save-Response "$BaseUrl/api/cheaper" (Join-Path $out 'cheaper.json') 'POST' $cheaperPayload
$summary.Add("cheaper.json : HTTP $cheaperCode")

# Второй запрос с ценой со страницы: показывает честное сравнение без «Ориентира».
$pricedPayload = Join-Path $out 'cheaper-priced-request.json'
Write-Payload $pricedPayload '{"url":"https://www.dns-shop.ru/product/12345678/gaming-pc/","category":"pc","title":"Core i5 RTX 4070 32GB","limit":5,"crossStore":true,"sourcePrice":89990}'
$pricedCode = Save-Response "$BaseUrl/api/cheaper" (Join-Path $out 'cheaper-priced.json') 'POST' $pricedPayload
$summary.Add("cheaper-priced.json : HTTP $pricedCode")

$buildPayload = Join-Path $out 'build-request.json'
Write-Payload $buildPayload '{"category":"pc","purpose":"game","priority":"performance","wishes":"RGB","region":"Moscow","budget":120000}'
$buildCode = Save-Response "$BaseUrl/api/build" (Join-Path $out 'build-pc.json') 'POST' $buildPayload
$summary.Add("build-pc.json : HTTP $buildCode")

$laptopPayload = Join-Path $out 'laptop-request.json'
Write-Payload $laptopPayload '{"budget":90000,"purpose":"study","priority":"battery","screen":"13_14","os":"any","requirements":"RAM 16","region":"Moscow"}'
$laptopCode = Save-Response "$BaseUrl/api/laptops" (Join-Path $out 'laptop.json') 'POST' $laptopPayload
$summary.Add("laptop.json : HTTP $laptopCode")

$pcPageUrl = "$BaseUrl/cheaper?mode=build&category=pc&purpose=game&priority=performance&region=Moscow&budget=120000&wishes=RGB"
$laptopPageUrl = "$BaseUrl/cheaper?mode=build&category=laptop&purpose=study&priority=battery&screen=13_14&os=any&region=Moscow&budget=90000&requirements=RAM%2016"
$summary.Add("build-pc.html : HTTP " + (Save-Response $pcPageUrl (Join-Path $out 'build-pc.html')))
$summary.Add("laptop.html : HTTP " + (Save-Response $laptopPageUrl (Join-Path $out 'laptop.html')))

# Доказательство межсервисного взаимодействия: gateway -> catalog -> price.
$topologyCode = Save-Response "$BaseUrl/api/topology" (Join-Path $out 'topology.json')
$summary.Add("topology.json : HTTP $topologyCode")

$healthCode = Save-Response "$BaseUrl/actuator/health" (Join-Path $out 'health.json')
$metricsCode = Save-Response "$BaseUrl/actuator/metrics" (Join-Path $out 'metrics.json')
$catalogHealthCode = Save-Response "$CatalogUrl/actuator/health" (Join-Path $out 'catalog-health.json')
$priceHealthCode = Save-Response "$PriceUrl/actuator/health" (Join-Path $out 'price-health.json')
$catalogMetricsCode = Save-Response "$CatalogUrl/actuator/metrics" (Join-Path $out 'catalog-metrics.json')
$priceMetricsCode = Save-Response "$PriceUrl/actuator/metrics" (Join-Path $out 'price-metrics.json')
$summary.Add("health.json : HTTP $healthCode")
$summary.Add("metrics.json : HTTP $metricsCode")
$summary.Add("catalog-health.json : HTTP $catalogHealthCode")
$summary.Add("price-health.json : HTTP $priceHealthCode")
$summary.Add("catalog-metrics.json : HTTP $catalogMetricsCode")
$summary.Add("price-metrics.json : HTTP $priceMetricsCode")

foreach ($file in Get-ChildItem -File $out | Sort-Object Name) {
    $summary.Add(("{0,-24} {1,8} bytes" -f $file.Name, $file.Length))
}

[IO.File]::WriteAllLines((Join-Path $out 'summary.txt'), $summary, [Text.Encoding]::UTF8)
$summary | ForEach-Object { Write-Host $_ }
Write-Host "Demo artifacts saved to: $out"
