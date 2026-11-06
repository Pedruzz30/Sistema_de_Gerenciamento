package service;

import model.ClasseFuncionario;
import model.EmbalagemProduto;
import model.EstoqueProduto;
import model.Permissao;
import model.Produto;
import model.Usuario;
import model.VariacaoItemCardapio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import repository.InMemoryCategoriaCardapioRepository;
import repository.InMemoryCodigoVendaRepository;
import repository.InMemoryEmbalagemProdutoRepository;
import repository.InMemoryEstoqueProdutoRepository;
import repository.InMemoryItemCardapioRepository;
import repository.InMemoryLogRepository;
import repository.InMemoryMovimentoEstoqueRepository;
import repository.InMemoryPedidoRepository;
import repository.InMemoryProdutoRepository;
import repository.InMemoryUnidadeMedidaRepository;
import repository.VariacaoItemCardapioRepository;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cadastro rápido de bebida: cria produto, item, variações, embalagem e códigos de uma vez. */
class ProdutoRevendaNovoTest {

    private static final String EAN_LATA = "4006381333931";
    private static final String EAN_FARDO = "5901234123457";

    private InMemoryProdutoRepository produtos;
    private InMemoryEstoqueProdutoRepository estoques;
    private InMemoryEmbalagemProdutoRepository embalagens;
    private CodigoVendaService codigoVendaService;
    private ProdutoRevendaService service;
    private Usuario gerente;

    @BeforeEach
    void setUp() {
        produtos = new InMemoryProdutoRepository();
        estoques = new InMemoryEstoqueProdutoRepository();
        embalagens = new InMemoryEmbalagemProdutoRepository();
        InMemoryMovimentoEstoqueRepository movimentos = new InMemoryMovimentoEstoqueRepository();
        SincronizadorEstoque sincronizador = new SincronizadorEstoque(produtos, estoques, movimentos);
        EstoqueService estoqueService = new EstoqueService(produtos, new InMemoryPedidoRepository(), sincronizador);
        UnidadeMedidaService unidades = new UnidadeMedidaService(new InMemoryUnidadeMedidaRepository());
        ProdutoEstoqueService produtoEstoque = new ProdutoEstoqueService(produtos, embalagens, estoques, unidades);
        InMemoryItemCardapioRepository itens = new InMemoryItemCardapioRepository();
        CardapioService cardapio = new CardapioService(new InMemoryCategoriaCardapioRepository(), itens, produtos);
        VariacoesEmMemoria variacoes = new VariacoesEmMemoria();
        VariacaoItemCardapioService variacaoService =
                new VariacaoItemCardapioService(itens, produtos, variacoes, new InMemoryLogRepository());
        InMemoryCodigoVendaRepository codigos = new InMemoryCodigoVendaRepository();
        codigoVendaService = new CodigoVendaService(codigos, variacoes);
        service = new ProdutoRevendaService(estoqueService, produtoEstoque, cardapio, variacaoService,
                codigoVendaService, codigos, produtos, unidades);
        gerente = usuario(1, Permissao.EDITAR_ESTOQUE, Permissao.VER_ESTOQUE, Permissao.VER_VENDAS);
    }

