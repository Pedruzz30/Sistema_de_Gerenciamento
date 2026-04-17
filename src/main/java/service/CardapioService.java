package service;

import model.CategoriaCardapio;
import model.ItemCardapio;
import model.Produto;
import model.TipoItemCardapio;
import repository.CategoriaCardapioRepository;
import repository.ItemCardapioRepository;
import repository.ProdutoRepository;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;

public class CardapioService {

    private final CategoriaCardapioRepository categoriaCardapioRepository;
    private final ItemCardapioRepository itemCardapioRepository;
    private final ProdutoRepository produtoRepository;

    public CardapioService(CategoriaCardapioRepository categoriaCardapioRepository,
                           ItemCardapioRepository itemCardapioRepository,
                           ProdutoRepository produtoRepository) {
        this.categoriaCardapioRepository = categoriaCardapioRepository;
        this.itemCardapioRepository = itemCardapioRepository;
        this.produtoRepository = produtoRepository;
    }

    public List<CategoriaCardapio> listarCategoriasAtivas() {
        return listarCategoriasAdministrativas().stream()
                .filter(CategoriaCardapio::isAtivo)
                .toList();
    }

    public List<CategoriaCardapio> listarCategoriasAdministrativas() {
        return categoriaCardapioRepository.listarTodos().stream()
                .sorted(comparadorCategorias())
                .toList();
    }

    public List<ItemCardapio> listarItensAtivos(String categoriaCodigo, String busca) {
        String categoriaNormalizada = normalizarTexto(categoriaCodigo);
        String buscaNormalizada = normalizarTexto(busca);

        return itemCardapioRepository.listarTodos().stream()
                .filter(ItemCardapio::isAtivo)
                .filter(this::categoriaAtiva)
                .filter(item -> categoriaNormalizada.isEmpty()
                        || normalizarTexto(item.getCategoriaCardapio().getCodigo()).equals(categoriaNormalizada))
                .filter(item -> buscaNormalizada.isEmpty() || montarIndiceBusca(item).contains(buscaNormalizada))
                .sorted(comparadorItens())
                .toList();
    }

    public List<ItemCardapio> listarItensAdministrativos(Integer categoriaId, String busca) {
        String buscaNormalizada = normalizarTexto(busca);

        return itemCardapioRepository.listarTodos().stream()
                .filter(item -> categoriaId == null
                        || (item.getCategoriaCardapio() != null && item.getCategoriaCardapio().getId() == categoriaId))
                .filter(item -> buscaNormalizada.isEmpty() || montarIndiceBusca(item).contains(buscaNormalizada))
                .sorted(comparadorItens())
                .toList();
    }

    public ItemCardapio buscarItemAtivoPorId(int itemCardapioId) {
        ItemCardapio item = sincronizarCategoriaVigente(buscarItemExistente(itemCardapioId));
        if (!item.isAtivo() || !categoriaAtiva(item)) {
            throw new IllegalArgumentException("Item de cardapio inativo: " + itemCardapioId);
        }
        if (!itemDisponivel(item)) {
            throw new IllegalArgumentException("Item de cardapio indisponivel: " + itemCardapioId);
        }
        return item;
    }

    public boolean itemDisponivel(ItemCardapio itemCardapio) {
        ItemCardapio itemAtual = sincronizarCategoriaVigente(itemCardapio);
        return itemDisponivelAtual(itemAtual);
    }

    public boolean itemDisponivelAtual(ItemCardapio itemCardapio) {
        if (itemCardapio == null || !itemCardapio.isAtivo() || !itemCardapio.isDisponivel() || !categoriaAtiva(itemCardapio)) {
            return false;
        }
        if (itemCardapio.isPreparadoSobDemanda()) {
            return true;
        }

        Produto produto = itemCardapio.getProdutoVinculado();
        if (produto == null) {
            return true;
        }
        return produto.isControladoPorEstoque() && produto.getQuantidadeAtual() > 0;
    }

    public Integer quantidadeDisponivel(ItemCardapio itemCardapio) {
        ItemCardapio itemAtual = sincronizarCategoriaVigente(itemCardapio);
        Produto produto = itemAtual != null ? itemAtual.getProdutoVinculado() : null;
        if (produto == null || !produto.isControladoPorEstoque()) {
            return null;
        }
        return produto.getQuantidadeAtual();
    }

    public String nivelEstoque(ItemCardapio itemCardapio) {
        ItemCardapio itemAtual = sincronizarCategoriaVigente(itemCardapio);
        Produto produto = itemAtual != null ? itemAtual.getProdutoVinculado() : null;
        if (produto == null || !produto.isControladoPorEstoque()) {
            return null;
        }
        return produto.calcularNivel().name();
    }

