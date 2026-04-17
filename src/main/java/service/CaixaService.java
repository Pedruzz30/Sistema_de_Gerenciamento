package service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import model.Caixa;
import model.FechamentoCaixa;
import model.ItemCardapio;
import model.LogAcao;
import model.MovimentacaoCaixa;
import model.Permissao;
import model.TipoMovimentacaoCaixa;
import model.Usuario;
import org.springframework.transaction.annotation.Transactional;
import repository.CaixaRepository;
import repository.FechamentoCaixaRepository;
import repository.LogRepository;

/**
 * Gerencia o ciclo de vida dos caixas PDV.
 */
@Transactional(readOnly = true)
public class CaixaService {

    private final CaixaRepository caixaRepository;
    private final FechamentoCaixaRepository fechamentoCaixaRepository;
    private final LogRepository logRepository;
    private final EstoqueService estoqueService;
    private final CardapioService cardapioService;
    private final AtomicLong proximoIdMovimentacao;

    public CaixaService(CaixaRepository caixaRepository,
                        FechamentoCaixaRepository fechamentoCaixaRepository,
                        LogRepository logRepository,
                        EstoqueService estoqueService,
                        CardapioService cardapioService) {
        this.caixaRepository = caixaRepository;
        this.fechamentoCaixaRepository = fechamentoCaixaRepository;
        this.logRepository = logRepository;
        this.estoqueService = estoqueService;
        this.cardapioService = cardapioService;
        this.proximoIdMovimentacao = new AtomicLong(
                caixaRepository.listarTodos().stream()
                        .flatMap(caixa -> caixa.getMovimentacoes().stream())
                        .mapToLong(MovimentacaoCaixa::getId)
                        .max()
                        .orElse(0L) + 1
        );
    }

    @Transactional
    public Caixa abrirCaixa(Usuario operador, int numeroCaixa, double saldoInicial) {
        validarPermissao(operador, Permissao.VER_VENDAS);

        caixaRepository.buscarAbertoporNumero(numeroCaixa).ifPresent(c -> {
            throw new IllegalStateException(
                    "Caixa " + numeroCaixa + " ja esta aberto pelo operador: "
                            + c.getOperadorAtual().getNomeCompleto()
            );
        });

        Caixa caixa = new Caixa(0, numeroCaixa);
        caixa.abrir(operador, saldoInicial);
        caixa = caixaRepository.salvar(caixa);

        logRepository.salvar(new LogAcao(
                0, operador.getRu(),
                "CAIXA_ABERTO",
                "Caixa " + numeroCaixa + " aberto com saldo inicial R$"
                        + formatarMoeda(BigDecimal.valueOf(saldoInicial))
        ));

        return caixa;
    }

    @Transactional
    public void registrarVenda(Usuario operador, int numeroCaixa,
                               double valor, String descricao) {
        registrarVenda(operador, numeroCaixa, BigDecimal.valueOf(valor), descricao, List.of());
    }

    @Transactional
    public void registrarVenda(Usuario operador,
                               int numeroCaixa,
                               BigDecimal valorInformado,
                               String descricao,
                               List<VendaItemInput> itensVendidos) {
        validarPermissao(operador, Permissao.VER_VENDAS);

        BigDecimal valorVenda = normalizarValorVenda(valorInformado, itensVendidos, operador, numeroCaixa, descricao);
        registrarMovimentacao(operador, numeroCaixa,
                TipoMovimentacaoCaixa.VENDA, valorVenda.doubleValue(), descricao);
    }

    @Transactional
    public void registrarSangria(Usuario operador, int numeroCaixa,
                                 double valor, String descricao) {
        validarPermissao(operador, Permissao.VER_FINANCAS);
        registrarMovimentacao(operador, numeroCaixa,
                TipoMovimentacaoCaixa.SANGRIA, valor, descricao);
    }

    @Transactional
    public void registrarSuprimento(Usuario operador, int numeroCaixa,
                                    double valor, String descricao) {
        validarPermissao(operador, Permissao.VER_FINANCAS);
        registrarMovimentacao(operador, numeroCaixa,
                TipoMovimentacaoCaixa.SUPRIMENTO, valor, descricao);
    }

    private void registrarMovimentacao(Usuario operador, int numeroCaixa,
                                       TipoMovimentacaoCaixa tipo,
                                       double valor, String descricao) {
        Caixa caixa = buscarCaixaAbertoOuLancar(numeroCaixa);

        MovimentacaoCaixa mov = new MovimentacaoCaixa(
                proximoIdMovimentacao.getAndIncrement(),
                tipo,
                BigDecimal.valueOf(valor),
                descricao,
                operador
        );
        caixa.registrarMovimentacao(mov);
        caixaRepository.salvar(caixa);

        logRepository.salvar(new LogAcao(
                0,
                operador.getRu(),
                "MOVIMENTACAO_CAIXA",
                String.format(
                        "Caixa %d: %s de R$%s (%s)",
                        numeroCaixa,
                        tipo.name(),
                        formatarMoeda(BigDecimal.valueOf(valor)),
                        (descricao == null || descricao.isBlank()) ? "sem descricao" : descricao
                )
        ));
    }

