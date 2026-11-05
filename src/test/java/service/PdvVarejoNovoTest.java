package service;

import model.Caixa;
import model.CanalVenda;
import model.ClasseFuncionario;
import model.CodigoVenda;
import model.EstoqueProduto;
import model.FormaPagamento;
import model.MovimentacaoCaixa;
import model.Permissao;
import model.Produto;
import model.TipoMovimentacao;
import model.TipoMovimentacaoCaixa;
import model.Usuario;
import model.Venda;
import model.VendaPagamento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import repository.InMemoryCaixaRepository;
import repository.InMemoryCategoriaCardapioRepository;
import repository.InMemoryEstoqueProdutoRepository;
import repository.InMemoryFechamentoCaixaRepository;
import repository.InMemoryItemCardapioRepository;
import repository.InMemoryLogRepository;
import repository.InMemoryMovimentoEstoqueRepository;
import repository.InMemoryPedidoRepository;
import repository.InMemoryProdutoRepository;
import repository.InMemoryVendaPagamentoRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Correções do Caixa PDV para varejo de balcão (análise de 04/11/2026):
 * saldo único de estoque, gaveta só com dinheiro, pagamentos, estorno,
 * autorização de gerente, venda sem itens e código de barras.
 */
class PdvVarejoNovoTest {

    private static BigDecimal bd(String valor) {
        return new BigDecimal(valor);
    }

    private static void assertValor(String esperado, BigDecimal atual) {
        assertEquals(0, bd(esperado).compareTo(atual), () -> "esperado " + esperado + " mas foi " + atual);
    }

    private static Usuario usuario(long ru, String nome, Permissao... permissoes) {
        ClasseFuncionario classe = new ClasseFuncionario((int) ru, "CLASSE_" + ru, "Teste");
        for (Permissao p : permissoes) {
            classe.adicionarPermissao(p);
        }
        return new Usuario(ru, nome, "Teste", "529.982.247-25", "1234", classe, Usuario.Perfil.OPERADOR);
    }

    // ── Saldo único de estoque ────────────────────────────────────────────────

    @Nested
    class SaldoUnicoDeEstoque {
        private InMemoryProdutoRepository produtos;
        private InMemoryEstoqueProdutoRepository estoques;
        private InMemoryMovimentoEstoqueRepository movimentos;
        private EstoqueService estoqueService;
        private Usuario gerente;

        @BeforeEach
        void setUp() {
            produtos = new InMemoryProdutoRepository();
            estoques = new InMemoryEstoqueProdutoRepository();
            movimentos = new InMemoryMovimentoEstoqueRepository();
            SincronizadorEstoque sincronizador = new SincronizadorEstoque(produtos, estoques, movimentos);
            estoqueService = new EstoqueService(produtos, new InMemoryPedidoRepository(), sincronizador);
            gerente = usuario(10, "Gerente", Permissao.EDITAR_ESTOQUE, Permissao.VER_ESTOQUE, Permissao.VER_VENDAS);
        }

        private EstoqueProduto decimal(Produto produto) {
            return estoques.buscarPorFilialEProduto(1L, produto.getId()).orElseThrow();
        }

        @Test
        void cadastroComQuantidadeInicialEntraNoSaldoOficialENoEspelho() {
            Produto lata = estoqueService.cadastrarProduto(gerente, "Skol lata 350ml", 24, 12, bd("4.50"));

            assertValor("24", decimal(lata).getQuantidadeAtual());
            assertEquals(24, produtos.buscarPorId(lata.getId()).orElseThrow().getQuantidadeAtual());
            assertEquals(1, movimentos.listarTodos().size());
        }

        @Test
        void notaFiscalLevaCustoEOPdvPassaAVerOEstoque() {
            Produto lata = estoqueService.cadastrarProduto(gerente, "Brahma lata 350ml", 0, 12, bd("4.20"));

            estoqueService.registrarEntradaComCusto(lata.getId(), 120, bd("2.75"), gerente,
                    "Entrada via nota fiscal #7", EstoqueService.ORIGEM_NOTA_FISCAL, 7);

            EstoqueProduto oficial = decimal(lata);
            assertValor("120", oficial.getQuantidadeAtual());
            assertValor("2.75", oficial.getCustoMedio());
            assertEquals(120, produtos.buscarPorId(lata.getId()).orElseThrow().getQuantidadeAtual());
        }