    public CategoriaCardapio criarCategoria(String codigo,
                                            String nomeExibicao,
                                            int ordemExibicao,
                                            Boolean ativo) {
        String codigoNormalizado = normalizarCodigo(codigo, nomeExibicao);
        validarCodigoCategoriaDisponivel(codigoNormalizado, null);
        return categoriaCardapioRepository.salvar(
                new CategoriaCardapio(0, codigoNormalizado, nomeExibicao, ordemExibicao, ativo)
        );
    }

    public CategoriaCardapio atualizarCategoria(int categoriaId,
                                                String codigo,
                                                String nomeExibicao,
                                                int ordemExibicao) {
        CategoriaCardapio categoriaAtual = buscarCategoriaExistente(categoriaId);
        String codigoNormalizado = normalizarCodigo(codigo, nomeExibicao);
        validarCodigoCategoriaDisponivel(codigoNormalizado, categoriaAtual.getId());
        return salvarCategoriaESincronizarItens(
                new CategoriaCardapio(
                        categoriaAtual.getId(),
                        codigoNormalizado,
                        nomeExibicao,
                        ordemExibicao,
                        categoriaAtual.isAtivo()
                )
        );
    }

    public CategoriaCardapio atualizarStatusCategoria(int categoriaId, boolean ativo) {
        CategoriaCardapio categoriaAtual = buscarCategoriaExistente(categoriaId);
        return salvarCategoriaESincronizarItens(
                new CategoriaCardapio(
                        categoriaAtual.getId(),
                        categoriaAtual.getCodigo(),
                        categoriaAtual.getNomeExibicao(),
                        categoriaAtual.getOrdemExibicao(),
                        ativo
                )
        );
    }

    public CategoriaCardapio atualizarOrdemCategoria(int categoriaId, int ordemExibicao) {
        CategoriaCardapio categoriaAtual = buscarCategoriaExistente(categoriaId);
        return salvarCategoriaESincronizarItens(
                new CategoriaCardapio(
                        categoriaAtual.getId(),
                        categoriaAtual.getCodigo(),
                        categoriaAtual.getNomeExibicao(),
                        ordemExibicao,
                        categoriaAtual.isAtivo()
                )
        );
    }

    public ItemCardapio criarItem(String codigo,
                                  String nome,
                                  String descricaoCurta,
                                  BigDecimal precoVenda,
                                  int categoriaId,
                                  TipoItemCardapio tipoItem,
                                  Boolean ativo,
                                  Boolean disponivel,
                                  int ordemExibicao,
                                  Integer produtoVinculadoId) {
        CategoriaCardapio categoria = buscarCategoriaExistente(categoriaId);
        String codigoNormalizado = normalizarCodigo(codigo, nome);
        validarCodigoItemDisponivel(codigoNormalizado, null);
        Produto produtoVinculado = resolverProdutoVinculado(tipoItem, produtoVinculadoId);

        return itemCardapioRepository.salvar(new ItemCardapio(
                0,
                codigoNormalizado,
                nome,
                descricaoCurta,
                precoVenda,
                categoria,
                tipoItem,
                ativo,
                disponivel,
                ordemExibicao,
                produtoVinculado
        ));
    }

    public ItemCardapio atualizarItem(int itemId,
                                      String codigo,
                                      String nome,
                                      String descricaoCurta,
                                      BigDecimal precoVenda,
                                      int categoriaId,
                                      TipoItemCardapio tipoItem,
                                      int ordemExibicao,
                                      Integer produtoVinculadoId) {
        ItemCardapio itemAtual = buscarItemExistente(itemId);
        CategoriaCardapio categoria = buscarCategoriaExistente(categoriaId);
        String codigoNormalizado = normalizarCodigo(codigo, nome);
        validarCodigoItemDisponivel(codigoNormalizado, itemAtual.getId());
        Produto produtoVinculado = resolverProdutoVinculado(tipoItem, produtoVinculadoId);

        return itemCardapioRepository.salvar(new ItemCardapio(
                itemAtual.getId(),
                codigoNormalizado,
                nome,
                descricaoCurta,
                precoVenda,
                categoria,
                tipoItem,
                itemAtual.isAtivo(),
                itemAtual.isDisponivel(),
                ordemExibicao,
                produtoVinculado
        ));
    }