    @Transactional
    public FechamentoCaixa encerrarCaixa(Usuario operador, int numeroCaixa) {
        validarPermissao(operador, Permissao.VER_FINANCAS);
        Caixa caixa = buscarCaixaAbertoOuLancar(numeroCaixa);
        return encerrarCaixaInterno(operador, caixa, caixa.getSaldoAtual(), "");
    }

    @Transactional
    public FechamentoCaixa encerrarCaixa(Usuario operador,
                                         int numeroCaixa,
                                         BigDecimal valorContado,
                                         String observacao) {
        validarPermissao(operador, Permissao.VER_FINANCAS);
        Caixa caixa = buscarCaixaAbertoOuLancar(numeroCaixa);
        return encerrarCaixaInterno(operador, caixa, valorContado, observacao);
    }

    private FechamentoCaixa encerrarCaixaInterno(Usuario operador,
                                                 Caixa caixa,
                                                 BigDecimal valorContado,
                                                 String observacao) {
        BigDecimal valorSistema = caixa.getSaldoAtual().setScale(2, RoundingMode.HALF_UP);
        BigDecimal valorContadoNormalizado = normalizarValorMonetario(valorContado, "Valor contado e obrigatorio.");
        if (valorContadoNormalizado.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Valor contado nao pode ser negativo.");
        }
        BigDecimal divergencia = valorContadoNormalizado
                .subtract(valorSistema)
                .setScale(2, RoundingMode.HALF_UP);

        caixa.encerrar();
        caixa = caixaRepository.salvar(caixa);

        FechamentoCaixa fechamento = FechamentoCaixa.gerar(
                caixa,
                operador,
                valorSistema,
                valorContadoNormalizado,
                divergencia,
                observacao
        );
        fechamento = fechamentoCaixaRepository.salvar(fechamento);

        logRepository.salvar(new LogAcao(
                0, operador.getRu(),
                "CAIXA_ENCERRADO",
                String.format("Caixa %d encerrado por %s. Valor sistema: R$%s | Valor contado: R$%s | Divergencia: R$%s",
                        caixa.getNumeroCaixa(),
                        operador.getNomeCompleto(),
                        formatarMoeda(fechamento.getValorSistema()),
                        formatarMoeda(fechamento.getValorContado()),
                        formatarMoeda(fechamento.getDivergencia()))
        ));

        return fechamento;
    }