        @Test
        void saidaManualUsaOSaldoOficialEAtualizaOEspelho() {
            Produto lata = estoqueService.cadastrarProduto(gerente, "Guarana lata", 10, 5, bd("4.00"));

            estoqueService.registrarMovimentacao(lata.getId(), 3, TipoMovimentacao.SAIDA, gerente, "Quebra");

            assertValor("7", decimal(lata).getQuantidadeAtual());
            assertEquals(7, produtos.buscarPorId(lata.getId()).orElseThrow().getQuantidadeAtual());
            assertThrows(IllegalArgumentException.class, () ->
                    estoqueService.registrarMovimentacao(lata.getId(), 8, TipoMovimentacao.SAIDA, gerente, "Demais"));
        }

        @Test
        void vendaQueBaixaSoODecimalEEspelhadaNoLegado() {
            Produto lata = estoqueService.cadastrarProduto(gerente, "Coca lata", 12, 6, bd("5.00"));
            SincronizadorEstoque sincronizador = new SincronizadorEstoque(produtos, estoques, movimentos);
            EstoqueProduto oficial = decimal(lata);

            // Simula a finalização do PDV: mexe só no decimal e pede o espelho.
            oficial.aplicarSaida(bd("5"), oficial.getCustoMedio(), true);
            estoques.salvar(oficial);
            sincronizador.espelharNoLegado(oficial);

            assertEquals(7, produtos.buscarPorId(lata.getId()).orElseThrow().getQuantidadeAtual());
        }

        @Test
        void espelhoArredondaParaBaixoENuncaFicaNegativo() {
            assertEquals(2, SincronizadorEstoque.quantidadeLegada(bd("2.999")));
            assertEquals(0, SincronizadorEstoque.quantidadeLegada(bd("-1.5")));
        }
    }

    // ── Gaveta só com dinheiro ────────────────────────────────────────────────

    @Nested
    class GavetaSoComDinheiro {
        private final Usuario operador = usuario(20, "Marina", Permissao.VER_VENDAS);

        @Test
        void pixECartaoNaoEntramNoSaldoDaGavetaMasEntramNoTotal() {
            Caixa caixa = new Caixa(1, 1);
            caixa.abrir(operador, 100.00);

            caixa.registrarMovimentacao(new MovimentacaoCaixa(0, TipoMovimentacaoCaixa.VENDA, bd("50.00"),
                    "Venda #1", operador, FormaPagamento.DINHEIRO, 1L));
            caixa.registrarMovimentacao(new MovimentacaoCaixa(0, TipoMovimentacaoCaixa.VENDA, bd("30.00"),
                    "Venda #2", operador, FormaPagamento.PIX, 2L));
            caixa.registrarMovimentacao(new MovimentacaoCaixa(0, TipoMovimentacaoCaixa.VENDA, bd("20.00"),
                    "Venda #2", operador, FormaPagamento.DEBITO, 2L));

            assertValor("150.00", caixa.getSaldoAtual());
            assertValor("100.00", caixa.calcularTotalVendas());
            assertValor("30.00", caixa.calcularVendasPorForma().get(FormaPagamento.PIX));
            assertEquals(2, caixa.contarVendas());
        }

        @Test
        void estornoNoPixNaoTiraDinheiroDaGavetaEEstornoEmDinheiroTira() {
            Caixa caixa = new Caixa(1, 1);
            caixa.abrir(operador, 10.00);
            caixa.registrarMovimentacao(new MovimentacaoCaixa(0, TipoMovimentacaoCaixa.VENDA, bd("200.00"),
                    "Venda #3", operador, FormaPagamento.PIX, 3L));

            // Antes: o estorno virava SANGRIA e falhava por "saldo insuficiente".
            caixa.registrarMovimentacao(new MovimentacaoCaixa(0, TipoMovimentacaoCaixa.ESTORNO_VENDA, bd("200.00"),
                    "Estorno venda #3", operador, FormaPagamento.PIX, 3L));
            assertValor("10.00", caixa.getSaldoAtual());
            assertValor("0.00", caixa.calcularTotalVendas());

            caixa.registrarMovimentacao(new MovimentacaoCaixa(0, TipoMovimentacaoCaixa.VENDA, bd("15.00"),
                    "Venda #4", operador, FormaPagamento.DINHEIRO, 4L));
            caixa.registrarMovimentacao(new MovimentacaoCaixa(0, TipoMovimentacaoCaixa.ESTORNO_VENDA, bd("5.00"),
                    "Estorno venda #4", operador, FormaPagamento.DINHEIRO, 4L));
            assertValor("20.00", caixa.getSaldoAtual());
            assertTrue(caixa.calcularTotalSaidas().compareTo(bd("205.00")) == 0);
        }

