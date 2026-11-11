# Instalação do StockOS na loja

Guia para instalar e cuidar do sistema no notebook do balcão. Não precisa de Docker, de internet nem de instalar Java ou banco de dados: tudo vem no pacote.

**Requisitos:** Windows 10 ou 11 de 64 bits, 8 GB de memória e pelo menos 2 GB livres no disco.

## 1. Baixar o pacote

1. Abra a página de execuções do workflow [StockOS instalador da loja](https://github.com/Pedruzz30/Sistema_de_Gerenciamento/actions/workflows/stockos-instalador.yml), com sua conta do GitHub.
2. Entre na execução mais recente com o sinal verde.
3. Em **Artifacts**, baixe **StockOS-Loja**. O GitHub entrega um ZIP com o `StockOS-Loja.zip` dentro.

O pacote fica disponível por 30 dias depois de cada execução. Guarde uma cópia.

## 2. Instalar

1. Extraia o ZIP (botão direito, **Extrair tudo**).
2. Abra `Instalar.cmd`. Se o Windows avisar sobre o arquivo, clique em **Mais informações** e **Executar assim mesmo**.
3. Quando o navegador abrir, entre com nome `Admin`, sobrenome `Superior` e senha `1234`.
4. O sistema pede uma senha nova e um PIN. Anote em lugar seguro.

O sistema fica em `C:\StockOS`. Instale com o usuário do Windows que vai usar o caixa: só ele tem acesso à pasta, e o sistema liga quando ele entra no Windows.

## 3. Ajustes no notebook

Faça uma vez, depois de instalar:

- **Impressora térmica como padrão:** em Configurações, Bluetooth e dispositivos, Impressoras, escolha a térmica e clique em "Definir como padrão". No caixa, use o atalho **StockOS Caixa**: o cupom sai direto nela, sem a janela de impressão. Para imprimir a folha de códigos ou o fechamento em outra impressora, use o atalho **StockOS**.
- **Gaveta:** nas propriedades da impressora térmica, ligue a opção de abrir a gaveta ao imprimir, se o driver tiver.
- **Cupom de teste:** abra o caixa, faça uma venda e aperte **F8**. Confira se o cupom sai inteiro e se a gaveta abre.

- **Não deixar suspender na tomada:** em Configurações, Sistema, Energia. Com o notebook suspenso, o sistema e os backups param.
- **Entrada automática no Windows:** o sistema liga quando o usuário entra. Se o notebook reiniciar sozinho, ele só volta depois que alguém entrar no Windows.
- **Pen drive com a pasta de backup:** veja a próxima seção.

## 4. Backup

**Automático:** a cada 3 horas com o sistema ligado e a cada fechamento de caixa. Os arquivos ficam em `C:\StockOS\backups`.

**O que fica guardado:** tudo dos últimos 2 dias, o último de cada dia por 30 dias e o último de cada mês por um ano.

**Cópia fora do notebook:** o backup em `C:\StockOS\backups` não ajuda se o notebook quebrar ou for roubado. Crie uma pasta chamada exatamente `StockOS-Backups` em um pen drive, em um HD externo ou no OneDrive. A cópia é automática sempre que a pasta estiver disponível, mesmo que a letra do pen drive mude.

Guarde o pen drive em local seguro: os arquivos têm vendas, estoque e cadastros, **sem criptografia**. As senhas ficam só como hash.

**Como saber se está funcionando:** entre com a sua senha e olhe o topo do Dashboard.

| Aviso | O que fazer |
|---|---|
| Verde: "Backup em dia" | Nada |
| Amarelo: "Os backups estão só neste notebook" | Conecte o pen drive, ou crie a pasta |
| Amarelo: "A cópia do backup não foi gravada" | Confira se há espaço no pen drive |
| Vermelho: "O backup está com problema" | Clique em **Fazer backup agora**. Se continuar, veja `C:\StockOS\backups\ultimo-backup.log` e `C:\StockOS\logs\stockos.log` |

O painel aparece só para quem tem acesso ao financeiro e entrou com senha. Quem entrou por PIN não o vê.

## 5. Restaurar um backup

1. Abra `C:\StockOS\Restaurar backup.cmd`.
2. Escolha o número do backup na lista, ou cole o caminho do arquivo.
3. Digite `RESTAURAR` para confirmar.

Vendas e cadastros feitos depois daquele backup saem do sistema. Antes de trocar, ele guarda uma cópia de como estava, com nome `stockos-antes-restauracao-...`. Se o backup não puder ser carregado, nada muda.

## 6. Esqueceu a senha

- **Funcionário:** o gerente entra, vai em **Funcionários** e clica em **Redefinir senha** na linha da pessoa. Aparece uma senha temporária, uma vez só. A pessoa entra com ela e cria uma senha nova e um PIN.
- **Dono:** no notebook da loja, abra `C:\StockOS\Redefinir senha do dono.cmd`, digite `SIM` e use a senha temporária que aparecer. Se houver mais de um dono, ele pede o número de quem esqueceu.

Se aparecer "muitas tentativas", espere 5 minutos.

## 7. Notebook novo (quebrou ou foi roubado)

1. Instale o sistema no notebook novo, como no item 2. Não precisa criar senha no sistema vazio.
2. Conecte o pen drive.
3. Abra `C:\StockOS\Restaurar backup.cmd` e escolha o backup mais novo.
4. Entre com a sua senha de sempre.

## 8. Atualizar o sistema

Baixe o pacote novo, extraia e abra `Instalar.cmd`. Os dados continuam. Antes de trocar, ele guarda uma cópia. Se a versão nova não ligar, ele volta sozinho para a anterior, com os dados de antes da atualização.

## 9. Celular do atendente

O atendente lança os pedidos da comanda pelo próprio celular, no Wi-Fi da loja. O celular só vê a tela de comanda; o resto do sistema continua só no notebook.

**Uma vez no notebook:** abra `C:\StockOS\Liberar celular.cmd` e aceite o pedido de administrador do Windows. Ele libera a porta do celular só para aparelhos da mesma rede.

**Uma vez em cada celular:**
1. Conecte o celular no Wi-Fi da loja (o mesmo do notebook).
2. No Dashboard do notebook, aponte a câmera do celular para o código do quadro **Celular do atendente**, ou digite o endereço que aparece ali (algo como `https://192.168.0.10:8443`).
3. Na primeira vez, o navegador avisa que a conexão não é particular: toque em **Avançado** e **Continuar**.
4. Na tela que abrir, toque em **Primeira vez neste celular?** e siga o passo a passo para instalar o certificado da loja. A tela mostra o passo a passo do aparelho (Android ou iPhone). Depois disso o aviso some e a câmera passa a ler o cartão da comanda.
   - **iPhone:** abra o endereço no **Safari** (pelo Chrome do iPhone o perfil não instala; a tela avisa). Toque em "Baixar o perfil da loja", depois instale em Ajustes → Geral → VPN e Gerenciamento de Dispositivo, e ligue a confiança em Ajustes → Geral → Sobre → Ajustes de Confiança de Certificado.
5. Salve o endereço na tela inicial do celular (Android: menu do navegador, **Adicionar à tela inicial**; iPhone: Safari, Compartilhar, **Adicionar à Tela de Início**). No iPhone, o atalho da tela inicial pede o PIN uma vez a mais, porque guarda a entrada separada do Safari.

**Onde ver as comandas no notebook:** menu **Comandas**. Lá estão as abertas, o que cada uma tem e o botão **Cobrar no caixa**.

**No dia a dia:** toque no seu nome e digite o seu PIN. Abra a comanda pelo número ou pela câmera, toque nos produtos, confira o pedido e toque em **Lançar**. Tirar item já lançado e receber o pagamento continuam no caixa.

| Problema | O que tentar |
|---|---|
| O celular não abre o endereço | Celular e notebook no mesmo Wi-Fi; rode `Liberar celular.cmd` de novo |
| O endereço do Dashboard mudou | O roteador trocou o endereço do notebook: aponte a câmera para o código novo. O certificado instalado continua valendo |
| A câmera não abre | Instale o certificado da loja (passo 4) e permita a câmera no navegador. Enquanto isso, digite o número |
| "PIN incorreto" várias vezes | Depois de 5 erros, aquela pessoa espera 5 minutos |

## 10. Pedido impresso para a cozinha

Se a loja prepara porções, caldos ou petiscos, o pedido sai impresso na cozinha assim que é lançado. Bebida não vai.

**Uma vez:**
1. Instale a impressora térmica da cozinha no Windows do notebook (com o programa do fabricante). Pode ser a mesma do caixa, se a cozinha for perto.
2. No caixa, botão **Cozinha** → **Configurar a impressora da cozinha**.
3. Escolha a impressora, o papel (80 mm ou 58 mm) e marque o que vai para a cozinha (por exemplo, Porções e Caldos). Sem nada marcado, vão os itens cadastrados como preparo na hora.
4. **Salvar** e **Imprimir teste**.

**Quando sai o papel:** quando o atendente lança pelo celular, quando o caixa guarda a comanda (Esc) ou troca de comanda, e quando a venda de balcão é paga. O mesmo item não sai duas vezes. Se um item já impresso sai da comanda, sai um papel **CANCELAR**.

**Se a impressora estiver desligada ou sem papel:** o pedido fica guardado e sai sozinho quando ela voltar. O botão **Cozinha** mostra em vermelho quantos não saíram, e cada pedido pode ser reimpresso na janela.

## 11. Se algo der errado

| Problema | O que tentar |
|---|---|
| O navegador não abre o sistema | Use o atalho **StockOS** na Área de Trabalho: ele liga o que estiver desligado |
| "Pouco espaço livre" | Libere espaço no disco `C:` |
| O instalador diz que há "instalação incompleta" | Renomeie `C:\StockOS` e tente de novo |
| O banco não abre neste Windows | Instale o Visual C++ Redistributable (x64) da Microsoft |
| Precisa desligar tudo | `C:\StockOS\programa\scripts\parar.ps1`, ou reinicie o notebook |

**Registros:** `C:\StockOS\logs`.

## Limites

- **Só este notebook acessa o sistema completo.** Pela rede, só a tela de comanda do celular, com conexão segura e PIN (item 9). Um segundo computador de caixa em rede ainda não existe.
- **Atualização com troca da versão principal do banco** não é automática: o instalador recusa e não altera nada.
- **Remover o sistema:** apague o atalho da Área de Trabalho e o `StockOS (iniciar)` da pasta Inicializar, rode `parar.ps1` e apague `C:\StockOS`. Antes, confira se há backup fora dele.
