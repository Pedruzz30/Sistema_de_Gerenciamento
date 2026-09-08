package service;

import model.CategoriaEstoque;
import model.ClasseFuncionario;
import model.CompraEstoque;
import model.EmbalagemProduto;
import model.EstoqueProduto;
import model.ItemCompraEstoque;
import model.MovimentoEstoque;
import model.Permissao;
import model.PoliticaFalta;
import model.Produto;
import model.TipoMovimentoEstoque;
import model.UnidadeMedida;
import model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import repository.InMemoryCompraEstoqueRepository;
import repository.InMemoryEmbalagemProdutoRepository;
import repository.InMemoryEstoqueProdutoRepository;
import repository.InMemoryFornecedorRepository;
import repository.InMemoryMovimentoEstoqueRepository;
import repository.InMemoryProdutoRepository;
import repository.InMemoryRegistroIdempotenciaRepository;
import repository.InMemoryUnidadeMedidaRepository;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompraEstoqueServiceTest {

    private InMemoryProdutoRepository produtoRepository;
    private InMemoryCompraEstoqueRepository compraRepository;
    private InMemoryEstoqueProdutoRepository estoqueRepository;
    private InMemoryMovimentoEstoqueRepository movimentoRepository;
    private InMemoryEmbalagemProdutoRepository embalagemRepository;
    private UnidadeMedidaService unidadeService;
    private ProdutoEstoqueService produtoEstoqueService;
    private CompraEstoqueService compraService;
    private MovimentoEstoqueService movimentoService;
    private Usuario gerente;
    private UnidadeMedida grama;
    private UnidadeMedida unidade;
    private Produto mussarela;

    @BeforeEach
    void setUp() {
        produtoRepository = new InMemoryProdutoRepository();
        compraRepository = new InMemoryCompraEstoqueRepository();
        estoqueRepository = new InMemoryEstoqueProdutoRepository();
        movimentoRepository = new InMemoryMovimentoEstoqueRepository();
        embalagemRepository = new InMemoryEmbalagemProdutoRepository();
        unidadeService = new UnidadeMedidaService(new InMemoryUnidadeMedidaRepository());
        produtoEstoqueService = new ProdutoEstoqueService(
                produtoRepository,
                embalagemRepository,
                estoqueRepository,
                unidadeService
        );
        compraService = new CompraEstoqueService(
                compraRepository,
                produtoRepository,
                new InMemoryFornecedorRepository(),
                embalagemRepository,
                estoqueRepository,
                movimentoRepository,
                new InMemoryRegistroIdempotenciaRepository(),
                unidadeService
        );
        movimentoService = new MovimentoEstoqueService(movimentoRepository, estoqueRepository);

        ClasseFuncionario classe = new ClasseFuncionario(1, "GERENTE_ESTOQUE", "Gerente");
        classe.adicionarPermissao(Permissao.EDITAR_ESTOQUE);
        classe.adicionarPermissao(Permissao.VER_ESTOQUE);
        classe.adicionarPermissao(Permissao.VER_COMPRAS);
        gerente = new Usuario(1, "Maria", "Gerente", "111.444.777-35", "1234", classe, Usuario.Perfil.OPERADOR);

        grama = unidadeService.buscarPorCodigoObrigatorio("GRAMA");
        unidade = unidadeService.buscarPorCodigoObrigatorio("UNIDADE");
        mussarela = salvarProduto("Mussarela", grama);
    }

    @Test
    void embalagemDeDozeUnidadesGeraDozeUnidadesBase() {
        Produto refrigerante = salvarProduto("Refrigerante", unidade);
        EmbalagemProduto caixa = produtoEstoqueService.cadastrarEmbalagem(
                gerente,
                refrigerante.getId(),
                "Caixa com 12 latas",
                new BigDecimal("12"),
                "UNIDADE",
                null,
                Boolean.TRUE
        );

        CompraEstoque compra = compraService.criarCompra(gerente, null);
        compra = compraService.adicionarItem(
                gerente,
                compra.getId(),
                refrigerante.getId(),
                caixa.getId(),
                null,
                BigDecimal.ONE,
                new BigDecimal("120.00"),
                null
        );

        ItemCompraEstoque item = compra.getItens().get(0);
        assertBigDecimalEquals("12.000000", item.getQuantidadeBase());
        assertBigDecimalEquals("10.00000000", item.getCustoUnitarioBase());
    }

    @Test
    void compraInicialDefineCustoMedioEMovimentoComSaldos() {
        EmbalagemProduto pacote = pacoteMussarela5kg();
        CompraEstoque compra = compraService.criarCompra(gerente, null);
        compra = compraService.adicionarItem(
                gerente,
                compra.getId(),
                mussarela.getId(),
                pacote.getId(),
                null,
                new BigDecimal("2"),
                new BigDecimal("300.00"),
                null
        );

        compraService.confirmarCompra(gerente, compra.getId(), "mussarela-1", "Entrada inicial");

        EstoqueProduto estoque = estoqueRepository.buscarPorFilialEProduto(1L, mussarela.getId()).orElseThrow();
        MovimentoEstoque movimento = movimentoRepository.listarTodos().get(0);
        assertBigDecimalEquals("10000.000000", estoque.getQuantidadeAtual());
        assertBigDecimalEquals("0.03000000", estoque.getCustoMedio());
        assertEquals(TipoMovimentoEstoque.ENTRADA_COMPRA, movimento.getTipo());
        assertBigDecimalEquals("0.000000", movimento.getSaldoAnterior());
        assertBigDecimalEquals("10000.000000", movimento.getSaldoPosterior());
        assertBigDecimalEquals("0.00000000", movimento.getCustoMedioAnterior());
        assertBigDecimalEquals("0.03000000", movimento.getCustoMedioPosterior());
        assertBigDecimalEquals("0.0000", movimento.getValorEstoqueAnterior());
        assertBigDecimalEquals("300.0000", movimento.getValorEstoquePosterior());
        assertBigDecimalEquals("0.03000000", movimento.getCustoUnitario());
        assertBigDecimalEquals("300.0000", movimento.getCustoTotal());
        assertEquals(gerente.getRu(), movimento.getUsuarioId());
    }

    @Test
    void segundaCompraAtualizaCustoMedioPonderado() {
        CompraEstoque primeira = compraComEmbalagem("2", "300.00", "key-1");
        CompraEstoque segunda = compraService.criarCompra(gerente, null);
        segunda = compraService.adicionarItem(
                gerente,
                segunda.getId(),
                mussarela.getId(),
                null,
                "GRAMA",
                new BigDecimal("5000"),
                new BigDecimal("180.00"),
                null
        );

        compraService.confirmarCompra(gerente, segunda.getId(), "key-2", "Segunda entrada");

        EstoqueProduto estoque = estoqueRepository.buscarPorFilialEProduto(1L, mussarela.getId()).orElseThrow();
        assertTrue(primeira.estaConfirmada());
        assertBigDecimalEquals("15000.000000", estoque.getQuantidadeAtual());
        assertBigDecimalEquals("0.03200000", estoque.getCustoMedio());
    }

    @Test
    void falhaNaPreValidacaoDaConfirmacaoNaoAlteraSaldoParcial() {
        Produto produtoInvalido = produtoRepository.salvar(new Produto(
                0,
                "Produto sem unidade",
                0,
                0,
                BigDecimal.ZERO,
                CategoriaEstoque.SECO,
                Boolean.TRUE
        ));
        CompraEstoque compra = compraService.criarCompra(gerente, null);
        compra.adicionarItem(new ItemCompraEstoque(
                0,
                mussarela,
                null,
                grama,
                new BigDecimal("100"),
                new BigDecimal("100"),
                new BigDecimal("10.00")
        ));
        compra.adicionarItem(new ItemCompraEstoque(
                0,
                produtoInvalido,
                null,
                grama,
                new BigDecimal("100"),
                new BigDecimal("100"),
                new BigDecimal("10.00")
        ));
        compraRepository.salvar(compra);

        assertThrows(IllegalArgumentException.class,
                () -> compraService.confirmarCompra(gerente, compra.getId(), "falha", "Falha esperada"));

        assertTrue(estoqueRepository.buscarPorFilialEProduto(1L, mussarela.getId()).isEmpty());
        assertTrue(movimentoRepository.listarTodos().isEmpty());
    }

    @Test
    void idempotenciaImpedeEntradaDuplicada() {
        CompraEstoque compra = compraComEmbalagem("2", "300.00", "dup-1");
        CompraEstoque repetida = compraService.confirmarCompra(gerente, compra.getId(), "dup-1", "Retry");

        EstoqueProduto estoque = estoqueRepository.buscarPorFilialEProduto(1L, mussarela.getId()).orElseThrow();
        assertEquals(compra.getId(), repetida.getId());
        assertBigDecimalEquals("10000.000000", estoque.getQuantidadeAtual());
        assertEquals(1, movimentoRepository.listarTodos().size());
    }

    @Test
    void mesmaChaveComPayloadDiferenteRetornaConflito() {
        compraComEmbalagem("2", "300.00", "mesma-chave");
        CompraEstoque outra = compraService.criarCompra(gerente, null);
        outra = compraService.adicionarItem(
                gerente,
                outra.getId(),
                mussarela.getId(),
                null,
                "GRAMA",
                new BigDecimal("5000"),
                new BigDecimal("180.00"),
                null
        );

        long outraId = outra.getId();
        assertThrows(ConflitoIdempotenciaException.class,
                () -> compraService.confirmarCompra(gerente, outraId, "mesma-chave", "Conflito"));
    }

    @Test
    void estornoCriaMovimentoInversoEBloqueiaSegundoEstorno() {
        compraComEmbalagem("2", "300.00", "estorno-1");
        CompraEstoque segunda = compraService.criarCompra(gerente, null);
        segunda = compraService.adicionarItem(
                gerente,
                segunda.getId(),
                mussarela.getId(),
                null,
                "GRAMA",
                new BigDecimal("5000"),
                new BigDecimal("180.00"),
                null
        );
        compraService.confirmarCompra(gerente, segunda.getId(), "estorno-2", "Segunda entrada");
        long segundaCompraId = segunda.getId();
        long segundaMovimentoId = movimentoRepository.listarTodos().stream()
                .filter(m -> m.getOrigemId().equals(segundaCompraId))
                .map(MovimentoEstoque::getId)
                .findFirst()
                .orElseThrow();

        MovimentoEstoque estorno = movimentoService.estornar(
                gerente,
                segundaMovimentoId,
                "Cancelamento da segunda entrada",
                "Teste"
        );

        EstoqueProduto estoque = estoqueRepository.buscarPorFilialEProduto(1L, mussarela.getId()).orElseThrow();
        assertEquals(TipoMovimentoEstoque.ESTORNO_MOVIMENTO, estorno.getTipo());
        assertEquals(segundaMovimentoId, estorno.getMovimentoEstornado().getId());
        assertBigDecimalEquals("15000.000000", estorno.getSaldoAnterior());
        assertBigDecimalEquals("10000.000000", estorno.getSaldoPosterior());
        assertBigDecimalEquals("10000.000000", estoque.getQuantidadeAtual());
        assertBigDecimalEquals("0.03000000", estoque.getCustoMedio());
        assertBigDecimalEquals("0.03200000", estorno.getCustoMedioAnterior());
        assertBigDecimalEquals("0.03000000", estorno.getCustoMedioPosterior());
        assertBigDecimalEquals("480.0000", estorno.getValorEstoqueAnterior());
        assertBigDecimalEquals("300.0000", estorno.getValorEstoquePosterior());
        ErroNegocioException erro = assertThrows(ErroNegocioException.class,
                () -> movimentoService.estornar(gerente, segundaMovimentoId, "Segundo estorno", null));
        assertEquals("MOVIMENTO_JA_ESTORNADO", erro.getCodigo());
    }

    @Test
    void estornoComConsumoPosteriorInsuficienteEbloqueadoSemMovimentoParcial() {
        compraComEmbalagem("2", "300.00", "bloq-1");
        CompraEstoque segunda = compraService.criarCompra(gerente, null);
        segunda = compraService.adicionarItem(
                gerente,
                segunda.getId(),
                mussarela.getId(),
                null,
                "GRAMA",
                new BigDecimal("5000"),
                new BigDecimal("180.00"),
                null
        );
        compraService.confirmarCompra(gerente, segunda.getId(), "bloq-2", "Segunda entrada");
        long segundaCompraId = segunda.getId();
        MovimentoEstoque movimentoSegunda = movimentoRepository.listarTodos().stream()
                .filter(m -> m.getOrigemId().equals(segundaCompraId))
                .findFirst()
                .orElseThrow();

        EstoqueProduto estoque = estoqueRepository.buscarPorFilialEProduto(1L, mussarela.getId()).orElseThrow();
        estoque.aplicarSaida(new BigDecimal("11000"), estoque.getCustoMedio(), true);
        estoqueRepository.salvar(estoque);
        int movimentosAntes = movimentoRepository.listarTodos().size();

        ErroNegocioException erro = assertThrows(ErroNegocioException.class,
                () -> movimentoService.estornar(gerente, movimentoSegunda.getId(), "Estorno sem saldo", "Teste"));

        assertEquals("MOVIMENTO_NAO_PODE_SER_ESTORNADO", erro.getCodigo());
        assertEquals("ESTOQUE_INSUFICIENTE_PARA_ESTORNO", erro.getDetalhes().get("motivoBloqueio"));
        assertEquals(movimentosAntes, movimentoRepository.listarTodos().size());
        EstoqueProduto estoqueDepois = estoqueRepository.buscarPorFilialEProduto(1L, mussarela.getId()).orElseThrow();
        assertBigDecimalEquals("4000.000000", estoqueDepois.getQuantidadeAtual());
        assertBigDecimalEquals("0.03200000", estoqueDepois.getCustoMedio());
    }

    @Test
    void saidaNormalMantemCustoMedioESaldoZeroMantemValorZerado() {
        EstoqueProduto estoque = new EstoqueProduto(
                0,
                1L,
                mussarela,
                new BigDecimal("10"),
                BigDecimal.ZERO,
                new BigDecimal("2.00000000")
        );

        estoque.aplicarSaida(new BigDecimal("4"), estoque.getCustoMedio(), true);
        assertBigDecimalEquals("6.000000", estoque.getQuantidadeAtual());
        assertBigDecimalEquals("2.00000000", estoque.getCustoMedio());
        assertBigDecimalEquals("12.0000", estoque.valorEstoque());

        estoque.aplicarSaida(new BigDecimal("6"), estoque.getCustoMedio(), true);
        assertBigDecimalEquals("0.000000", estoque.getQuantidadeAtual());
        assertBigDecimalEquals("2.00000000", estoque.getCustoMedio());
        assertBigDecimalEquals("0.0000", estoque.valorEstoque());
    }

    @Test
    void confirmacoesConcorrentesNaoPerdemAtualizacaoDeEstoque() throws Exception {
        CompraEstoque compraA = compraDireta("100", "100.00");
        CompraEstoque compraB = compraDireta("50", "75.00");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<CompraEstoque>> tarefas = List.of(
                    () -> compraService.confirmarCompra(gerente, compraA.getId(), "conc-a", "Concorrente A"),
                    () -> compraService.confirmarCompra(gerente, compraB.getId(), "conc-b", "Concorrente B")
            );
            List<Future<CompraEstoque>> resultados = executor.invokeAll(tarefas);
            for (Future<CompraEstoque> resultado : resultados) {
                resultado.get();
            }
        } finally {
            executor.shutdownNow();
        }

        EstoqueProduto estoque = estoqueRepository.buscarPorFilialEProduto(1L, mussarela.getId()).orElseThrow();
        assertBigDecimalEquals("150.000000", estoque.getQuantidadeAtual());
        assertBigDecimalEquals("1.16666667", estoque.getCustoMedio());
        assertEquals(2, movimentoRepository.listarTodos().size());
    }

    @Test
    void cenarioAceiteMussarelaCompleto() {
        EmbalagemProduto pacote = pacoteMussarela5kg();
        CompraEstoque primeira = compraService.criarCompra(gerente, null);
        primeira = compraService.adicionarItem(
                gerente,
                primeira.getId(),
                mussarela.getId(),
                pacote.getId(),
                null,
                new BigDecimal("2"),
                new BigDecimal("300.00"),
                null
        );
        compraService.confirmarCompra(gerente, primeira.getId(), "aceite-1", "Entrada 2 pacotes");

        CompraEstoque segunda = compraService.criarCompra(gerente, null);
        segunda = compraService.adicionarItem(
                gerente,
                segunda.getId(),
                mussarela.getId(),
                null,
                "GRAMA",
                new BigDecimal("5000"),
                new BigDecimal("180.00"),
                null
        );
        compraService.confirmarCompra(gerente, segunda.getId(), "aceite-2", "Entrada 5000g");
        long segundaCompraId = segunda.getId();

        EstoqueProduto estoqueDepoisCompras = estoqueRepository.buscarPorFilialEProduto(1L, mussarela.getId()).orElseThrow();
        assertBigDecimalEquals("15000.000000", estoqueDepoisCompras.getQuantidadeAtual());
        assertBigDecimalEquals("0.03200000", estoqueDepoisCompras.getCustoMedio());
        assertEquals(2, movimentoRepository.listarTodos().size());

        MovimentoEstoque movimentoSegunda = movimentoRepository.listarTodos().stream()
                .filter(m -> m.getOrigemId().equals(segundaCompraId))
                .max(Comparator.comparing(MovimentoEstoque::getCriadoEm))
                .orElseThrow();
        movimentoService.estornar(gerente, movimentoSegunda.getId(), "Estorno da segunda entrada", "Aceite");

        EstoqueProduto estoqueFinal = estoqueRepository.buscarPorFilialEProduto(1L, mussarela.getId()).orElseThrow();
        assertBigDecimalEquals("10000.000000", estoqueFinal.getQuantidadeAtual());
        assertBigDecimalEquals("0.03000000", estoqueFinal.getCustoMedio());
        assertBigDecimalEquals("300.0000", estoqueFinal.valorEstoque());
        assertEquals(3, movimentoRepository.listarTodos().size());
        assertTrue(movimentoRepository.listarTodos().stream()
                .anyMatch(m -> m.getMovimentoEstornado() != null
                        && m.getMovimentoEstornado().getId() == movimentoSegunda.getId()
                        && "Estorno da segunda entrada".equals(m.getMotivo())
                        && m.getUsuarioId().equals(gerente.getRu())));
    }

    @Test
    void duasRequisicoesSimultaneasComMesmaChaveNaoDuplicamEntrada() throws Exception {
        CompraEstoque compra = compraDireta("100", "30.00");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<CompraEstoque>> tarefas = List.of(
                    () -> compraService.confirmarCompra(gerente, compra.getId(), "mesma-concorrente", "Primeira"),
                    () -> compraService.confirmarCompra(gerente, compra.getId(), "mesma-concorrente", "Primeira")
            );
            List<Future<CompraEstoque>> resultados = executor.invokeAll(tarefas);
            for (Future<CompraEstoque> resultado : resultados) {
                assertEquals(compra.getId(), resultado.get().getId());
            }
        } finally {
            executor.shutdownNow();
        }

        EstoqueProduto estoque = estoqueRepository.buscarPorFilialEProduto(1L, mussarela.getId()).orElseThrow();
        assertBigDecimalEquals("100.000000", estoque.getQuantidadeAtual());
        assertEquals(1, movimentoRepository.listarTodos().size());
    }

    @Test
    void duasTentativasSimultaneasNaoDuplicamEstorno() throws Exception {
        compraComEmbalagem("2", "300.00", "estorno-conc-1");
        MovimentoEstoque movimento = movimentoRepository.listarTodos().get(0);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Boolean>> tarefas = List.of(
                    () -> tentarEstornar(movimento.getId(), "Concorrente A"),
                    () -> tentarEstornar(movimento.getId(), "Concorrente B")
            );
            List<Future<Boolean>> resultados = executor.invokeAll(tarefas);
            long sucessos = 0;
            for (Future<Boolean> resultado : resultados) {
                if (resultado.get()) {
                    sucessos++;
                }
            }
            assertEquals(1, sucessos);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, movimentoRepository.listarTodos().stream()
                .filter(m -> m.getMovimentoEstornado() != null
                        && m.getMovimentoEstornado().getId() == movimento.getId())
                .count());
    }

    @Test
    void compraComVariosProdutosProcessaLocksEmOrdemDeterministica() {
        Produto produtoMaiorId = salvarProduto("Produto maior", grama);
        Produto produtoMenorId = mussarela;
        assertTrue(produtoMenorId.getId() < produtoMaiorId.getId());

        CompraEstoque compra = compraService.criarCompra(gerente, null);
        compra = compraService.adicionarItem(
                gerente,
                compra.getId(),
                produtoMaiorId.getId(),
                null,
                "GRAMA",
                new BigDecimal("10"),
                new BigDecimal("20.00"),
                null
        );
        compra = compraService.adicionarItem(
                gerente,
                compra.getId(),
                produtoMenorId.getId(),
                null,
                "GRAMA",
                new BigDecimal("10"),
                new BigDecimal("10.00"),
                null
        );

        compraService.confirmarCompra(gerente, compra.getId(), "multi-ordem", "Multi");

        long movimentoProdutoMenor = movimentoRepository.listarTodos().stream()
                .filter(m -> m.getProduto().getId() == produtoMenorId.getId())
                .mapToLong(MovimentoEstoque::getId)
                .findFirst()
                .orElseThrow();
        long movimentoProdutoMaior = movimentoRepository.listarTodos().stream()
                .filter(m -> m.getProduto().getId() == produtoMaiorId.getId())
                .mapToLong(MovimentoEstoque::getId)
                .findFirst()
                .orElseThrow();

        assertTrue(movimentoProdutoMenor < movimentoProdutoMaior);
    }

    @Test
    void produtoLegadoComCamposNovosNulosMantemDefaultsCompativeis() {
        Produto legado = new Produto(
                0,
                "Produto legado",
                1,
                1,
                BigDecimal.ONE,
                CategoriaEstoque.SECO,
                Boolean.TRUE,
                null,
                null,
                null
        );

        assertEquals(PoliticaFalta.BLOQUEAR, legado.getPoliticaFalta());
        assertTrue(legado.isAtivo());
        assertFalse(legado.getUnidadeBase() != null);
    }

    private Produto salvarProduto(String nome, UnidadeMedida unidadeBase) {
        return produtoRepository.salvar(new Produto(
                0,
                nome,
                0,
                0,
                BigDecimal.ZERO,
                CategoriaEstoque.FRIO,
                Boolean.TRUE,
                unidadeBase,
                PoliticaFalta.BLOQUEAR,
                Boolean.TRUE
        ));
    }

    private EmbalagemProduto pacoteMussarela5kg() {
        return produtoEstoqueService.cadastrarEmbalagem(
                gerente,
                mussarela.getId(),
                "Pacote 5 kg",
                new BigDecimal("5000"),
                "GRAMA",
                null,
                Boolean.TRUE
        );
    }

    private CompraEstoque compraComEmbalagem(String quantidadePacotes, String custoTotal, String key) {
        EmbalagemProduto pacote = embalagemRepository.listarPorProduto(mussarela.getId()).stream()
                .findFirst()
                .orElseGet(this::pacoteMussarela5kg);
        CompraEstoque compra = compraService.criarCompra(gerente, null);
        compra = compraService.adicionarItem(
                gerente,
                compra.getId(),
                mussarela.getId(),
                pacote.getId(),
                null,
                new BigDecimal(quantidadePacotes),
                new BigDecimal(custoTotal),
                null
        );
        return compraService.confirmarCompra(gerente, compra.getId(), key, "Entrada");
    }

    private CompraEstoque compraDireta(String quantidadeGramas, String custoTotal) {
        CompraEstoque compra = compraService.criarCompra(gerente, null);
        return compraService.adicionarItem(
                gerente,
                compra.getId(),
                mussarela.getId(),
                null,
                "GRAMA",
                new BigDecimal(quantidadeGramas),
                new BigDecimal(custoTotal),
                null
        );
    }

    private boolean tentarEstornar(long movimentoId, String motivo) {
        try {
            movimentoService.estornar(gerente, movimentoId, motivo, null);
            return true;
        } catch (ErroNegocioException e) {
            return false;
        }
    }

    private static void assertBigDecimalEquals(String esperado, BigDecimal atual) {
        assertEquals(0, new BigDecimal(esperado).compareTo(atual));
    }
}
