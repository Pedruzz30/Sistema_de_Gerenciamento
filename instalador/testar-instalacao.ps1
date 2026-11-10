# Instala o pacote em um Windows limpo do GitHub e simula os desastres da loja.
# APAGA C:\StockOS: nunca rode fora do GitHub.
param([Parameter(Mandatory)][string]$Zip)
$ErrorActionPreference = 'Stop'
if ($env:GITHUB_ACTIONS -ne 'true') { throw 'Este teste apaga C:\StockOS e só roda no GitHub.' }

$Raiz = 'C:\StockOS'
$PenDrive = 'C:\StockOS-Backups'
$Base = 'http://localhost:8080'
$Senha = 'SenhaDoDono1'

# Pasta com espaço e acento, como "Downloads\Instalação da loja" no computador de alguém.
$Extraido = 'C:\Temp\Instalação da Loja'
New-Item -ItemType Directory -Force -Path $Extraido | Out-Null
& tar -xf $Zip -C $Extraido
if ($LASTEXITCODE -ne 0) { throw 'Falha ao extrair o pacote.' }
$Pacote = Join-Path $Extraido 'StockOS-Loja'

function Step([string]$Nome) { Write-Host ''; Write-Host "=== $Nome ===" -ForegroundColor Cyan }

function Assert([bool]$Condicao, [string]$Mensagem) {
    if (-not $Condicao) { throw "FALHOU: $Mensagem" }
    Write-Host "  ok: $Mensagem"
}

# Sempre no PowerShell 5.1, o que existe no Windows da loja.
function Invoke-Ps51([string]$Script, [string[]]$Argumentos = @()) {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $Script @Argumentos | Write-Host
    $LASTEXITCODE
}

function Install-Pacote([string]$Origem) { Invoke-Ps51 (Join-Path $Origem 'programa\scripts\instalar.ps1') @('-SemAbrir') }
function Restore-Arquivo([string]$Arquivo) { Invoke-Ps51 (Join-Path $Raiz 'programa\scripts\restaurar.ps1') @('-Arquivo', $Arquivo, '-Confirmado') }

function Connect-Dono {
    $sessao = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    Invoke-RestMethod -Method Post -Uri "$Base/api/auth/login" -WebSession $sessao -ContentType 'application/json; charset=utf-8' `
        -Body (@{ nome = 'Admin'; sobrenome = 'Superior'; senha = $Senha } | ConvertTo-Json) | Out-Null
    $sessao
}

function Get-Produtos($Sessao) { @(Invoke-RestMethod -Uri "$Base/api/produtos" -WebSession $Sessao) }

function Get-Saldo($Produtos, [string]$Nome) {
    ($Produtos | Where-Object { $_.nome -eq $Nome } | Select-Object -First 1).quantidadeAtual
}

function Assert-DadosDaLoja([string]$Quando) {
    $sessao = Connect-Dono
    $produtos = Get-Produtos $sessao
    Assert ((Get-Saldo $produtos 'Skol lata 350ml') -eq 87) "$Quando`: estoque da Skol (87) e senha do dono continuam"
    $sessao
}

function Invoke-Psql([string]$Sql) {
    $config = Get-Content "$Raiz\config\stockos.json" -Raw | ConvertFrom-Json
    $env:PGPASSWORD = $config.senhaBanco
    # O Windows entrega o argumento na página de código ANSI, não em UTF-8.
    $env:PGCLIENTENCODING = 'WIN1252'
    $saida = & "$Raiz\programa\pgsql\bin\psql.exe" -h 127.0.0.1 -p $config.portaBanco -U stockos -d stockos -w -tA -c $Sql
    if ($LASTEXITCODE -ne 0) { throw "psql falhou: $Sql" }
    "$saida".Trim()
}

function New-BackupPelaTela($Sessao) {
    Invoke-RestMethod -Method Post -Uri "$Base/api/sistema/backup" -WebSession $Sessao
}

Step 'Instalação nova, de uma pasta com espaço e acento'
Assert ((Install-Pacote $Pacote) -eq 0) 'instalador terminou sem erro'
Assert (Test-Path "$Raiz\dados\PG_VERSION") 'banco criado'
Assert ((Invoke-WebRequest "$Base/login.html" -UseBasicParsing).StatusCode -eq 200) 'tela de login no ar'
Assert (Test-Path (Join-Path ([Environment]::GetFolderPath('Desktop')) 'StockOS.lnk')) 'atalho na Área de Trabalho'
$atalhoCaixa = (New-Object -ComObject WScript.Shell).CreateShortcut((Join-Path ([Environment]::GetFolderPath('Desktop')) 'StockOS Caixa.lnk'))
Assert ($atalhoCaixa.Arguments -match 'abrir\.ps1" -Caixa$') 'atalho do caixa com impressão direta'
Assert (Test-Path (Join-Path ([Environment]::GetFolderPath('Startup')) 'StockOS (iniciar).lnk')) 'início automático com o Windows'
Assert (Test-Path "$Raiz\Restaurar backup.cmd") 'atalho de restauração na pasta'

