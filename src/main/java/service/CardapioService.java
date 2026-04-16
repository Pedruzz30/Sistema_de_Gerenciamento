package service;

import model.CategoriaCardapio;
import model.ItemCardapio;
import model.Produto;
import repository.CategoriaCardapioRepository;
import repository.ItemCardapioRepository;

import java.text.Normalizer;
import java.util.List;

public class CardapioService {

    private final CategoriaCardapioRepository categoriaCardapioRepository;
    private final ItemCardapioRepository itemCardapioRepository;

    public CardapioService(CategoriaCardapioRepository categoriaCardapioRepository,
                           ItemCardapioRepository itemCardapioRepository) {
        this.categoriaCardapioRepository = categoriaCardapioRepository;
        this.itemCardapioRepository = itemCardapioRepository;
    }

    public List<CategoriaCardapio> listarCategoriasAtivas() {
        return categoriaCardapioRepository.listarTodos().stream()
                .filter(CategoriaCardapio::isAtivo)
                .toList();
    }

    public List<ItemCardapio> listarItensAtivos(String categoriaCodigo, String busca) {
        String categoriaNormalizada = normalizarTexto(categoriaCodigo);
        String buscaNormalizada = normalizarTexto(busca);

        return itemCardapioRepository.listarTodos().stream()
                .filter(ItemCardapio::isAtivo)
                .filter(item -> item.getCategoriaCardapio() != null && item.getCategoriaCardapio().isAtivo())
                .filter(item -> categoriaNormalizada.isEmpty()
                        || normalizarTexto(item.getCategoriaCardapio().getCodigo()).equals(categoriaNormalizada))
                .filter(item -> buscaNormalizada.isEmpty() || montarIndiceBusca(item).contains(buscaNormalizada))
                .toList();
    }

    public ItemCardapio buscarItemAtivoPorId(int itemCardapioId) {
        ItemCardapio item = itemCardapioRepository.buscarPorId(itemCardapioId)
                .orElseThrow(() -> new IllegalArgumentException("Item de cardapio nao encontrado: " + itemCardapioId));
        if (!item.isAtivo() || item.getCategoriaCardapio() == null || !item.getCategoriaCardapio().isAtivo()) {
            throw new IllegalArgumentException("Item de cardapio inativo: " + itemCardapioId);
        }
        return item;
    }

    public boolean itemDisponivel(ItemCardapio itemCardapio) {
        if (itemCardapio == null || !itemCardapio.isAtivo()) {
            return false;
        }
        if (itemCardapio.isPreparadoSobDemanda()) {
            return true;
        }

        Produto produto = itemCardapio.getProdutoVinculado();
        return produto != null
                && produto.isControladoPorEstoque()
                && produto.getQuantidadeAtual() > 0;
    }

    public Integer quantidadeDisponivel(ItemCardapio itemCardapio) {
        Produto produto = itemCardapio != null ? itemCardapio.getProdutoVinculado() : null;
        if (produto == null || !produto.isControladoPorEstoque()) {
            return null;
        }
        return produto.getQuantidadeAtual();
    }

    public String nivelEstoque(ItemCardapio itemCardapio) {
        Produto produto = itemCardapio != null ? itemCardapio.getProdutoVinculado() : null;
        if (produto == null || !produto.isControladoPorEstoque()) {
            return null;
        }
        return produto.calcularNivel().name();
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
