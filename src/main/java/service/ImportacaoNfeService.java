package service;

import model.CategoriaEstoque;
import model.CodigoVenda;
import model.Fornecedor;
import model.LogAcao;
import model.NotaFiscal;
import model.Permissao;
import model.Produto;
import model.Usuario;
import model.VariacaoItemCardapio;
import model.VinculoProdutoFornecedor;
import org.springframework.transaction.annotation.Transactional;
import repository.CodigoVendaRepository;
import repository.FornecedorRepository;
import repository.LogRepository;
import repository.NotaFiscalRepository;
import repository.ProdutoRepository;
import repository.VinculoProdutoFornecedorRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Importa o XML da NF-e do fornecedor em dois passos:
 * <ol>
 *   <li>{@link #previa}: lê o arquivo e reconhece o que já conhece, sem gravar nada;</li>
 *   <li>{@link #importar}: com as decisões conferidas por uma pessoa, cria o que
 *       falta, registra a nota e dá entrada no estoque, tudo ou nada.</li>
 * </ol>
 *
 * <p>Quantidades e valores vêm sempre do XML, nunca do navegador. O navegador só
 * decide o que fazer com cada item e quantas unidades vêm na embalagem: é a
 * conferência humana que evita dar entrada em 5 "caixas" como se fossem 5 latas.
 */
public class ImportacaoNfeService {

    private static final Set<String> UNIDADES_AVULSAS = Set.of("UN", "UND", "UNID", "UNIDADE", "PC", "PÇ", "PECA", "GF", "GAR", "LT", "LATA");
    private static final Pattern FATOR_NA_DESCRICAO =
            Pattern.compile("(?:\\bC\\s*/\\s*|\\bCX\\s*C?/?\\s*|\\bFD\\s*C?/?\\s*|\\bPCT\\s*C?/?\\s*|\\bPK\\s*)(\\d{1,3})\\b|\\b(\\d{1,3})\\s*X\\s*\\d");
    private static final int MAX_UNIDADES_POR_EMBALAGEM = 1000;

    private final FornecedorRepository fornecedorRepository;
    private final NotaFiscalRepository notaFiscalRepository;
    private final NotaFiscalService notaFiscalService;
    private final ProdutoRepository produtoRepository;
    private final CodigoVendaRepository codigoVendaRepository;
    private final VinculoProdutoFornecedorRepository vinculoRepository;
    private final ProdutoRevendaService produtoRevendaService;
    private final LogRepository logRepository;

    public ImportacaoNfeService(FornecedorRepository fornecedorRepository,
                                NotaFiscalRepository notaFiscalRepository,
                                NotaFiscalService notaFiscalService,
                                ProdutoRepository produtoRepository,
                                CodigoVendaRepository codigoVendaRepository,
                                VinculoProdutoFornecedorRepository vinculoRepository,
                                ProdutoRevendaService produtoRevendaService,
                                LogRepository logRepository) {
        this.fornecedorRepository = fornecedorRepository;
        this.notaFiscalRepository = notaFiscalRepository;
        this.notaFiscalService = notaFiscalService;
        this.produtoRepository = produtoRepository;
        this.codigoVendaRepository = codigoVendaRepository;
        this.vinculoRepository = vinculoRepository;
        this.produtoRevendaService = produtoRevendaService;
        this.logRepository = logRepository;
    }

    // ── Prévia ────────────────────────────────────────────────────────────────

    public record Previa(NfeXmlParser.Documento documento,
                         Long fornecedorId,
                         boolean fornecedorInativo,
                         Long notaExistenteId,
                         List<ItemPrevia> itens,
                         List<String> avisos) {}

    /**
     * @param unidadesPorEmbalagem sugestão; {@code conferir} pede atenção de quem importa
     * @param origem VINCULO, CODIGO_BARRAS ou nulo (produto novo)
     */
    public record ItemPrevia(NfeXmlParser.Item item,
                             Integer produtoId,
                             String produtoNome,
                             String origem,
                             int unidadesPorEmbalagem,
                             boolean conferir) {}

    @Transactional(readOnly = true)
    public Previa previa(Usuario usuario, String xml) {
        validarPermissao(usuario);
        NfeXmlParser.Documento documento = NfeXmlParser.ler(xml);

        Optional<Fornecedor> fornecedor = buscarFornecedor(documento);
        List<String> avisos = new ArrayList<>();
        if (documento.homologacao()) {
            avisos.add("Nota de homologacao (teste): nao tem valor fiscal.");
        }
        if (!documento.notaDeSaidaNormal()) {
            avisos.add("Esta nao parece uma nota normal de venda (pode ser devolucao, complemento ou entrada). Confira antes de importar.");
        }
        if (documento.emitente().documento().length() != 14) {
            avisos.add("O emitente nao tem CNPJ: esta nota nao pode ser importada.");
        }

        List<ItemPrevia> itens = new ArrayList<>();
        for (NfeXmlParser.Item item : documento.itens()) {
            itens.add(reconhecer(item, fornecedor.orElse(null)));
        }
        return new Previa(
                documento,
                fornecedor.map(Fornecedor::getId).orElse(null),
                fornecedor.map(f -> !f.isAtivo()).orElse(false),
                notaFiscalRepository.buscarPorChaveAcesso(documento.chaveAcesso()).map(NotaFiscal::getId).orElse(null),
                itens,
                avisos
        );
    }

    private ItemPrevia reconhecer(NfeXmlParser.Item item, Fornecedor fornecedor) {
        if (fornecedor != null && !item.codigoFornecedor().isBlank()) {
            Optional<VinculoProdutoFornecedor> vinculo = vinculoRepository.buscar(fornecedor.getId(), item.codigoFornecedor());
            if (vinculo.isPresent()) {
                Produto produto = vinculo.get().getProduto();
                return new ItemPrevia(item, produto.getId(), produto.getNome(), "VINCULO",
                        vinculo.get().getUnidadesPorEmbalagem(), false);
            }
        }

        Fator fator = sugerirFator(item);
        // O código da embalagem comprada (cEAN) diz quantas unidades ela baixa, se já for conhecido.
        Optional<VariacaoItemCardapio> porEmbalagem = variacaoDoCodigo(item.eanComercial());
        if (porEmbalagem.isPresent()) {
            VariacaoItemCardapio variacao = porEmbalagem.get();
            int quantidade = variacao.getQuantidadeProdutoDiretoBase().setScale(0, RoundingMode.DOWN).intValue();
            Produto produto = variacao.getProdutoDireto();
            return new ItemPrevia(item, produto.getId(), produto.getNome(), "CODIGO_BARRAS",
                    Math.max(quantidade, fator.unidades()), fator.conferir() && quantidade <= 1);
        }
        Optional<VariacaoItemCardapio> porUnidade = variacaoDoCodigo(item.eanTributavel());
        if (porUnidade.isPresent()) {
            Produto produto = porUnidade.get().getProdutoDireto();
            return new ItemPrevia(item, produto.getId(), produto.getNome(), "CODIGO_BARRAS", fator.unidades(), fator.conferir());
        }
        return new ItemPrevia(item, null, null, null, fator.unidades(), fator.conferir());
    }

    private Optional<VariacaoItemCardapio> variacaoDoCodigo(String codigo) {
        if (codigo == null) {
            return Optional.empty();
        }
        return codigoVendaRepository.listarAtivosPorCodigo(codigo).stream()
                .map(CodigoVenda::getVariacao)
                .filter(variacao -> variacao.getProdutoDireto() != null)
                .findFirst();
    }

    record Fator(int unidades, boolean conferir) {}

    /**
     * Quantas unidades vêm em cada embalagem comprada. A quantidade tributável
     * da nota é o dado mais confiável; a descrição é só um palpite.
     */
    static Fator sugerirFator(NfeXmlParser.Item item) {
        if (!item.unidadeComercial().equals(item.unidadeTributavel())
                && item.quantidadeComercial().signum() > 0
                && item.quantidadeTributavel().signum() > 0) {
            BigDecimal razao = item.quantidadeTributavel()
                    .divide(item.quantidadeComercial(), 6, RoundingMode.HALF_UP)
                    .stripTrailingZeros();
            if (razao.scale() <= 0 && razao.compareTo(BigDecimal.valueOf(2)) >= 0
                    && razao.compareTo(BigDecimal.valueOf(MAX_UNIDADES_POR_EMBALAGEM)) <= 0) {
                return new Fator(razao.intValueExact(), false);
            }
        }
        if (UNIDADES_AVULSAS.contains(item.unidadeComercial())) {
            return new Fator(1, false);
        }
        Matcher palpite = FATOR_NA_DESCRICAO.matcher(item.descricao().toUpperCase(Locale.ROOT));
        if (palpite.find()) {
            int valor = Integer.parseInt(palpite.group(1) != null ? palpite.group(1) : palpite.group(2));
            if (valor >= 2 && valor <= MAX_UNIDADES_POR_EMBALAGEM) {
                return new Fator(valor, true);
            }
        }
        return new Fator(1, true);
    }

    // ── Importação ────────────────────────────────────────────────────────────

    public enum Acao { VINCULAR, CRIAR, IGNORAR }

    /** O que fazer com um item, conferido por quem está importando. */
    public record Decisao(int numeroItem,
                          Acao acao,
                          Integer produtoId,
                          int unidadesPorEmbalagem,
                          String nome,
                          BigDecimal precoVenda,
                          BigDecimal precoGelado,
                          BigDecimal precoFardo,
                          int quantidadeMinima,
                          String categoriaCardapioCodigo,
                          String categoriaCardapioNome,
                          CategoriaEstoque categoriaEstoque) {}

    public record Resultado(long notaId,
                            boolean fornecedorCriado,
                            String fornecedorNome,
                            int produtosCriados,
                            int itensLancados,
                            int itensIgnorados,
                            int unidades,
                            BigDecimal custoTotal) {}

    private static final class Lancamento {
        private final Produto produto;
        private BigDecimal unidades = BigDecimal.ZERO;
        private BigDecimal custo = BigDecimal.ZERO;

        private Lancamento(Produto produto) {
            this.produto = produto;
        }
    }

    @Transactional
    public Resultado importar(Usuario usuario, String xml, List<Decisao> decisoes) {
        validarPermissao(usuario);
        NfeXmlParser.Documento documento = NfeXmlParser.ler(xml);

        Optional<NotaFiscal> existente = notaFiscalRepository.buscarPorChaveAcesso(documento.chaveAcesso());
        if (existente.isPresent()) {
            throw new ErroNegocioException("NFE_JA_IMPORTADA",
                    "Esta nota ja foi importada (nota #" + existente.get().getId() + ").",
                    Map.of("notaId", existente.get().getId()));
        }

        Map<Integer, Decisao> porItem = new LinkedHashMap<>();
        if (decisoes != null) {
            for (Decisao decisao : decisoes) {
                porItem.put(decisao.numeroItem(), decisao);
            }
        }
        for (NfeXmlParser.Item item : documento.itens()) {
            if (!porItem.containsKey(item.numero())) {
                throw new IllegalArgumentException("Decida o que fazer com o item " + item.numero() + ": " + item.descricao() + ".");
            }
        }

        boolean fornecedorNovo = buscarFornecedor(documento).isEmpty();
        Fornecedor fornecedor = fornecedorDaNota(usuario, documento);

        Map<Integer, Lancamento> lancamentos = new LinkedHashMap<>();
        int criados = 0;
        int ignorados = 0;
        for (NfeXmlParser.Item item : documento.itens()) {
            Decisao decisao = porItem.get(item.numero());
            if (decisao.acao() == null) {
                throw new IllegalArgumentException("Decida o que fazer com o item " + item.numero() + ".");
            }
            if (decisao.acao() == Acao.IGNORAR) {
                ignorados++;
                continue;
            }
            BigDecimal unidades = unidadesDoItem(item, decisao.unidadesPorEmbalagem());
            BigDecimal custoUnitario = item.custoTotal().divide(unidades, 2, RoundingMode.HALF_UP);
            if (custoUnitario.signum() <= 0) {
                throw new IllegalArgumentException("Item " + item.numero() + " (" + item.descricao()
                        + ") nao tem custo: o caixa nao vende produto sem custo. Marque como ignorado.");
            }

            Produto produto;
            if (decisao.acao() == Acao.CRIAR) {
                produto = criarProduto(usuario, item, decisao, custoUnitario);
                criados++;
            } else {
                if (decisao.produtoId() == null) {
                    throw new IllegalArgumentException("Escolha o produto do item " + item.numero() + ": " + item.descricao() + ".");
                }
                produto = produtoRepository.buscarPorId(decisao.produtoId())
                        .filter(Produto::isControladoPorEstoque)
                        .orElseThrow(() -> new IllegalArgumentException("Produto do item " + item.numero() + " nao encontrado."));
            }

            produto.preencherDadosFiscais(item.ncm(), item.cest());
            produto = produtoRepository.salvar(produto);
            lembrarVinculo(fornecedor, item, produto, decisao.unidadesPorEmbalagem());

            Lancamento lancamento = lancamentos.get(produto.getId());
            if (lancamento == null) {
                lancamento = new Lancamento(produto);
                lancamentos.put(produto.getId(), lancamento);
            }
            lancamento.unidades = lancamento.unidades.add(unidades);
            lancamento.custo = lancamento.custo.add(item.custoTotal());
        }

        if (lancamentos.isEmpty()) {
            throw new IllegalArgumentException("Nenhum item para dar entrada: todos foram ignorados.");
        }

        NotaFiscal nota = notaFiscalService.abrirNota(usuario, fornecedor);
        nota.registrarOrigemNfe(documento.chaveAcesso(), documento.numero(), documento.dataEmissao());
        nota = notaFiscalRepository.salvar(nota);

        int totalUnidades = 0;
        BigDecimal custoTotal = BigDecimal.ZERO;
        for (Lancamento lancamento : lancamentos.values()) {
            int unidades = lancamento.unidades.intValueExact();
            // Linhas repetidas do mesmo produto viram um item, com o custo médio entre elas.
            BigDecimal custoUnitario = lancamento.custo.divide(lancamento.unidades, 2, RoundingMode.HALF_UP);
            notaFiscalService.adicionarItem(usuario, nota, lancamento.produto, unidades, custoUnitario);
            totalUnidades += unidades;
            custoTotal = custoTotal.add(lancamento.custo);
        }
        notaFiscalService.confirmarNota(usuario, nota);

        logRepository.salvar(new LogAcao(
                0,
                usuario.getRu(),
                "NFE_IMPORTADA",
                "NF-e " + documento.numero() + " de " + fornecedor.getNome() + " importada: "
                        + lancamentos.size() + " produto(s), " + totalUnidades + " unidade(s). Chave " + documento.chaveAcesso()
        ));

        return new Resultado(nota.getId(), fornecedorNovo, fornecedor.getNome(), criados, lancamentos.size(),
                ignorados, totalUnidades, custoTotal.setScale(2, RoundingMode.HALF_UP));
    }

    private static BigDecimal unidadesDoItem(NfeXmlParser.Item item, int unidadesPorEmbalagem) {
        if (unidadesPorEmbalagem < 1 || unidadesPorEmbalagem > MAX_UNIDADES_POR_EMBALAGEM) {
            throw new IllegalArgumentException("Unidades por embalagem invalidas no item " + item.numero() + ".");
        }
        BigDecimal unidades = item.quantidadeComercial()
                .multiply(BigDecimal.valueOf(unidadesPorEmbalagem))
                .stripTrailingZeros();
        if (unidades.signum() <= 0 || unidades.scale() > 0) {
            throw new IllegalArgumentException("Item " + item.numero() + " (" + item.descricao()
                    + ") nao resulta em unidades inteiras: " + item.quantidadeComercial().stripTrailingZeros().toPlainString()
                    + " " + item.unidadeComercial() + " x " + unidadesPorEmbalagem + ". Confira as unidades por embalagem ou ignore o item.");
        }
        return unidades.setScale(0, RoundingMode.UNNECESSARY);
    }

    private Produto criarProduto(Usuario usuario, NfeXmlParser.Item item, Decisao decisao, BigDecimal custoUnitario) {
        int unidades = decisao.unidadesPorEmbalagem();
        String eanUnidade = item.eanTributavel();
        String eanEmbalagem = item.eanComercial();
        if (unidades <= 1) {
            // Compra avulsa: o código comercial é o da própria unidade.
            eanUnidade = eanEmbalagem != null ? eanEmbalagem : eanUnidade;
            eanEmbalagem = null;
        } else if (eanUnidade != null && eanUnidade.equals(eanEmbalagem)) {
            // A nota repetiu o código da caixa nos dois campos: ele não identifica a lata.
            eanUnidade = null;
        }

        ProdutoRevendaService.Fardo fardo = null;
        if (unidades >= 2 && decisao.precoFardo() != null) {
            fardo = new ProdutoRevendaService.Fardo(unidades, decisao.precoFardo(), eanEmbalagem);
        }
        String nome = decisao.nome() == null || decisao.nome().isBlank() ? item.descricao() : decisao.nome();
        return produtoRevendaService.cadastrar(usuario, new ProdutoRevendaService.Entrada(
                nome,
                decisao.categoriaCardapioCodigo(),
                decisao.categoriaCardapioNome(),
                decisao.categoriaEstoque(),
                eanUnidade,
                decisao.precoVenda(),
                decisao.precoGelado(),
                custoUnitario,
                0,
                decisao.quantidadeMinima(),
                fardo
        )).produto();
    }

    private void lembrarVinculo(Fornecedor fornecedor, NfeXmlParser.Item item, Produto produto, int unidades) {
        if (item.codigoFornecedor().isBlank() || item.codigoFornecedor().length() > 60) {
            return;
        }
        VinculoProdutoFornecedor vinculo = vinculoRepository.buscar(fornecedor.getId(), item.codigoFornecedor())
                .map(existente -> {
                    existente.atualizar(produto, unidades);
                    return existente;
                })
                .orElseGet(() -> new VinculoProdutoFornecedor(fornecedor, item.codigoFornecedor(), produto, unidades));
        vinculoRepository.salvar(vinculo);
    }

    private Optional<Fornecedor> buscarFornecedor(NfeXmlParser.Documento documento) {
        return fornecedorRepository.buscarPorCnpj(documento.emitente().documento());
    }

    /** Fornecedor novo é criado aqui: os dados vêm da nota e a importação já exige permissão de estoque. */
    private Fornecedor fornecedorDaNota(Usuario usuario, NfeXmlParser.Documento documento) {
        Optional<Fornecedor> existente = buscarFornecedor(documento);
        if (existente.isPresent()) {
            if (!existente.get().isAtivo()) {
                throw new IllegalArgumentException("O fornecedor " + existente.get().getNome()
                        + " esta inativo. Reative-o antes de importar a nota.");
            }
            return existente.get();
        }
        NfeXmlParser.Parte emitente = documento.emitente();
        if (emitente.documento().length() != 14) {
            throw new IllegalArgumentException("O emitente da nota nao tem CNPJ: nao e possivel cadastrar o fornecedor.");
        }
        Fornecedor novo = fornecedorRepository.salvar(new Fornecedor(0, emitente.nome(), emitente.documento(), emitente.telefone()));
        logRepository.salvar(new LogAcao(0, usuario.getRu(), "FORNECEDOR_CADASTRADO",
                "Fornecedor cadastrado pela NF-e: " + novo.getNome() + " (CNPJ: " + emitente.documento() + ")"));
        return novo;
    }

    private static void validarPermissao(Usuario usuario) {
        if (usuario == null
                || usuario.getClasse() == null
                || !usuario.getClasse().possuiPermissao(Permissao.EDITAR_ESTOQUE)) {
            throw new SecurityException("Voce nao tem permissao para importar notas.");
        }
    }
}