Step 'O que fica protegido'
$acesso = (& icacls.exe $Raiz | Out-String)
Assert ($acesso -notmatch 'BUILTIN\\Users|Authenticated Users|Everyone') 'outros usuários do Windows não abrem a pasta'
Assert ((Get-Content "$Raiz\dados\postgresql.conf" -Raw) -match "listen_addresses = '127.0.0.1'") 'banco só aceita este computador'
$ipRede = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' } | Select-Object -First 1).IPAddress
$cliente = New-Object Net.Sockets.TcpClient
$conectou = $false
try { $conectou = $cliente.ConnectAsync($ipRede, 8080).Wait(2000) } catch { $conectou = $false }
$cliente.Dispose()
Assert (-not $conectou) "sistema não abre pela rede ($ipRede)"
Assert ((Invoke-Psql "SELECT 'Água' < 'Zebra' AND 'água' < 'Bebida'") -eq 't') 'ordem alfabética em português'

Step 'Caminho completo da loja no navegador, neste Windows'
$env:E2E_URL = $Base
$env:E2E_BACKUP = '1'
& node scripts/test-e2e-real.mjs
Assert ($LASTEXITCODE -eq 0) 'teste de ponta a ponta passou'

Step 'Backup pela tela, com cópia no pen drive'
$sessao = Assert-DadosDaLoja 'depois das vendas'
New-Item -ItemType Directory -Force -Path $PenDrive | Out-Null
Start-Sleep -Seconds 2
$situacao = New-BackupPelaTela $sessao
Assert (-not $situacao.ultimoErro) 'backup sem erro'
Assert (-not $situacao.semCopiaExterna) 'painel reconhece a cópia externa'
$backupBom = Get-ChildItem $PenDrive -Filter 'stockos-*.dump' | Sort-Object Name -Descending | Select-Object -First 1
Assert ($null -ne $backupBom) 'arquivo gravado no pen drive'
Assert (Test-Path (Join-Path "$Raiz\backups" $backupBom.Name)) 'arquivo gravado no notebook'

Step 'Fechamento de caixa dispara backup'
$antes = @(Get-ChildItem "$Raiz\backups" -Filter 'stockos-2*.dump').Count
$corpoAbrir = @{ numeroCaixa = 7; saldoInicial = 10 } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$Base/api/caixas/abrir" -WebSession $sessao -ContentType 'application/json' -Body $corpoAbrir | Out-Null
Start-Sleep -Seconds 2
$corpoFechar = @{ numeroCaixa = 7; valorContado = 10 } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$Base/api/caixas/encerrar" -WebSession $sessao -ContentType 'application/json' -Body $corpoFechar | Out-Null
$limite = (Get-Date).AddSeconds(60)
while ((Get-Date) -lt $limite -and @(Get-ChildItem "$Raiz\backups" -Filter 'stockos-2*.dump').Count -le $antes) { Start-Sleep -Seconds 1 }
Assert (@(Get-ChildItem "$Raiz\backups" -Filter 'stockos-2*.dump').Count -gt $antes) 'backup novo depois do fechamento'
$limite = (Get-Date).AddSeconds(30)
while ((Get-Date) -lt $limite -and (Invoke-RestMethod -Uri "$Base/api/sistema/backup" -WebSession $sessao).emAndamento) { Start-Sleep -Seconds 1 }

Step 'Restaurar desfaz o que veio depois do backup'
$novo = @{ nome = 'Produto depois do backup'; categoriaCardapioCodigo = 'bebidas'; categoriaCardapioNome = 'Bebidas'; categoriaEstoque = 'BEBIDAS'
    precoVenda = 5; custoUnitario = 2; quantidadeInicial = 5; quantidadeMinima = 1 } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$Base/api/produtos/revenda" -WebSession $sessao -ContentType 'application/json' -Body $novo | Out-Null
Assert ($null -ne (Get-Saldo (Get-Produtos $sessao) 'Produto depois do backup')) 'produto novo existe antes de restaurar'
Assert ((Restore-Arquivo $backupBom.FullName) -eq 0) 'restauração terminou sem erro'
$sessao = Assert-DadosDaLoja 'depois de restaurar'
Assert ($null -eq (Get-Saldo (Get-Produtos $sessao) 'Produto depois do backup')) 'produto feito depois do backup saiu'
Assert (@(Get-ChildItem "$Raiz\backups" -Filter 'stockos-antes-restauracao-*.dump').Count -ge 1) 'cópia de antes da restauração guardada'

