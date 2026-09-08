package service;

import model.ItemCardapio;
import model.LogAcao;
import model.Produto;
import model.TipoComposicaoVariacao;
import model.TipoItemCardapio;
import model.Usuario;
import model.VariacaoItemCardapio;
import org.springframework.transaction.annotation.Transactional;
import repository.ItemCardapioRepository;
import repository.LogRepository;
import repository.ProdutoRepository;
import repository.VariacaoItemCardapioRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class VariacaoItemCardapioService {

    public record BackfillVariacaoResultado(int criadas, List<String> pendencias) {}

    private final ItemCardapioRepository itemCardapioRepository;
    private final ProdutoRepository produtoRepository;
    private final VariacaoItemCardapioRepository variacaoRepository;
    private final LogRepository logRepository;

    public VariacaoItemCardapioService(ItemCardapioRepository itemCardapioRepository,
                                       ProdutoRepository produtoRepository,
                                       VariacaoItemCardapioRepository variacaoRepository,
                                       LogRepository logRepository) {
        this.itemCardapioRepository = itemCardapioRepository;
        this.produtoRepository = produtoRepository;
        this.variacaoRepository = variacaoRepository;
        this.logRepository = logRepository;
    }

    @Transactional
    public List<VariacaoItemCardapio> listarPorItem(int itemId, Usuario usuario) {
        ItemCardapio item = buscarItemObrigatorio(itemId);
        garantirVariacaoPadraoSePossivel(item, usuario);
        return variacaoRepository.listarPorItem(itemId);
    }

    @Transactional(readOnly = true)
    public VariacaoItemCardapio buscarObrigatoria(long variacaoId) {
        return variacaoRepository.buscarPorId(variacaoId)
                .orElseThrow(() -> new ErroNegocioException(
                        "VARIACAO_NAO_ENCONTRADA",
                        "Variacao de cardapio nao encontrada."
                ));
    }

    @Transactional
    public VariacaoItemCardapio criar(int itemId,
                                      String nome,
                                      String codigo,
                                      BigDecimal precoVenda,
                                      TipoComposicaoVariacao tipoComposicao,
                                      Integer produtoDiretoId,
                                      BigDecimal quantidadeProdutoDiretoBase,
                                      Boolean ativa,
                                      Boolean padrao,
                                      Integer ordemExibicao,
                                      Usuario usuario) {
        ItemCardapio item = buscarItemObrigatorio(itemId);
        Produto produtoDireto = buscarProdutoDireto(tipoComposicao, produtoDiretoId);
        validarUnicidades(itemId, normalizarCodigo(codigo), padrao != null && padrao, null);
        VariacaoItemCardapio variacao = new VariacaoItemCardapio(
                0,
                item,
                nome,
                codigo,
                precoVenda,
                tipoComposicao,
                produtoDireto,
                quantidadeProdutoDiretoBase,
                ativa,
                padrao,
                ordemExibicao == null ? 0 : ordemExibicao
        );
        variacao = variacaoRepository.salvar(variacao);
        auditar(usuario, "VARIACAO_CARDAPIO_CRIADA",
                "Variacao " + variacao.getCodigo() + " criada para item " + item.getCodigo() + ".");
        return variacao;
    }

    @Transactional
    public VariacaoItemCardapio atualizar(long variacaoId,
                                          String nome,
                                          String codigo,
                                          BigDecimal precoVenda,
                                          TipoComposicaoVariacao tipoComposicao,
                                          Integer produtoDiretoId,
                                          BigDecimal quantidadeProdutoDiretoBase,
                                          Boolean ativa,
                                          Boolean padrao,
                                          Integer ordemExibicao,
                                          Usuario usuario) {
        VariacaoItemCardapio variacao = buscarObrigatoria(variacaoId);
        Produto produtoDireto = buscarProdutoDireto(tipoComposicao, produtoDiretoId);
        validarUnicidades(
                variacao.getItemCardapio().getId(),
                normalizarCodigo(codigo),
                padrao != null && padrao && ativa != Boolean.FALSE,
                variacao.getId()
        );
        variacao.atualizar(
                nome,
                codigo,
                precoVenda,
                tipoComposicao,
                produtoDireto,
                quantidadeProdutoDiretoBase,
                ativa,
                padrao,
                ordemExibicao == null ? variacao.getOrdemExibicao() : ordemExibicao
        );
        variacao = variacaoRepository.salvar(variacao);
        auditar(usuario, "VARIACAO_CARDAPIO_ALTERADA",
                "Variacao " + variacao.getCodigo() + " alterada.");
        return variacao;
    }

    @Transactional
    public VariacaoItemCardapio alterarStatus(long variacaoId, boolean ativa, Usuario usuario) {
        VariacaoItemCardapio variacao = buscarObrigatoria(variacaoId);
        if (ativa && variacao.isPadrao()) {
            validarUnicidades(variacao.getItemCardapio().getId(), variacao.getCodigo(), true, variacao.getId());
        }
        variacao.alterarStatus(ativa);
        variacao = variacaoRepository.salvar(variacao);
        auditar(usuario, "VARIACAO_CARDAPIO_STATUS",
                "Status da variacao " + variacao.getCodigo() + " alterado para " + ativa + ".");
        return variacao;
    }

    @Transactional
    public BackfillVariacaoResultado garantirVariacoesPadrao(Usuario usuario) {
        int criadas = 0;
        List<String> pendencias = new ArrayList<>();
        for (ItemCardapio item : itemCardapioRepository.listarTodos()) {
            ResultadoPadrao resultado = garantirVariacaoPadraoSePossivel(item, usuario);
            if (resultado.criada()) {
                criadas++;
            }
            if (resultado.pendencia() != null) {
                pendencias.add(resultado.pendencia());
            }
        }
        return new BackfillVariacaoResultado(criadas, List.copyOf(pendencias));
    }

    @Transactional
    public ResultadoPadrao garantirVariacaoPadraoSePossivel(ItemCardapio item, Usuario usuario) {
        if (variacaoRepository.buscarPadraoAtivaPorItem(item.getId()).isPresent()) {
            return ResultadoPadrao.semAlteracao();
        }

        TipoComposicaoVariacao tipoComposicao;
        Produto produtoDireto = null;
        if (item.getTipoItem() == TipoItemCardapio.PREPARADO_SOB_DEMANDA) {
            tipoComposicao = TipoComposicaoVariacao.PREPARADO;
        } else if (item.possuiProdutoVinculado()) {
            tipoComposicao = TipoComposicaoVariacao.PRODUTO_DIRETO;
            produtoDireto = item.getProdutoVinculado();
        } else {
            String pendencia = "Item " + item.getCodigo() +
                    " e ESTOQUE_DIRETO, mas nao possui produto vinculado para variação padrao.";
            auditar(usuario, "VARIACAO_PADRAO_PENDENTE", pendencia);
            return ResultadoPadrao.pendente(pendencia);
        }

        String codigoPadrao = codigoPadraoDisponivel(item.getId());
        VariacaoItemCardapio variacao = new VariacaoItemCardapio(
                0,
                item,
                "Padrão",
                codigoPadrao,
                item.getPrecoVenda(),
                tipoComposicao,
                produtoDireto,
                item.isAtivo(),
                true,
                0
        );
        variacaoRepository.salvar(variacao);
        auditar(usuario, "VARIACAO_PADRAO_CRIADA",
                "Variacao padrao criada para item " + item.getCodigo() + ".");
        return ResultadoPadrao.comCriacao();
    }

    private void validarUnicidades(int itemId, String codigo, boolean padraoAtiva, Long ignorarId) {
        if (variacaoRepository.existeCodigoNoItem(itemId, codigo, ignorarId)) {
            throw new ErroNegocioException(
                    "VARIACAO_CODIGO_DUPLICADO",
                    "Ja existe variacao com este codigo para o item."
            );
        }
        if (padraoAtiva && variacaoRepository.existePadraoAtivaNoItem(itemId, ignorarId)) {
            throw new ErroNegocioException(
                    "VARIACAO_PADRAO_DUPLICADA",
                    "Ja existe uma variacao padrao ativa para este item."
            );
        }
    }

    private Produto buscarProdutoDireto(TipoComposicaoVariacao tipoComposicao, Integer produtoDiretoId) {
        if (tipoComposicao == TipoComposicaoVariacao.PRODUTO_DIRETO) {
            if (produtoDiretoId == null) {
                throw new ErroNegocioException(
                        "VARIACAO_PRODUTO_DIRETO_INCONSISTENTE",
                        "Variacao de produto direto exige produto."
                );
            }
            return produtoRepository.buscarPorId(produtoDiretoId)
                    .orElseThrow(() -> new ErroNegocioException("PRODUTO_NAO_ENCONTRADO", "Produto nao encontrado."));
        }
        if (produtoDiretoId != null) {
            throw new ErroNegocioException(
                    "VARIACAO_PRODUTO_DIRETO_INCONSISTENTE",
                    "Variacao preparada nao deve possuir produto direto."
            );
        }
        return null;
    }

    private ItemCardapio buscarItemObrigatorio(int itemId) {
        return itemCardapioRepository.buscarPorId(itemId)
                .orElseThrow(() -> new ErroNegocioException("ITEM_NAO_ENCONTRADO", "Item do cardapio nao encontrado."));
    }

    private String codigoPadraoDisponivel(int itemId) {
        if (!variacaoRepository.existeCodigoNoItem(itemId, "PADRAO", null)) {
            return "PADRAO";
        }
        int sequencia = 2;
        while (variacaoRepository.existeCodigoNoItem(itemId, "PADRAO_" + sequencia, null)) {
            sequencia++;
        }
        return "PADRAO_" + sequencia;
    }

    private static String normalizarCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new ErroNegocioException("VARIACAO_INVALIDA", "Codigo da variacao e obrigatorio.");
        }
        return codigo.trim().toUpperCase();
    }

    private void auditar(Usuario usuario, String acao, String descricao) {
        logRepository.salvar(new LogAcao(0, usuario == null ? null : usuario.getRu(), acao, descricao));
    }

    public record ResultadoPadrao(boolean criada, String pendencia) {
        static ResultadoPadrao semAlteracao() { return new ResultadoPadrao(false, null); }
        static ResultadoPadrao comCriacao() { return new ResultadoPadrao(true, null); }
        static ResultadoPadrao pendente(String pendencia) { return new ResultadoPadrao(false, pendencia); }
    }
}
