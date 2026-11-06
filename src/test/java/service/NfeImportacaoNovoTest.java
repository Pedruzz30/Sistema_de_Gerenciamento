package service;

import model.ClasseFuncionario;
import model.EstoqueProduto;
import model.Fornecedor;
import model.NotaFiscal;
import model.Permissao;
import model.Produto;
import model.Usuario;
import model.VinculoProdutoFornecedor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import repository.InMemoryCategoriaCardapioRepository;
import repository.InMemoryCodigoVendaRepository;
import repository.InMemoryEmbalagemProdutoRepository;
import repository.InMemoryEstoqueProdutoRepository;
import repository.InMemoryFornecedorRepository;
import repository.InMemoryItemCardapioRepository;
import repository.InMemoryLogRepository;
import repository.InMemoryMovimentoEstoqueRepository;
import repository.InMemoryNotaFiscalRepository;
import repository.InMemoryPedidoRepository;
import repository.InMemoryProdutoRepository;
import repository.InMemoryUnidadeMedidaRepository;
import repository.VinculoProdutoFornecedorRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Importação do XML da NF-e: leitura segura, conferência e entrada no estoque. */
class NfeImportacaoNovoTest {

    private static final String EAN_LATA = "4006381333931";
    private static final String EAN_CAIXA = "5901234123457";
    private static final String IMPOSTOS = "<imposto><ICMS><ICMS10><vICMSST>12.00</vICMSST></ICMS10></ICMS>"
            + "<IPI><IPITrib><vIPI>3.00</vIPI></IPITrib></IPI></imposto>";

    private InMemoryProdutoRepository produtos;
    private InMemoryEstoqueProdutoRepository estoques;
    private InMemoryFornecedorRepository fornecedores;
    private InMemoryNotaFiscalRepository notas;
    private ImportacaoNfeService service;
    private Usuario gerente;

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static void assertValor(String esperado, BigDecimal atual) {
        assertEquals(0, bd(esperado).compareTo(atual), () -> "esperado " + esperado + " mas foi " + atual);
    }

    private static NfeXmlExemplo.Linha caixaDeLatas(String codigo) {
        return new NfeXmlExemplo.Linha(codigo, "CERV SKOL LT 350ML", EAN_CAIXA, EAN_LATA, "CX", "5.0000", "UN", "60.0000", "150.00", IMPOSTOS);
    }

    private static ImportacaoNfeService.Decisao criar(int item, int unidades, String nome) {
        return new ImportacaoNfeService.Decisao(item, ImportacaoNfeService.Acao.CRIAR, null, unidades, nome,
                bd("3.99"), bd("4.50"), bd("38.90"), 12, null, null, null);
    }

    private static ImportacaoNfeService.Decisao vincular(int item, int produtoId, int unidades) {
        return new ImportacaoNfeService.Decisao(item, ImportacaoNfeService.Acao.VINCULAR, produtoId, unidades,
                null, null, null, null, 0, null, null, null);
    }

    @BeforeEach
    void setUp() {
        produtos = new InMemoryProdutoRepository();
        estoques = new InMemoryEstoqueProdutoRepository();
        fornecedores = new InMemoryFornecedorRepository();
        notas = new InMemoryNotaFiscalRepository();
        InMemoryLogRepository logs = new InMemoryLogRepository();
        InMemoryMovimentoEstoqueRepository movimentos = new InMemoryMovimentoEstoqueRepository();
        SincronizadorEstoque sincronizador = new SincronizadorEstoque(produtos, estoques, movimentos);
        EstoqueService estoqueService = new EstoqueService(produtos, new InMemoryPedidoRepository(), sincronizador);
        UnidadeMedidaService unidades = new UnidadeMedidaService(new InMemoryUnidadeMedidaRepository());
        ProdutoEstoqueService produtoEstoque = new ProdutoEstoqueService(produtos, new InMemoryEmbalagemProdutoRepository(), estoques, unidades);
        InMemoryItemCardapioRepository itens = new InMemoryItemCardapioRepository();
        CardapioService cardapio = new CardapioService(new InMemoryCategoriaCardapioRepository(), itens, produtos);
        ProdutoRevendaNovoTest.VariacoesEmMemoria variacoes = new ProdutoRevendaNovoTest.VariacoesEmMemoria();
        VariacaoItemCardapioService variacaoService = new VariacaoItemCardapioService(itens, produtos, variacoes, logs);
        InMemoryCodigoVendaRepository codigos = new InMemoryCodigoVendaRepository();
        CodigoVendaService codigoVendaService = new CodigoVendaService(codigos, variacoes);
        ProdutoRevendaService revenda = new ProdutoRevendaService(estoqueService, produtoEstoque, cardapio, variacaoService,
                codigoVendaService, codigos, produtos, unidades);
        FornecedorService fornecedorService = new FornecedorService(fornecedores, logs);
        NotaFiscalService notaFiscalService = new NotaFiscalService(notas, logs, estoqueService, fornecedorService);
        service = new ImportacaoNfeService(fornecedores, notas, notaFiscalService, produtos, codigos,
                new VinculosEmMemoria(), revenda, logs);

        ClasseFuncionario classe = new ClasseFuncionario(1, "GERENTE", "Teste");
        classe.adicionarPermissao(Permissao.EDITAR_ESTOQUE);
        classe.adicionarPermissao(Permissao.VER_ESTOQUE);
        gerente = new Usuario(1, "Dona", "Loja", "529.982.247-25", "1234", classe, Usuario.Perfil.OPERADOR);
    }