Step 'Arquivo que não é backup é recusado sem mexer em nada'
$falso = Join-Path $env:TEMP 'nao-e-backup.dump'
Set-Content -LiteralPath $falso -Value 'isto nao e um backup'
Assert ((Restore-Arquivo $falso) -ne 0) 'restauração recusada'
Assert-DadosDaLoja 'depois da recusa' | Out-Null

Step 'Queda de energia: tudo morre de repente'
Get-Process javaw, postgres -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 2
Assert ((Invoke-Ps51 "$Raiz\programa\scripts\iniciar.ps1") -eq 0) 'início automático rodou'
$limite = (Get-Date).AddSeconds(180)
$voltou = $false
while ((Get-Date) -lt $limite -and -not $voltou) {
    try { $voltou = (Invoke-WebRequest "$Base/api/auth/config" -UseBasicParsing -TimeoutSec 3).StatusCode -eq 200 } catch { Start-Sleep -Seconds 2 }
}
Assert $voltou 'sistema voltou sozinho'
Assert-DadosDaLoja 'depois da queda' | Out-Null

Step 'Atualização não apaga dados'
Assert ((Install-Pacote $Pacote) -eq 0) 'atualização terminou sem erro'
Assert-DadosDaLoja 'depois de atualizar' | Out-Null
Assert (@(Get-ChildItem "$Raiz\backups" -Filter 'stockos-antes-atualizacao-*.dump').Count -ge 1) 'cópia de antes da atualização guardada'
Assert (-not (Test-Path "$Raiz\programa.anterior")) 'versão antiga removida depois do sucesso'

Step 'Atualização quebrada volta sozinha'
$Quebrado = 'C:\Temp\Pacote quebrado'
Copy-Item $Pacote $Quebrado -Recurse
Set-Content -LiteralPath "$Quebrado\programa\stockos.jar" -Value 'nao e um programa'
Assert ((Install-Pacote $Quebrado) -ne 0) 'atualização avisou da falha'
Assert ((Get-Item "$Raiz\programa\stockos.jar").Length -gt 1MB) 'programa anterior voltou'
Assert-DadosDaLoja 'depois da atualização quebrada' | Out-Null

Step 'Notebook perdido: instala do zero e restaura do pen drive'
Assert ((Invoke-Ps51 "$Raiz\programa\scripts\parar.ps1") -eq 0) 'sistema parado'
Remove-Item $Raiz -Recurse -Force
Assert ((Install-Pacote $Pacote) -eq 0) 'instalação nova terminou sem erro'
$vazio = (Invoke-Psql "SELECT count(*) FROM produtos WHERE nome = 'Skol lata 350ml'")
Assert ($vazio -eq '0') 'banco novo começou vazio'
Assert ((Restore-Arquivo $backupBom.FullName) -eq 0) 'restauração do pen drive terminou sem erro'
$sessao = Assert-DadosDaLoja 'no notebook novo'
$situacao = Invoke-RestMethod -Uri "$Base/api/sistema/backup" -WebSession $sessao
Assert $situacao.monitorado 'backup segue ligado no notebook novo'

Step 'Dono esqueceu a senha'
Assert (Test-Path "$Raiz\Redefinir senha do dono.cmd") 'atalho na pasta do sistema'
$saida = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$Raiz\programa\scripts\redefinir-senha-dono.ps1" -Confirmado | Out-String
Write-Host $saida
Assert ($LASTEXITCODE -eq 0) 'senha temporária criada'
$temporaria = ([regex]::Match($saida, 'SENHA TEMPORARIA: (\S+)')).Groups[1].Value
Assert ($temporaria -match '^[A-HJ-NP-Z2-9]{8}$') 'senha temporária legível'
$recusou = $false
try { Connect-Dono | Out-Null } catch { $recusou = $true }
Assert $recusou 'senha esquecida parou de valer'
$sessao = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$entrou = Invoke-RestMethod -Method Post -Uri "$Base/api/auth/login" -WebSession $sessao -ContentType 'application/json; charset=utf-8' `
    -Body (@{ nome = 'Admin'; sobrenome = 'Superior'; senha = $temporaria } | ConvertTo-Json)
Assert $entrou.senhaProvisoria 'entra com a temporária e precisa criar outra'
Invoke-RestMethod -Method Post -Uri "$Base/api/auth/trocar-senha" -WebSession $sessao -ContentType 'application/json; charset=utf-8' `
    -Body (@{ senhaAtual = $temporaria; novaSenha = $Senha } | ConvertTo-Json) | Out-Null
Assert-DadosDaLoja 'com a senha nova do dono' | Out-Null

Write-Host ''
Write-Host 'PASS: instalação, backup, restauração e desastres no Windows.' -ForegroundColor Green
