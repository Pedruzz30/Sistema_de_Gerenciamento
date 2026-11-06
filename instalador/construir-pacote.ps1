# Monta o ZIP de instalação da loja: sistema + Java + PostgreSQL. Roda no GitHub (stockos-instalador.yml).
param(
    [Parameter(Mandatory)][string]$Jar,
    [string]$Saida = (Join-Path $PSScriptRoot 'saida')
)
$ErrorActionPreference = 'Stop'

$Downloads = @{
    java = @{
        url  = 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21%2B9/OpenJDK17U-jre_x64_windows_hotspot_17.0.21_9.zip'
        hash = 'fe4656bd78fb09812dc38c49da777f79cc33e7ee435a254be82f9e7f40d9d117'
    }
    pgsql = @{
        url  = 'https://get.enterprisedb.com/postgresql/postgresql-17.11-1-windows-x64-binaries.zip'
        hash = '6eabdf00d2893713b75db4336a23c3fdf505f056e217ec6e2e95d901750cfea3'
    }
}
$Cache = Join-Path $PSScriptRoot 'cache'
$Trabalho = Join-Path $Saida 'StockOS-Loja'
$Programa = Join-Path $Trabalho 'programa'

function Get-Download([string]$Nome) {
    $item = $Downloads[$Nome]
    $arquivo = Join-Path $Cache "$Nome.zip"
    if (-not (Test-Path $arquivo)) {
        Write-Host "Baixando $Nome..."
        & curl.exe -sSfL -o $arquivo $item.url
        if ($LASTEXITCODE -ne 0) { throw "Falha ao baixar $Nome." }
    }
    # Nada entra no pacote sem conferir: o que vai para a loja é exatamente o que foi revisado.
    $hash = (Get-FileHash $arquivo -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($hash -ne $item.hash) {
        Remove-Item $arquivo
        throw "O download de $Nome não confere (recebido $hash)."
    }
    $arquivo
}

# PowerShell 5.1 lê .ps1 sem BOM como ANSI, e o .cmd precisa de fim de linha do Windows.
function Copy-Texto([string]$Origem, [string]$Destino, [bool]$ComBom) {
    $texto = [IO.File]::ReadAllText($Origem) -replace "`r?`n", "`r`n"
    [IO.File]::WriteAllText($Destino, $texto, (New-Object Text.UTF8Encoding($ComBom)))
}

New-Item -ItemType Directory -Force -Path $Cache | Out-Null
if (Test-Path $Saida) { Remove-Item $Saida -Recurse -Force }
New-Item -ItemType Directory -Force -Path (Join-Path $Programa 'scripts'), (Join-Path $Programa 'atalhos') | Out-Null

Write-Host 'Extraindo o Java...'
& tar -xf (Get-Download 'java') -C $Programa
if ($LASTEXITCODE -ne 0) { throw 'Falha ao extrair o Java.' }
Rename-Item (Get-ChildItem $Programa -Directory -Filter 'jdk-*').FullName 'java'

Write-Host 'Extraindo o PostgreSQL...'
& tar -xf (Get-Download 'pgsql') -C $Programa --exclude 'pgsql/pgAdmin 4/*' --exclude 'pgsql/doc/*' --exclude 'pgsql/include/*' --exclude 'pgsql/StackBuilder/*' --exclude 'pgsql/symbols/*'
if ($LASTEXITCODE -ne 0) { throw 'Falha ao extrair o PostgreSQL.' }
foreach ($sobra in 'pgAdmin 4', 'doc', 'include', 'StackBuilder', 'symbols') {
    $pasta = Join-Path $Programa "pgsql\$sobra"
    if (Test-Path $pasta) { Remove-Item $pasta -Recurse -Force }
}

# O PostgreSQL depende do runtime do Visual C++, que nem todo Windows tem. O Java traz os arquivos.
$runtime = Get-ChildItem (Join-Path $Programa 'java\bin') -Include 'vcruntime140*.dll', 'msvcp140*.dll' -Recurse
if ($runtime.Count -lt 3) { throw 'O Java não trouxe o runtime do Visual C++.' }
$runtime | Copy-Item -Destination (Join-Path $Programa 'pgsql\bin')

Copy-Item $Jar (Join-Path $Programa 'stockos.jar')
Get-ChildItem (Join-Path $PSScriptRoot 'scripts') -Filter '*.ps1' | ForEach-Object {
    Copy-Texto $_.FullName (Join-Path $Programa "scripts\$($_.Name)") $true
}
Copy-Texto (Join-Path $PSScriptRoot 'Instalar.cmd') (Join-Path $Trabalho 'Instalar.cmd') $false
Copy-Texto (Join-Path $PSScriptRoot 'LEIA-ME.txt') (Join-Path $Trabalho 'LEIA-ME.txt') $true
Copy-Texto (Join-Path $PSScriptRoot 'Restaurar backup.cmd') (Join-Path $Programa 'atalhos\Restaurar backup.cmd') $false

$versaoPostgres = ($Downloads.pgsql.url -replace '.*postgresql-(\d+)\..*', '$1')
$versao = if ($env:GITHUB_SHA) { $env:GITHUB_SHA.Substring(0, 7) } else { 'local' }
[IO.File]::WriteAllText((Join-Path $Programa 'versao.json'), (@{ stockos = $versao; postgres = $versaoPostgres } | ConvertTo-Json))

$zip = Join-Path $Saida 'StockOS-Loja.zip'
Write-Host 'Compactando...'
& tar -a -cf $zip -C $Saida 'StockOS-Loja'
if ($LASTEXITCODE -ne 0) { throw 'Falha ao compactar.' }
Write-Host ("Pacote pronto: {0} ({1:N0} MB)" -f $zip, ((Get-Item $zip).Length / 1MB))
