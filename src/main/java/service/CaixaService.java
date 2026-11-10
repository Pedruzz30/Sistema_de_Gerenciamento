package service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import model.Caixa;
import model.FechamentoCaixa;
import model.FormaPagamento;
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
        registrarVenda(operador, numeroCaixa, valorInformado, descricao, itensVendidos, FormaPagamento.DINHEIRO, null);
    }

    /**
     * Venda pela rota antiga ({@code /api/caixas/venda}).
     *
     * <p>Venda "so com valor", sem itens, nao baixa estoque. Por isso ela so e
     * aceita com permissao de gerente ({@code VER_FINANCAS}): do proprio
     * operador ou de um gerente que autorize na hora. O PDV usa
     * {@code /api/vendas}.
     */
    @Transactional
    public void registrarVenda(Usuario operador,
                               int numeroCaixa,
                               BigDecimal valorInformado,
                               String descricao,
                               List<VendaItemInput> itensVendidos,
                               FormaPagamento formaPagamento,
                               Usuario autorizador) {
        validarPermissao(operador, Permissao.VER_VENDAS);
        boolean semItens = itensVendidos == null || itensVendidos.isEmpty();
        Usuario autorizou = null;
        if (semItens) {
            autorizou = validarPermissaoOuAutorizacao(operador, autorizador, Permissao.VER_FINANCAS,
                    "Venda sem itens nao baixa estoque e exige autorizacao do gerente. Use o PDV.");
        }

        BigDecimal valorVenda = normalizarValorVenda(valorInformado, itensVendidos, operador, numeroCaixa, descricao);
        registrarMovimentacao(operador, numeroCaixa, TipoMovimentacaoCaixa.VENDA, valorVenda,
                anotarAutorizacao(descricao, autorizou),
                formaPagamento == null ? FormaPagamento.DINHEIRO : formaPagamento, null);
    }

    /**
     * Registra no caixa uma venda do PDV ({@code /api/vendas}) ja finalizada,
     * uma movimentacao por forma de pagamento. So o dinheiro entra no saldo
     * da gaveta (o troco ja vem descontado do valor).
     */
    @Transactional
    public void registrarVendaPdv(Usuario operador,
                                  int numeroCaixa,
                                  long vendaId,
                                  java.util.Map<FormaPagamento, BigDecimal> valoresPorForma) {
        validarPermissao(operador, Permissao.VER_VENDAS);
        if (valoresPorForma == null || valoresPorForma.isEmpty()) {
            throw new IllegalArgumentException("Venda sem pagamentos.");
        }
        valoresPorForma.forEach((forma, valor) -> {
            if (valor != null && valor.compareTo(BigDecimal.ZERO) > 0) {
                registrarMovimentacao(operador, numeroCaixa, TipoMovimentacaoCaixa.VENDA,
                        valor.setScale(2, RoundingMode.HALF_UP),
                        "Venda #" + vendaId + " (" + forma.getLabel() + ")",
                        forma, vendaId);
            }
        });
    }

    /**
     * Devolucao ao cliente de uma venda cancelada (total ou parcial). Em
     * dinheiro sai da gaveta; nas outras formas so abate os totais.
     */
    @Transactional
    public void registrarEstornoVenda(Usuario operador,
                                      int numeroCaixa,
                                      long vendaId,
                                      FormaPagamento forma,
                                      BigDecimal valor,
                                      String motivo) {
        exigirOperadorDeCaixa(operador);
        if (forma == null) {
            throw new IllegalArgumentException("Forma de pagamento do estorno e obrigatoria.");
        }
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Valor do estorno deve ser maior que zero.");
        }
        String descricao = "Estorno venda #" + vendaId + " (" + forma.getLabel() + ")"
                + (motivo == null || motivo.isBlank() ? "" : ": " + motivo.trim());
        registrarMovimentacao(operador, numeroCaixa, TipoMovimentacaoCaixa.ESTORNO_VENDA,
                valor.setScale(2, RoundingMode.HALF_UP), descricao, forma, vendaId);
    }

    @Transactional
    public void registrarSangria(Usuario operador, int numeroCaixa,
                                 double valor, String descricao) {
        registrarSangria(operador, numeroCaixa, valor, descricao, null);
    }

    /**
     * Sangria. Exige {@code VER_FINANCAS} do operador ou a autorizacao de um
     * gerente na hora (o operador de caixa continua logado).
     */
    @Transactional
    public void registrarSangria(Usuario operador, int numeroCaixa,
                                 double valor, String descricao, Usuario autorizador) {
        exigirOperadorDeCaixa(operador);
        Usuario autorizou = validarPermissaoOuAutorizacao(operador, autorizador, Permissao.VER_FINANCAS,
                "Sangria exige autorizacao do gerente.");
        registrarMovimentacao(operador, numeroCaixa, TipoMovimentacaoCaixa.SANGRIA,
                BigDecimal.valueOf(valor), anotarAutorizacao(descricao, autorizou), null, null);
    }

    @Transactional
    public void registrarSuprimento(Usuario operador, int numeroCaixa,
                                    double valor, String descricao) {
        registrarSuprimento(operador, numeroCaixa, valor, descricao, null);
    }

    @Transactional
    public void registrarSuprimento(Usuario operador, int numeroCaixa,
                                    double valor, String descricao, Usuario autorizador) {
        exigirOperadorDeCaixa(operador);
        Usuario autorizou = validarPermissaoOuAutorizacao(operador, autorizador, Permissao.VER_FINANCAS,
                "Suprimento exige autorizacao do gerente.");
        registrarMovimentacao(operador, numeroCaixa, TipoMovimentacaoCaixa.SUPRIMENTO,
                BigDecimal.valueOf(valor), anotarAutorizacao(descricao, autorizou), null, null);
    }

    private void registrarMovimentacao(Usuario operador, int numeroCaixa,
                                       TipoMovimentacaoCaixa tipo,
                                       BigDecimal valorMovimento, String descricao,
                                       FormaPagamento formaPagamento, Long vendaId) {
        Caixa caixa = buscarCaixaAbertoOuLancar(numeroCaixa);
        double valor = valorMovimento.doubleValue();

        // ID 0: o banco gera (sequence movimentacoes_caixa_id_seq).
        MovimentacaoCaixa mov = new MovimentacaoCaixa(
                0,
                tipo,
                valorMovimento,
                descricao,
                operador,
                formaPagamento,
                vendaId
        );
        caixa.registrarMovimentacao(mov);
        caixaRepository.salvar(caixa);

        logRepository.salvar(new LogAcao(
                0,
                operador.getRu(),
                "MOVIMENTACAO_CAIXA",
                String.format(
                        "Caixa %d: %s%s de R$%s (%s)",
                        numeroCaixa,
                        tipo.name(),
                        formaPagamento == null ? "" : " " + formaPagamento.name(),
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
        return encerrarCaixa(operador, numeroCaixa, valorContado, observacao, null);
    }

    /**
     * Fechamento cego: o operador informa o dinheiro contado sem ver o valor
     * esperado; a divergencia aparece so no resultado. Exige
     * {@code VER_FINANCAS} ou autorizacao de gerente.
     */
    @Transactional
    public FechamentoCaixa encerrarCaixa(Usuario operador,
                                         int numeroCaixa,
                                         BigDecimal valorContado,
                                         String observacao,
                                         Usuario autorizador) {
        exigirOperadorDeCaixa(operador);
        Usuario autorizou = validarPermissaoOuAutorizacao(operador, autorizador, Permissao.VER_FINANCAS,
                "Encerramento de caixa exige autorizacao do gerente.");
        Caixa caixa = buscarCaixaAbertoOuLancar(numeroCaixa);
        return encerrarCaixaInterno(operador, caixa, valorContado, anotarAutorizacao(observacao, autorizou));
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

    /**
     * Aceita a operacao se o operador tem a permissao ou se um gerente ativo
     * com a permissao autorizou. Retorna quem autorizou (ou {@code null} se o
     * proprio operador tinha a permissao).
     */
    private Usuario validarPermissaoOuAutorizacao(Usuario operador,
                                                  Usuario autorizador,
                                                  Permissao permissao,
                                                  String mensagem) {
        if (possuiPermissao(operador, permissao)) {
            return null;
        }
        if (autorizador != null && autorizador.isAtivo() && possuiPermissao(autorizador, permissao)) {
            return autorizador;
        }
        throw new SecurityException(mensagem);
    }

    /** Quem opera o caixa: tem vendas ou finanças (perfil só financeiro continua podendo). */
    private static void exigirOperadorDeCaixa(Usuario operador) {
        if (!possuiPermissao(operador, Permissao.VER_VENDAS) && !possuiPermissao(operador, Permissao.VER_FINANCAS)) {
            throw new SecurityException("Voce nao tem permissao para esta operacao.");
        }
    }

    private static boolean possuiPermissao(Usuario usuario, Permissao permissao) {
        return usuario != null
                && usuario.getClasse() != null
                && usuario.getClasse().possuiPermissao(permissao);
    }

    private static String anotarAutorizacao(String texto, Usuario autorizou) {
        String base = texto == null ? "" : texto.trim();
        if (autorizou == null) {
            return base;
        }
        String nota = "[autorizado por " + autorizou.getNomeCompleto() + "]";
        return base.isEmpty() ? nota : base + " " + nota;
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