        @Test
        void vendaSemFormaDePagamentoNaoEAceita() {
            assertThrows(IllegalArgumentException.class, () -> new MovimentacaoCaixa(0, TipoMovimentacaoCaixa.VENDA,
                    bd("1.00"), "x", operador, null, null));
            assertThrows(IllegalArgumentException.class, () -> new MovimentacaoCaixa(0, TipoMovimentacaoCaixa.SANGRIA,
                    bd("1.00"), "x", operador, FormaPagamento.PIX, null));
        }
    }

    // ── Pagamentos da venda e estorno ─────────────────────────────────────────

    @Nested
    class PagamentosEEstorno {
        private CaixaService caixaService;
        private InMemoryVendaPagamentoRepository pagamentos;
        private IntegracaoCaixaLegadoService integracao;
        private final Usuario operador = usuario(30, "Lucas", Permissao.VER_VENDAS, Permissao.VER_FINANCAS);
        private final Venda venda = new Venda(77, 1L, "V-77", CanalVenda.BALCAO, 30L);

        @BeforeEach
        void setUp() {
            InMemoryProdutoRepository produtoRepository = new InMemoryProdutoRepository();
            caixaService = new CaixaService(
                    new InMemoryCaixaRepository(),
                    new InMemoryFechamentoCaixaRepository(),
                    new InMemoryLogRepository(),
                    new EstoqueService(produtoRepository, new InMemoryPedidoRepository()),
                    new CardapioService(new InMemoryCategoriaCardapioRepository(),
                            new InMemoryItemCardapioRepository(), produtoRepository));
            pagamentos = new InMemoryVendaPagamentoRepository();
            integracao = new IntegracaoCaixaLegadoService(caixaService, pagamentos);
            caixaService.abrirCaixa(operador, 1, 50.00);
        }

        private IntegracaoCaixaLegadoService.PagamentoInput pag(FormaPagamento forma, String valor, String recebido) {
            return new IntegracaoCaixaLegadoService.PagamentoInput(forma, bd(valor), recebido == null ? null : bd(recebido), null);
        }

        @Test
        void vendaMistaLancaUmaMovimentacaoPorFormaEGuardaOTroco() {
            integracao.registrarPagamentosEVenda(operador, venda, bd("70.00"), 1, List.of(
                    pag(FormaPagamento.PIX, "50.00", null),
                    pag(FormaPagamento.DINHEIRO, "20.00", "50.00")));

            Caixa caixa = caixaService.buscarCaixaAberto(1);
            assertValor("70.00", caixa.getSaldoAtual()); // 50 de fundo + 20 em dinheiro (troco já descontado)
            assertValor("70.00", caixa.calcularTotalVendas());
            assertEquals(1, caixa.contarVendas());
            List<VendaPagamento> gravados = pagamentos.listarPorVenda(77);
            assertEquals(2, gravados.size());
            assertValor("30.00", gravados.get(1).getTroco());
        }

        @Test
        void somaDosPagamentosPrecisaBaterComOTotal() {
            ErroNegocioException erro = assertThrows(ErroNegocioException.class, () ->
                    integracao.registrarPagamentosEVenda(operador, venda, bd("70.00"), 1,
                            List.of(pag(FormaPagamento.PIX, "60.00", null))));
            assertEquals("PAGAMENTO_DIVERGENTE", erro.getCodigo());
        }

        @Test
        void trocoSoEmDinheiro() {
            assertThrows(ErroNegocioException.class, () ->
                    integracao.registrarPagamentosEVenda(operador, venda, bd("10.00"), 1,
                            List.of(pag(FormaPagamento.CREDITO, "10.00", "20.00"))));
        }

        @Test
        void semPagamentosInformadosContinuaComoDinheiro() {
            integracao.registrarPagamentosEVenda(operador, venda, bd("12.00"), 1, null);
            assertValor("62.00", caixaService.buscarCaixaAberto(1).getSaldoAtual());
        }

        @Test
        void cancelamentoViraEstornoNaFormaPagaEletronicasPrimeiro() {
            integracao.registrarPagamentosEVenda(operador, venda, bd("50.00"), 1, List.of(
                    pag(FormaPagamento.PIX, "30.00", null),
                    pag(FormaPagamento.DINHEIRO, "20.00", null)));

            integracao.registrarCancelamentoConsolidado(operador, 1, 77, bd("40.00"), null, "Cliente desistiu");

            Caixa caixa = caixaService.buscarCaixaAberto(1);
            Map<FormaPagamento, BigDecimal> porForma = caixa.calcularVendasPorForma();
            assertValor("0.00", porForma.get(FormaPagamento.PIX));
            assertValor("10.00", porForma.get(FormaPagamento.DINHEIRO));
            assertValor("60.00", caixa.getSaldoAtual()); // 50 + 20 - 10 devolvidos em dinheiro
            assertFalse(caixa.getMovimentacoes().stream().anyMatch(m -> m.getTipo() == TipoMovimentacaoCaixa.SANGRIA));
        }

