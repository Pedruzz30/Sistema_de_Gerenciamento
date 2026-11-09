# Quando o dono esquece a senha e não há outro dono para redefinir pela tela.
# Só funciona no notebook da loja, onde o sistema está instalado.
param(
    [switch]$Confirmado,
    [string]$Numero
)
. (Join-Path $PSScriptRoot 'comum.ps1')
$C = Get-Caminhos (Split-Path (Split-Path $PSScriptRoot))

try {
    $config = Read-Config $C
    Start-Banco $C $config

    if (-not $Confirmado) {
        Write-Host ''
        Write-Host 'Isto cria uma senha temporária para o dono da loja.' -ForegroundColor Yellow
        Write-Host 'A senha atual dele para de valer, e quem estiver usando o sistema com ela sai.'
        if ((Read-Host 'Para continuar, digite SIM') -cne 'SIM') {
            Write-Host 'Cancelado. Nada foi alterado.'
            exit 0
        }
    }

    $env:DB_URL = "jdbc:postgresql://127.0.0.1:$($config.portaBanco)/stockos"
    $env:DB_USERNAME = 'stockos'
    $env:DB_PASSWORD = $config.senhaBanco
    $argumentos = @('-cp', $C.Jar, '-Dloader.main=app.RedefinirSenhaDono', 'org.springframework.boot.loader.launch.PropertiesLauncher')
    if ($Numero) { $argumentos += $Numero }
    try {
        $resultado = Invoke-Programa (Join-Path $C.Programa 'java\bin\java.exe') $argumentos
    } finally {
        Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
    }

    if ($resultado.Codigo -eq 3) {
        Write-Host ''
        Write-Host 'Há mais de um dono. Abra de novo e informe o número de quem esqueceu:'
        Write-Host $resultado.Saida
        exit 3
    }
    if ($resultado.Codigo -ne 0) { throw $resultado.Saida }

    $senha = ([regex]::Match($resultado.Saida, 'SENHA TEMPORARIA: (\S+)')).Groups[1].Value
    $dono = ([regex]::Match($resultado.Saida, 'Dono: (.+?) \(numero')).Groups[1].Value
    if (-not $senha) { throw "Resposta inesperada:`n$($resultado.Saida)" }

    Write-Host ''
    Write-Host "Senha temporária de ${dono}:"
    Write-Host ''
    Write-Host "      $senha" -ForegroundColor Green
    Write-Host ''
    Write-Host 'Entre no sistema com ela. Ele vai pedir uma senha nova e um PIN.'
    Write-Host 'Se aparecer "muitas tentativas", espere 5 minutos.'
    Write-Host "SENHA TEMPORARIA: $senha"
    exit 0
} catch {
    Write-Host ''
    Write-Host "NÃO DEU CERTO: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