    public ItemCardapio atualizarStatusItem(int itemId, boolean ativo) {
        ItemCardapio itemAtual = sincronizarCategoriaVigente(buscarItemExistente(itemId));
        return itemCardapioRepository.salvar(new ItemCardapio(
                itemAtual.getId(),
                itemAtual.getCodigo(),
                itemAtual.getNome(),
                itemAtual.getDescricaoCurta(),
                itemAtual.getPrecoVenda(),
                itemAtual.getCategoriaCardapio(),
                itemAtual.getTipoItem(),
                ativo,
                itemAtual.isDisponivel(),
                itemAtual.getOrdemExibicao(),
                itemAtual.getProdutoVinculado()
        ));
    }

    public ItemCardapio atualizarDisponibilidadeItem(int itemId, boolean disponivel) {
        ItemCardapio itemAtual = sincronizarCategoriaVigente(buscarItemExistente(itemId));
        return itemCardapioRepository.salvar(new ItemCardapio(
                itemAtual.getId(),
                itemAtual.getCodigo(),
                itemAtual.getNome(),
                itemAtual.getDescricaoCurta(),
                itemAtual.getPrecoVenda(),
                itemAtual.getCategoriaCardapio(),
                itemAtual.getTipoItem(),
                itemAtual.isAtivo(),
                disponivel,
                itemAtual.getOrdemExibicao(),
                itemAtual.getProdutoVinculado()
        ));
    }

    public ItemCardapio atualizarOrdemItem(int itemId, int ordemExibicao) {
        ItemCardapio itemAtual = sincronizarCategoriaVigente(buscarItemExistente(itemId));
        return itemCardapioRepository.salvar(new ItemCardapio(
                itemAtual.getId(),
                itemAtual.getCodigo(),
                itemAtual.getNome(),
                itemAtual.getDescricaoCurta(),
                itemAtual.getPrecoVenda(),
                itemAtual.getCategoriaCardapio(),
                itemAtual.getTipoItem(),
                itemAtual.isAtivo(),
                itemAtual.isDisponivel(),
                ordemExibicao,
                itemAtual.getProdutoVinculado()
        ));
    }

    private CategoriaCardapio buscarCategoriaExistente(int categoriaId) {
        return categoriaCardapioRepository.buscarPorId(categoriaId)
                .orElseThrow(() -> new IllegalArgumentException("Categoria de cardapio nao encontrada: " + categoriaId));
    }