    private static Usuario usuario(long ru, Permissao... permissoes) {
        ClasseFuncionario classe = new ClasseFuncionario((int) ru, "CLASSE_" + ru, "Teste");
        for (Permissao p : permissoes) {
            classe.adicionarPermissao(p);
        }
        return new Usuario(ru, "Teste", "Usuario", "529.982.247-25", "1234", classe, Usuario.Perfil.OPERADOR);
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static void assertValor(String esperado, BigDecimal atual) {
        assertEquals(0, bd(esperado).compareTo(atual), () -> "esperado " + esperado + " mas foi " + atual);
    }

    private static ProdutoRevendaService.Entrada entrada(String nome, String ean, String gelada, int qtd,
                                                         ProdutoRevendaService.Fardo fardo) {
        return new ProdutoRevendaService.Entrada(nome, null, null, null, ean, bd("3.99"),
                gelada == null ? null : bd(gelada), bd("2.75"), qtd, 12, fardo);
    }

    @Test
    void bebidaComGeladaEFardoCriaTudoDeUmaVez() {
        ProdutoRevendaService.Resultado r = service.cadastrar(gerente, entrada("Skol lata 350ml", EAN_LATA, "4.50", 48,
                new ProdutoRevendaService.Fardo(12, bd("38.90"), EAN_FARDO)));

        assertEquals(48, r.produto().getQuantidadeAtual());
        EstoqueProduto oficial = estoques.buscarPorFilialEProduto(1L, r.produto().getId()).orElseThrow();
        assertValor("48", oficial.getQuantidadeAtual());
        assertValor("2.75", oficial.getCustoMedio());

        assertEquals(List.of("GELADA", "NATURAL", "FARDO_12"),
                r.variacoes().stream().map(VariacaoItemCardapio::getCodigo).toList());
        VariacaoItemCardapio gelada = r.variacoes().get(0);
        VariacaoItemCardapio natural = r.variacoes().get(1);
        VariacaoItemCardapio fardo = r.variacoes().get(2);
        assertTrue(gelada.isPadrao());
        assertFalse(natural.isPadrao());
        assertValor("4.50", gelada.getPrecoVenda());
        assertValor("3.99", natural.getPrecoVenda());
        assertValor("12", fardo.getQuantidadeProdutoDiretoBase());
        assertEquals(gelada.getProdutoDireto().getId(), fardo.getProdutoDireto().getId());

        // O bipe da lata leva à gelada, com a natural como alternativa (F4).
        CodigoVendaService.Leitura lata = codigoVendaService.ler(EAN_LATA).orElseThrow();
        assertEquals(gelada.getId(), lata.padrao().getVariacao().getId());
        assertEquals(natural.getId(), lata.alternativas().get(0).getVariacao().getId());
        // O bipe do fardo leva à variação que baixa 12.
        assertEquals(fardo.getId(), codigoVendaService.ler(EAN_FARDO).orElseThrow().padrao().getVariacao().getId());

        List<EmbalagemProduto> compras = embalagens.listarPorProduto(r.produto().getId());
        assertEquals(1, compras.size());
        assertEquals(EAN_FARDO, compras.get(0).getCodigoBarras());
    }

    @Test
    void semGeladaCriaSoAUnidade() {
        ProdutoRevendaService.Resultado r = service.cadastrar(gerente, entrada("Amendoim 100g", EAN_LATA, null, 10, null));

        assertEquals(List.of("UNIDADE"), r.variacoes().stream().map(VariacaoItemCardapio::getCodigo).toList());
        assertTrue(r.variacoes().get(0).isPadrao());
        assertTrue(codigoVendaService.ler(EAN_LATA).orElseThrow().alternativas().isEmpty());
    }

    @Test
    void semEstoqueInicialJaGuardaOCusto() {
        ProdutoRevendaService.Resultado r = service.cadastrar(gerente, entrada("Agua 500ml", "", null, 0, null));

        EstoqueProduto oficial = estoques.buscarPorFilialEProduto(1L, r.produto().getId()).orElseThrow();
        assertValor("0", oficial.getQuantidadeAtual());
        assertValor("2.75", oficial.getCustoMedio());
        assertTrue(r.codigos().isEmpty());
    }

    @Test
    void codigoJaUsadoErecusadoENaoCriaNada() {
        service.cadastrar(gerente, entrada("Brahma lata", EAN_LATA, null, 1, null));
        int antes = produtos.listarTodos().size();

        ErroNegocioException erro = assertThrows(ErroNegocioException.class, () ->
                service.cadastrar(gerente, entrada("Outra cerveja", EAN_LATA, null, 1, null)));

        assertEquals("CODIGO_VENDA_DUPLICADO", erro.getCodigo());
        assertTrue(erro.getMessage().contains("Brahma lata"));
        assertEquals(antes, produtos.listarTodos().size());
    }

    @Test
    void recusaDadosInvalidos() {
        assertThrows(IllegalArgumentException.class, () ->
                service.cadastrar(gerente, entrada("Lida errada", "4006381333932", null, 1, null)));
        assertThrows(IllegalArgumentException.class, () ->
                service.cadastrar(gerente, entrada("Fardo igual", EAN_LATA, null, 1,
                        new ProdutoRevendaService.Fardo(12, bd("30.00"), EAN_LATA))));
        assertThrows(IllegalArgumentException.class, () ->
                service.cadastrar(gerente, entrada("Fardo de um", "", null, 1,
                        new ProdutoRevendaService.Fardo(1, bd("30.00"), ""))));
        assertThrows(IllegalArgumentException.class, () ->
                service.cadastrar(gerente, new ProdutoRevendaService.Entrada("Sem custo", null, null, null, "",
                        bd("3.00"), null, null, 1, 0, null)));
        assertTrue(produtos.listarTodos().isEmpty());
    }

    @Test
    void recusaNomeRepetidoESemPermissao() {
        service.cadastrar(gerente, entrada("Guarana lata", "", null, 1, null));
        ErroNegocioException erro = assertThrows(ErroNegocioException.class, () ->
                service.cadastrar(gerente, entrada("guarana LATA", "", null, 1, null)));
        assertEquals("PRODUTO_DUPLICADO", erro.getCodigo());

        Usuario caixa = usuario(2, Permissao.VER_VENDAS);
        assertThrows(SecurityException.class, () -> service.cadastrar(caixa, entrada("Sprite", "", null, 1, null)));
    }

    /** O projeto não tem repositório de variações em memória: este simula o banco. */
    private static final class VariacoesEmMemoria implements VariacaoItemCardapioRepository {
        private final List<VariacaoItemCardapio> lista = new ArrayList<>();
        private long sequencia = 1;

        @Override
        public VariacaoItemCardapio salvar(VariacaoItemCardapio variacao) {
            if (variacao.getId() == 0) {
                try {
                    Field id = VariacaoItemCardapio.class.getDeclaredField("id");
                    id.setAccessible(true);
                    id.setLong(variacao, sequencia++);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
            lista.removeIf(v -> v.getId() == variacao.getId());
            lista.add(variacao);
            return variacao;
        }

        @Override
        public Optional<VariacaoItemCardapio> buscarPorId(long id) {
            return lista.stream().filter(v -> v.getId() == id).findFirst();
        }

        @Override
        public List<VariacaoItemCardapio> listarPorItem(int itemId) {
            return lista.stream().filter(v -> v.getItemCardapio().getId() == itemId).toList();
        }

        @Override
        public List<VariacaoItemCardapio> listarPorIds(List<Long> ids) {
            return lista.stream().filter(v -> ids.contains(v.getId())).toList();
        }

        @Override
        public Optional<VariacaoItemCardapio> buscarPadraoAtivaPorItem(int itemId) {
            return listarPorItem(itemId).stream().filter(v -> v.isPadrao() && v.isAtiva()).findFirst();
        }

        @Override
        public boolean existeCodigoNoItem(int itemId, String codigo, Long ignorarId) {
            return listarPorItem(itemId).stream()
                    .anyMatch(v -> v.getCodigo().equalsIgnoreCase(codigo) && !Long.valueOf(v.getId()).equals(ignorarId));
        }

        @Override
        public boolean existePadraoAtivaNoItem(int itemId, Long ignorarId) {
            return listarPorItem(itemId).stream()
                    .anyMatch(v -> v.isPadrao() && v.isAtiva() && !Long.valueOf(v.getId()).equals(ignorarId));
        }

        @Override
        public List<VariacaoItemCardapio> listarTodos() {
            return List.copyOf(lista);
        }
    }
}