    // ── Leitura do XML ────────────────────────────────────────────────────────

    @Test
    void custoSomaImpostosEDespesasEMenosDesconto() {
        String xml = NfeXmlExemplo.nota(1, new NfeXmlExemplo.Linha("A1", "CERV", EAN_CAIXA, EAN_LATA, "CX", "5", "UN", "60", "150.00", IMPOSTOS))
                .replace("<vProd>150.00</vProd>", "<vProd>150.00</vProd><vFrete>10.00</vFrete><vDesc>5.00</vDesc><vOutro>1.00</vOutro>");

        NfeXmlParser.Documento nota = NfeXmlParser.ler(xml);
        NfeXmlParser.Item item = nota.itens().get(0);

        assertValor("171.00", item.custoTotal()); // 150 + 10 − 5 + 1 + 3 IPI + 12 ST
        assertEquals(EAN_CAIXA, item.eanComercial());
        assertEquals(EAN_LATA, item.eanTributavel());
        assertEquals("22030000", item.ncm());
        assertEquals("0302100", item.cest());
        assertEquals("Distribuidora Exemplo LTDA", nota.emitente().nome());
        assertEquals(NfeXmlExemplo.CNPJ_DISTRIBUIDORA, nota.emitente().documento());
    }

    @Test
    void semGtinOuCodigoErradoNaoViramCodigoDeBarras() {
        assertNull(NfeXmlParser.gtinOuNulo("SEM GTIN"));
        assertNull(NfeXmlParser.gtinOuNulo(""));
        assertNull(NfeXmlParser.gtinOuNulo("4006381333932"));
        assertEquals(EAN_LATA, NfeXmlParser.gtinOuNulo(EAN_LATA));
    }

    @Test
    void recusaArquivoQueNaoEUmaNotaDeCompraValida() {
        NfeXmlExemplo.Linha linha = caixaDeLatas("A1");
        assertThrows(IllegalArgumentException.class, () -> NfeXmlParser.ler("isto nao e xml"));
        assertThrows(IllegalArgumentException.class, () -> NfeXmlParser.ler("<a><b/></a>"));
        assertThrows(IllegalArgumentException.class, () -> NfeXmlParser.ler(""));
        // NFC-e é cupom de consumidor, não nota de compra.
        assertTrue(assertThrows(IllegalArgumentException.class, () ->
                NfeXmlParser.ler(NfeXmlExemplo.nota(NfeXmlExemplo.chave(2), "65", 2, linha))).getMessage().contains("NFC-e"));
        // Chave alterada: o dígito verificador não confere.
        String chave = NfeXmlExemplo.chave(3);
        String alterada = chave.substring(0, 43) + ((chave.charAt(43) - '0' + 1) % 10);
        assertThrows(IllegalArgumentException.class, () -> NfeXmlParser.ler(NfeXmlExemplo.nota(alterada, "55", 3, linha)));
    }