    private ItemCardapio buscarItemExistente(int itemId) {
        return itemCardapioRepository.buscarPorId(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item de cardapio nao encontrado: " + itemId));
    }

    private Produto resolverProdutoVinculado(TipoItemCardapio tipoItem, Integer produtoVinculadoId) {
        if (tipoItem == null) {
            throw new IllegalArgumentException("Tipo do item de cardapio e obrigatorio.");
        }

        if (tipoItem == TipoItemCardapio.PREPARADO_SOB_DEMANDA) {
            if (produtoVinculadoId != null && produtoVinculadoId > 0) {
                throw new IllegalArgumentException(
                        "Itens preparados sob demanda nao devem vincular produto de estoque."
                );
            }
            return null;
        }

        if (produtoVinculadoId == null || produtoVinculadoId <= 0) {
            return null;
        }

        Produto produto = produtoRepository.buscarPorId(produtoVinculadoId)
                .orElseThrow(() -> new IllegalArgumentException("Produto vinculado nao encontrado: " + produtoVinculadoId));
        if (!produto.isControladoPorEstoque()) {
            throw new IllegalArgumentException("Produto vinculado precisa estar controlado por estoque.");
        }
        return produto;
    }

    private void validarCodigoCategoriaDisponivel(String codigo, Integer categoriaAtualId) {
        categoriaCardapioRepository.buscarPorCodigo(codigo)
                .filter(categoria -> categoriaAtualId == null || categoria.getId() != categoriaAtualId)
                .ifPresent(categoria -> {
                    throw new IllegalArgumentException("Ja existe uma categoria de cardapio com este codigo.");
                });
    }

    private void validarCodigoItemDisponivel(String codigo, Integer itemAtualId) {
        itemCardapioRepository.buscarPorCodigo(codigo)
                .filter(item -> itemAtualId == null || item.getId() != itemAtualId)
                .ifPresent(item -> {
                    throw new IllegalArgumentException("Ja existe um item de cardapio com este codigo.");
                });
    }

    private boolean categoriaAtiva(ItemCardapio item) {
        return item != null && categoriaAtiva(item.getCategoriaCardapio());
    }

    private boolean categoriaAtiva(CategoriaCardapio categoria) {
        return categoria != null && categoria.isAtivo();
    }

    private String montarIndiceBusca(ItemCardapio item) {
        return normalizarTexto(String.join(" ",
                valorOuVazio(item.getNome()),
                valorOuVazio(item.getDescricaoCurta()),
                item.getCategoriaCardapio() != null ? valorOuVazio(item.getCategoriaCardapio().getNomeExibicao()) : "",
                valorOuVazio(item.getCodigo())
        ));
    }

    private String valorOuVazio(String valor) {
        return valor == null ? "" : valor;
    }

    private CategoriaCardapio salvarCategoriaESincronizarItens(CategoriaCardapio categoriaCardapio) {
        CategoriaCardapio categoriaPersistida = categoriaCardapioRepository.salvar(categoriaCardapio);
        sincronizarCategoriaNosItens(categoriaPersistida);
        return categoriaPersistida;
    }

    private void sincronizarCategoriaNosItens(CategoriaCardapio categoriaVigente) {
        if (categoriaVigente == null) {
            return;
        }

        itemCardapioRepository.listarTodos().stream()
                .filter(item -> item.getCategoriaCardapio() != null && item.getCategoriaCardapio().getId() == categoriaVigente.getId())
                .filter(item -> categoriaDivergente(item.getCategoriaCardapio(), categoriaVigente))
                .forEach(item -> itemCardapioRepository.salvar(new ItemCardapio(
                        item.getId(),
                        item.getCodigo(),
                        item.getNome(),
                        item.getDescricaoCurta(),
                        item.getPrecoVenda(),
                        categoriaVigente,
                        item.getTipoItem(),
                        item.isAtivo(),
                        item.isDisponivel(),
                        item.getOrdemExibicao(),
                        item.getProdutoVinculado()
                )));
    }

    private boolean categoriaDivergente(CategoriaCardapio atual, CategoriaCardapio vigente) {
        return atual == null
                || atual.isAtivo() != vigente.isAtivo()
                || atual.getOrdemExibicao() != vigente.getOrdemExibicao()
                || !atual.getCodigo().equals(vigente.getCodigo())
                || !atual.getNomeExibicao().equals(vigente.getNomeExibicao());
    }

    private ItemCardapio sincronizarCategoriaVigente(ItemCardapio item) {
        if (item == null || item.getCategoriaCardapio() == null) {
            return item;
        }

        CategoriaCardapio categoriaVigente = categoriaCardapioRepository.buscarPorId(item.getCategoriaCardapio().getId())
                .orElse(item.getCategoriaCardapio());
        if (categoriaVigente.equals(item.getCategoriaCardapio())
                && categoriaVigente.isAtivo() == item.getCategoriaCardapio().isAtivo()
                && categoriaVigente.getOrdemExibicao() == item.getCategoriaCardapio().getOrdemExibicao()
                && categoriaVigente.getCodigo().equals(item.getCategoriaCardapio().getCodigo())
                && categoriaVigente.getNomeExibicao().equals(item.getCategoriaCardapio().getNomeExibicao())) {
            return item;
        }

        return new ItemCardapio(
                item.getId(),
                item.getCodigo(),
                item.getNome(),
                item.getDescricaoCurta(),
                item.getPrecoVenda(),
                categoriaVigente,
                item.getTipoItem(),
                item.isAtivo(),
                item.isDisponivel(),
                item.getOrdemExibicao(),
                item.getProdutoVinculado()
        );
    }

    private Comparator<CategoriaCardapio> comparadorCategorias() {
        return Comparator.comparingInt(CategoriaCardapio::getOrdemExibicao)
                .thenComparing(CategoriaCardapio::getNomeExibicao, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(CategoriaCardapio::getId);
    }

    private Comparator<ItemCardapio> comparadorItens() {
        return Comparator.comparingInt((ItemCardapio item) -> item.getCategoriaCardapio().getOrdemExibicao())
                .thenComparing(item -> item.getCategoriaCardapio().getNomeExibicao(), String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(ItemCardapio::getOrdemExibicao)
                .thenComparing(ItemCardapio::getNome, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(ItemCardapio::getId);
    }

    private static String normalizarCodigo(String codigo, String fallback) {
        String base = codigo != null && !codigo.isBlank() ? codigo : fallback;
        if (base == null || base.isBlank()) {
            throw new IllegalArgumentException("Codigo e obrigatorio.");
        }

        String normalizado = Normalizer.normalize(base, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "")
                .replaceAll("_+", "_");
        if (normalizado.isBlank()) {
            throw new IllegalArgumentException("Codigo e obrigatorio.");
        }
        return normalizado;
    }

    private static String normalizarTexto(String valor) {
        if (valor == null || valor.isBlank()) {
            return "";
        }

        return Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }
}