    private BigDecimal normalizarValorMonetario(BigDecimal valor, String mensagemQuandoNulo) {
        if (valor == null) {
            throw new IllegalArgumentException(mensagemQuandoNulo);
        }
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    public String consolidarDia(LocalDate data) {
        List<Caixa> encerrados = caixaRepository.buscarEncerradosPorData(data);

        if (encerrados.isEmpty()) {
            return "Nenhum caixa encerrado em " + data + ".";
        }

        BigDecimal totalVendas = somar(encerrados.stream().map(Caixa::calcularTotalVendas).toList());
        BigDecimal totalEntradas = somar(encerrados.stream().map(Caixa::calcularTotalEntradas).toList());
        BigDecimal totalSaidas = somar(encerrados.stream().map(Caixa::calcularTotalSaidas).toList());
        BigDecimal saldoTotal = somar(encerrados.stream().map(Caixa::getSaldoAtual).toList());

        String detalhesCaixas = encerrados.stream()
                .map(c -> String.format("  Caixa %d: vendas R$%s | saldo R$%s",
                        c.getNumeroCaixa(),
                        formatarMoeda(c.calcularTotalVendas()),
                        formatarMoeda(c.getSaldoAtual())))
                .collect(Collectors.joining("\n"));

        return String.format("""
                CONSOLIDADO DO DIA %s
                %s
                ------------------------------
                Total vendas:    R$%s
                Total entradas:  R$%s
                Total saidas:    R$%s
                Saldo total:     R$%s
                Caixas fechados: %d
                """,
                data,
                detalhesCaixas,
                formatarMoeda(totalVendas),
                formatarMoeda(totalEntradas),
                formatarMoeda(totalSaidas),
                formatarMoeda(saldoTotal),
                encerrados.size());
    }

    public Caixa buscarCaixaAberto(int numeroCaixa) {
        return buscarCaixaAbertoOuLancar(numeroCaixa);
    }

    public Caixa buscarCaixaPorId(long caixaId) {
        return buscarCaixaPorIdOuLancar(caixaId);
    }

    public List<MovimentacaoCaixa> listarMovimentacoesPorCaixaId(long caixaId) {
        return buscarCaixaPorIdOuLancar(caixaId).getMovimentacoes();
    }

    public List<Caixa> buscarHistoricoPorCaixaId(long caixaId) {
        Caixa caixa = buscarCaixaPorIdOuLancar(caixaId);
        return caixaRepository.buscarHistoricoPorNumero(caixa.getNumeroCaixa());
    }

    private Caixa buscarCaixaAbertoOuLancar(int numeroCaixa) {
        return caixaRepository.buscarAbertoporNumero(numeroCaixa)
                .orElseThrow(() -> new IllegalStateException(
                        "Caixa " + numeroCaixa + " nao esta aberto."
                ));
    }

    private Caixa buscarCaixaPorIdOuLancar(long caixaId) {
        return caixaRepository.buscarPorId(caixaId)
                .orElseThrow(() -> new IllegalStateException(
                        "Sessao de caixa " + caixaId + " nao encontrada."
                ));
    }

    private void validarPermissao(Usuario usuario, Permissao permissao) {
        if (usuario == null
                || usuario.getClasse() == null
                || !usuario.getClasse().possuiPermissao(permissao)) {
            throw new SecurityException(
                    "Voce nao tem permissao para esta operacao."
            );
        }
    }

    private BigDecimal somar(List<BigDecimal> valores) {
        return valores.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String formatarMoeda(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private BigDecimal normalizarValorVenda(BigDecimal valorInformado,
                                            List<VendaItemInput> itensVendidos,
                                            Usuario operador,
                                            int numeroCaixa,
                                            String descricao) {
        List<VendaItemInput> itens = itensVendidos == null ? List.of() : itensVendidos;
        if (!itens.isEmpty()) {
            return processarVendaComItens(operador, numeroCaixa, descricao, itens);
        }

        if (valorInformado == null || valorInformado.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Valor deve ser maior que zero.");
        }
        return valorInformado.setScale(2, RoundingMode.HALF_UP);
    }

    private String formatarDescricao(String descricao) {
        if (descricao == null || descricao.isBlank()) {
            return ".";
        }
        return ": " + descricao.trim();
    }

    private BigDecimal processarVendaComItens(Usuario operador,
                                              int numeroCaixa,
                                              String descricao,
                                              List<VendaItemInput> itensVendidos) {
        List<VendaItemInput> itens = itensVendidos == null ? List.of() : itensVendidos;
        if (itens.isEmpty()) {
            throw new IllegalArgumentException("A venda deve informar ao menos um item.");
        }

        String descricaoOrigem = "Saida via venda no caixa " + numeroCaixa + formatarDescricao(descricao);
        List<VendaItemInput> itensEstoqueLegados = new ArrayList<>();
        List<VendaItemInput> itensEstoqueViaCardapio = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (VendaItemInput item : itens) {
            if (item == null || !item.possuiReferenciaValida()) {
                throw new IllegalArgumentException(
                        "Itens da venda devem informar produtoId ou itemCardapioId, nunca ambos."
                );
            }

            if (item.possuiProdutoEmEstoque()) {
                itensEstoqueLegados.add(item);
                continue;
            }

            ItemCardapio itemCardapio = cardapioService.buscarItemAtivoPorId(item.itemCardapioId());
            total = total.add(itemCardapio.getPrecoVenda().multiply(BigDecimal.valueOf(item.quantidade())));

            if (!itemCardapio.isEstoqueDireto()) {
                continue;
            }
            if (!itemCardapio.possuiProdutoVinculado()) {
                continue;
            }
            if (!itemCardapio.getProdutoVinculado().isControladoPorEstoque()) {
                throw new IllegalArgumentException(
                        "Produto vinculado ao item de cardapio nao esta controlado por estoque: " + itemCardapio.getNome()
                );
            }

            itensEstoqueViaCardapio.add(new VendaItemInput(itemCardapio.getProdutoVinculado().getId(), item.quantidade()));
        }

        if (!itensEstoqueLegados.isEmpty()) {
            total = total.add(estoqueService.registrarSaidasPorVenda(operador, itensEstoqueLegados, descricaoOrigem));
        }
        if (!itensEstoqueViaCardapio.isEmpty()) {
            estoqueService.registrarSaidasPorVenda(operador, itensEstoqueViaCardapio, descricaoOrigem);
        }

        return total.setScale(2, RoundingMode.HALF_UP);
    }
}