        @Test
        void estornoEletronicoComCaixaEncerradoNaoQuebraOCancelamento() {
            integracao.registrarPagamentosEVenda(operador, venda, bd("30.00"), 1,
                    List.of(pag(FormaPagamento.PIX, "30.00", null)));
            caixaService.encerrarCaixa(operador, 1, bd("50.00"), "");

            integracao.registrarCancelamentoConsolidado(operador, 1, 77, bd("30.00"), null, "Devolucao");
        }
    }

    // ── Autorização de gerente e venda sem itens ──────────────────────────────

    @Nested
    class AutorizacaoDeGerente {
        private CaixaService caixaService;
        private final Usuario operadorCaixa = usuario(40, "Ana", Permissao.VER_VENDAS, Permissao.VER_ESTOQUE);
        private final Usuario gerente = usuario(41, "Dona", Permissao.VER_VENDAS, Permissao.VER_FINANCAS);

        @BeforeEach
        void setUp() {
            InMemoryProdutoRepository produtoRepository = new InMemoryProdutoRepository();
            caixaService = new CaixaService(
                    new InMemoryCaixaRepository(),
                    new InMemoryFechamentoCaixaRepository(),
                    new InMemoryLogRepository(),
                    new EstoqueService(produtoRepository, new InMemoryPedidoRepository()),
                    new CardapioService(new InMemoryCategoriaCardapioRepository(),
                            new InMemoryItemCardapioRepository(), produtoRepository));
            caixaService.abrirCaixa(operadorCaixa, 2, 200.00);
        }

        @Test
        void operadorDeCaixaFazSangriaComAutorizacaoDoGerente() {
            assertThrows(SecurityException.class, () ->
                    caixaService.registrarSangria(operadorCaixa, 2, 100.00, "Cofre", null));

            caixaService.registrarSangria(operadorCaixa, 2, 100.00, "Cofre", gerente);

            Caixa caixa = caixaService.buscarCaixaAberto(2);
            assertValor("100.00", caixa.getSaldoAtual());
            MovimentacaoCaixa sangria = caixa.getMovimentacoes().get(0);
            assertEquals(operadorCaixa, sangria.getOperador());
            assertTrue(sangria.getDescricao().contains("autorizado por Dona Teste"));
        }

        @Test
        void vendaSoComValorSemItensExigeGerente() {
            assertThrows(SecurityException.class, () ->
                    caixaService.registrarVenda(operadorCaixa, 2, bd("15.00"), "Avulsa", List.of(),
                            FormaPagamento.DINHEIRO, null));

            caixaService.registrarVenda(operadorCaixa, 2, bd("15.00"), "Avulsa", List.of(),
                    FormaPagamento.PIX, gerente);
            Caixa caixa = caixaService.buscarCaixaAberto(2);
            assertValor("200.00", caixa.getSaldoAtual());
            assertValor("15.00", caixa.calcularTotalVendas());
        }

        @Test
        void encerramentoComparaSoODinheiro() {
            caixaService.registrarVendaPdv(operadorCaixa, 2, 9, Map.of(FormaPagamento.PIX, bd("80.00")));
            var fechamento = caixaService.encerrarCaixa(operadorCaixa, 2, bd("200.00"), "", gerente);
            assertValor("0.00", fechamento.getDivergencia());
        }
    }

    // ── Código de barras ──────────────────────────────────────────────────────

    @Nested
    class CodigoDeBarras {
        @Test
        void classificaEConfereDigitoVerificador() {
            assertEquals(CodigoVenda.Tipo.EAN13, CodigoVendaService.classificar("4006381333931"));
            assertEquals(CodigoVenda.Tipo.EAN8, CodigoVendaService.classificar("96385074"));
            assertEquals(CodigoVenda.Tipo.UPCA, CodigoVendaService.classificar("036000291452"));
            assertEquals(CodigoVenda.Tipo.INTERNO, CodigoVendaService.classificar("101"));
            assertEquals(CodigoVenda.Tipo.INTERNO, CodigoVendaService.classificar("GELO5"));
            assertThrows(IllegalArgumentException.class, () -> CodigoVendaService.classificar("4006381333932"));
            assertThrows(IllegalArgumentException.class, () -> CodigoVendaService.classificar("caldo de feijao"));
        }

        @Test
        void normalizaEspacosEEnterQueAlgunsLeitoresMandam() {
            assertEquals("4006381333931", CodigoVendaService.normalizar(" 4006381333931\r\n"));
        }
    }
}