    @Test
    void recusaXmlComEntidadeExterna() {
        String ataque = "<?xml version=\"1.0\"?><!DOCTYPE nfe [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + "<nfeProc><infNFe Id=\"NFe1\"><ide><mod>&xxe;</mod></ide></infNFe></nfeProc>";
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class, () -> NfeXmlParser.ler(ataque));
        assertFalse(erro.getMessage().contains("root:"));
        assertTrue(erro.getMessage().contains("invalido"));
    }

    @Test
    void sugereUnidadesPorEmbalagem() {
        // Nota informa: 5 CX = 60 UN.
        assertEquals(new ImportacaoNfeService.Fator(12, false), ImportacaoNfeService.sugerirFator(item("CERV", "CX", "5", "UN", "60")));
        // Unidade avulsa.
        assertEquals(new ImportacaoNfeService.Fator(1, false), ImportacaoNfeService.sugerirFator(item("AGUA 500ML", "UN", "24", "UN", "24")));
        // Só a descrição indica: é palpite, pede conferência.
        assertEquals(new ImportacaoNfeService.Fator(12, true), ImportacaoNfeService.sugerirFator(item("CERV LT 350ML CX C/12", "CX", "5", "CX", "5")));
        assertEquals(new ImportacaoNfeService.Fator(6, true), ImportacaoNfeService.sugerirFator(item("REFRI 6X2L", "FD", "2", "FD", "2")));
        // Sem pista nenhuma: 1, com aviso.
        assertEquals(new ImportacaoNfeService.Fator(1, true), ImportacaoNfeService.sugerirFator(item("CERVEJA", "CX", "5", "CX", "5")));
    }

    private static NfeXmlParser.Item item(String descricao, String uCom, String qCom, String uTrib, String qTrib) {
        return new NfeXmlParser.Item(1, "X", descricao, "", null, "", null, null, uCom, bd(qCom), uTrib, bd(qTrib), bd("1"), bd("1"));
    }

    // ── Prévia e importação ───────────────────────────────────────────────────

    @Test
    void previaNaoGravaNada() {
        ImportacaoNfeService.Previa previa = service.previa(gerente, NfeXmlExemplo.nota(10, caixaDeLatas("A1")));

        assertNull(previa.fornecedorId());
        assertNull(previa.notaExistenteId());
        assertEquals(12, previa.itens().get(0).unidadesPorEmbalagem());
        assertNull(previa.itens().get(0).produtoId());
        assertTrue(produtos.listarTodos().isEmpty());
        assertTrue(fornecedores.listarTodos().isEmpty());
    }

    @Test
    void primeiraNotaCriaFornecedorProdutoEDaEntradaEmUnidades() {
        String xml = NfeXmlExemplo.nota(11, caixaDeLatas("A1"));

        ImportacaoNfeService.Resultado r = service.importar(gerente, xml, List.of(criar(1, 12, "Skol lata 350ml")));

        assertTrue(r.fornecedorCriado());
        assertEquals(1, r.produtosCriados());
        assertEquals(60, r.unidades());
        assertValor("165.00", r.custoTotal());

        Produto skol = produtos.buscarPorNome("Skol lata 350ml").orElseThrow();
        assertEquals(60, skol.getQuantidadeAtual());
        assertEquals("22030000", skol.getNcm());
        assertEquals("0302100", skol.getCest());
        EstoqueProduto oficial = estoques.buscarPorFilialEProduto(1L, skol.getId()).orElseThrow();
        assertValor("60", oficial.getQuantidadeAtual());
        assertValor("2.75", oficial.getCustoMedio()); // (150 + 3 + 12) / 60

        NotaFiscal nota = notas.buscarPorChaveAcesso(NfeXmlExemplo.chave(11)).orElseThrow();
        assertEquals(NotaFiscal.Status.CONFIRMADA, nota.getStatus());
        assertEquals("11", nota.getNumeroNfe());
        assertEquals(2026, nota.getDataEmissao().getYear());
        Fornecedor fornecedor = nota.getFornecedor();
        assertEquals("Distribuidora Exemplo LTDA", fornecedor.getNome());
    }

    @Test
    void mesmaNotaNaoEntraDuasVezes() {
        String xml = NfeXmlExemplo.nota(12, caixaDeLatas("A1"));
        service.importar(gerente, xml, List.of(criar(1, 12, "Skol lata 350ml")));

        assertTrue(service.previa(gerente, xml).notaExistenteId() != null);
        ErroNegocioException erro = assertThrows(ErroNegocioException.class, () ->
                service.importar(gerente, xml, List.of(vincular(1, produtos.buscarPorNome("Skol lata 350ml").orElseThrow().getId(), 12))));
        assertEquals("NFE_JA_IMPORTADA", erro.getCodigo());
        assertEquals(60, produtos.buscarPorNome("Skol lata 350ml").orElseThrow().getQuantidadeAtual());
    }

    @Test
    void segundaNotaReconheceOItemSozinhoECalculaCustoMedio() {
        service.importar(gerente, NfeXmlExemplo.nota(13, caixaDeLatas("A1")), List.of(criar(1, 12, "Skol lata 350ml")));
        Produto skol = produtos.buscarPorNome("Skol lata 350ml").orElseThrow();

        String segunda = NfeXmlExemplo.nota(14, new NfeXmlExemplo.Linha("A1", "CERV SKOL LT 350ML", "SEM GTIN", "SEM GTIN",
                "CX", "5", "CX", "5", "195.00", null));
        ImportacaoNfeService.Previa previa = service.previa(gerente, segunda);

        ImportacaoNfeService.ItemPrevia item = previa.itens().get(0);
        assertEquals("VINCULO", item.origem());
        assertEquals(skol.getId(), item.produtoId());
        assertEquals(12, item.unidadesPorEmbalagem());
        assertFalse(item.conferir());
        assertTrue(previa.fornecedorId() != null);

        ImportacaoNfeService.Resultado r = service.importar(gerente, segunda, List.of(vincular(1, skol.getId(), 12)));

        assertFalse(r.fornecedorCriado());
        assertEquals(0, r.produtosCriados());
        EstoqueProduto oficial = estoques.buscarPorFilialEProduto(1L, skol.getId()).orElseThrow();
        assertValor("120", oficial.getQuantidadeAtual());
        assertValor("3.00", oficial.getCustoMedio()); // (60 × 2,75 + 60 × 3,25) / 120
    }

    @Test
    void reconhecePeloCodigoDeBarrasDeProdutoJaCadastrado() {
        service.importar(gerente, NfeXmlExemplo.nota(15, caixaDeLatas("A1")), List.of(criar(1, 12, "Skol lata 350ml")));

        // Outro código de fornecedor, mesmo código de barras da caixa: a variação do fardo diz 12.
        ImportacaoNfeService.ItemPrevia pelaCaixa = service.previa(gerente, NfeXmlExemplo.nota(16,
                new NfeXmlExemplo.Linha("B9", "SKOL", EAN_CAIXA, EAN_CAIXA, "CX", "2", "CX", "2", "60.00", null))).itens().get(0);
        assertEquals("CODIGO_BARRAS", pelaCaixa.origem());
        assertEquals(12, pelaCaixa.unidadesPorEmbalagem());

        ImportacaoNfeService.ItemPrevia pelaLata = service.previa(gerente, NfeXmlExemplo.nota(17,
                new NfeXmlExemplo.Linha("C7", "SKOL", EAN_LATA, EAN_LATA, "UN", "24", "UN", "24", "60.00", null))).itens().get(0);
        assertEquals("CODIGO_BARRAS", pelaLata.origem());
        assertEquals(1, pelaLata.unidadesPorEmbalagem());
    }

    @Test
    void linhasRepetidasDoMesmoProdutoViramUmItem() {
        Produto agua = new Produto(0, "Agua 500ml", 0, 0, bd("3.00"));
        agua = produtos.salvar(agua);
        int aguaId = agua.getId();
        String xml = NfeXmlExemplo.nota(18,
                new NfeXmlExemplo.Linha("A1", "AGUA", "SEM GTIN", "SEM GTIN", "UN", "10", "UN", "10", "10.00", null),
                new NfeXmlExemplo.Linha("A2", "AGUA PROMO", "SEM GTIN", "SEM GTIN", "UN", "10", "UN", "10", "20.00", null));

        ImportacaoNfeService.Resultado r = service.importar(gerente, xml, List.of(vincular(1, aguaId, 1), vincular(2, aguaId, 1)));

        assertEquals(1, r.itensLancados());
        assertEquals(20, r.unidades());
        assertEquals(1, notas.buscarPorChaveAcesso(NfeXmlExemplo.chave(18)).orElseThrow().getItens().size());
        assertValor("1.50", estoques.buscarPorFilialEProduto(1L, aguaId).orElseThrow().getCustoMedio());
    }

    @Test
    void itemIgnoradoNaoEntraETudoIgnoradoERecusado() {
        Produto agua = produtos.salvar(new Produto(0, "Agua 500ml", 0, 0, bd("3.00")));
        String xml = NfeXmlExemplo.nota(19,
                new NfeXmlExemplo.Linha("A1", "AGUA", "SEM GTIN", "SEM GTIN", "UN", "10", "UN", "10", "10.00", null),
                new NfeXmlExemplo.Linha("A2", "BRINDE", "SEM GTIN", "SEM GTIN", "UN", "1", "UN", "1", "0.00", null));
        ImportacaoNfeService.Decisao ignorar = new ImportacaoNfeService.Decisao(2, ImportacaoNfeService.Acao.IGNORAR,
                null, 1, null, null, null, null, 0, null, null, null);

        ImportacaoNfeService.Resultado r = service.importar(gerente, xml, List.of(vincular(1, agua.getId(), 1), ignorar));
        assertEquals(1, r.itensIgnorados());
        assertEquals(10, produtos.buscarPorId(agua.getId()).orElseThrow().getQuantidadeAtual());

        String soBrinde = NfeXmlExemplo.nota(20, new NfeXmlExemplo.Linha("A2", "BRINDE", "SEM GTIN", "SEM GTIN", "UN", "1", "UN", "1", "0.00", null));
        ImportacaoNfeService.Decisao ignorarUnico = new ImportacaoNfeService.Decisao(1, ImportacaoNfeService.Acao.IGNORAR,
                null, 1, null, null, null, null, 0, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> service.importar(gerente, soBrinde, List.of(ignorarUnico)));
    }

    @Test
    void recusaDecisaoFaltandoQuantidadeQuebradaECustoZero() {
        Produto agua = produtos.salvar(new Produto(0, "Agua 500ml", 0, 0, bd("3.00")));
        int id = agua.getId();
        String xml = NfeXmlExemplo.nota(21, new NfeXmlExemplo.Linha("A1", "CHOPP", "SEM GTIN", "SEM GTIN", "L", "2.5", "L", "2.5", "10.00", null));

        assertThrows(IllegalArgumentException.class, () -> service.importar(gerente, xml, List.of()));
        // 2,5 × 1 não dá unidades inteiras.
        assertTrue(assertThrows(IllegalArgumentException.class, () ->
                service.importar(gerente, xml, List.of(vincular(1, id, 1)))).getMessage().contains("inteiras"));
        // Vincular sem escolher o produto.
        String inteira = NfeXmlExemplo.nota(22, new NfeXmlExemplo.Linha("A1", "X", "SEM GTIN", "SEM GTIN", "UN", "2", "UN", "2", "10.00", null));
        assertThrows(IllegalArgumentException.class, () -> service.importar(gerente, inteira, List.of(
                new ImportacaoNfeService.Decisao(1, ImportacaoNfeService.Acao.VINCULAR, null, 1, null, null, null, null, 0, null, null, null))));
        // Sem custo o caixa não venderia.
        String gratis = NfeXmlExemplo.nota(23, new NfeXmlExemplo.Linha("A1", "X", "SEM GTIN", "SEM GTIN", "UN", "2", "UN", "2", "0.00", null));
        assertThrows(IllegalArgumentException.class, () -> service.importar(gerente, gratis, List.of(vincular(1, id, 1))));
        assertEquals(0, produtos.buscarPorId(id).orElseThrow().getQuantidadeAtual());
    }

    @Test
    void semPermissaoDeEstoqueNaoImporta() {
        ClasseFuncionario classe = new ClasseFuncionario(2, "CAIXA", "Teste");
        classe.adicionarPermissao(Permissao.VER_VENDAS);
        Usuario caixa = new Usuario(2, "Ana", "Caixa", "935.411.347-80", "1234", classe, Usuario.Perfil.OPERADOR);
        String xml = NfeXmlExemplo.nota(24, caixaDeLatas("A1"));

        assertThrows(SecurityException.class, () -> service.previa(caixa, xml));
        assertThrows(SecurityException.class, () -> service.importar(caixa, xml, List.of(criar(1, 12, "Skol"))));
    }

    private static final class VinculosEmMemoria implements VinculoProdutoFornecedorRepository {
        private final List<VinculoProdutoFornecedor> lista = new ArrayList<>();

        @Override
        public VinculoProdutoFornecedor salvar(VinculoProdutoFornecedor vinculo) {
            if (!lista.contains(vinculo) || vinculo.getId() == 0) {
                lista.removeIf(v -> v == vinculo);
                lista.add(vinculo);
            }
            return vinculo;
        }

        @Override
        public Optional<VinculoProdutoFornecedor> buscar(long fornecedorId, String codigoProduto) {
            return lista.stream()
                    .filter(v -> v.getFornecedor().getId() == fornecedorId && v.getCodigoProduto().equals(codigoProduto))
                    .findFirst();
        }
    }
}
