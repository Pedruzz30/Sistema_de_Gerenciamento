package repository;

import model.ItemCardapio;
import model.Produto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class InMemoryItemCardapioRepository
        implements ItemCardapioRepository {

    private static final Comparator<ItemCardapio> ORDENACAO_PADRAO =
            Comparator.comparingInt(
                    (ItemCardapio item) ->
                            item.getCategoriaCardapio().getOrdemExibicao())
                    .thenComparing(
                            item -> item.getCategoriaCardapio().getNomeExibicao(),
                            String.CASE_INSENSITIVE_ORDER)
                    .thenComparingInt(item -> item.getOrdemExibicao())
                    .thenComparing(
                            item -> item.getNome(),
                            String.CASE_INSENSITIVE_ORDER)
                    .thenComparingInt(item -> item.getId());

    private final List<ItemCardapio> itens = new ArrayList<>();
    private final AtomicInteger sequenceId = new AtomicInteger(1);

    @Override
    public synchronized ItemCardapio salvar(ItemCardapio itemCardapio) {
        if (itemCardapio == null) {
            throw new IllegalArgumentException(
                    "Item de cardapio nao pode ser nulo.");
        }

        ItemCardapio persistido = itemCardapio;

        if (itemCardapio.getId() <= 0) {
            persistido = itemCardapio.comId(
                    sequenceId.getAndIncrement());
        } else {
            sequenceId.updateAndGet(atual ->
                    Math.max(atual, itemCardapio.getId() + 1));
        }

        final ItemCardapio itemPersistido = persistido;

        itens.removeIf(item ->
                item.getId() == itemPersistido.getId());

        itens.add(itemPersistido);
        itens.sort(ORDENACAO_PADRAO);

        return itemPersistido;
    }

    @Override
    public synchronized Optional<ItemCardapio> buscarPorId(int id) {
        return itens.stream()
                .filter(item -> item.getId() == id)
                .findFirst();
    }

    @Override
    public synchronized Optional<ItemCardapio> buscarPorCodigo(
            String codigo) {

        if (codigo == null) {
            return Optional.empty();
        }

        String codigoNormalizado = codigo.trim();

        return itens.stream()
                .filter(item -> item.getCodigo()
                        .equalsIgnoreCase(codigoNormalizado))
                .findFirst();
    }

    @Override
    public synchronized Optional<ItemCardapio>
            buscarPrimeiroPorProdutoVinculadoId(int produtoId) {

        return itens.stream()
                .filter(item -> item.possuiProdutoVinculado())
                .filter(item -> {
                    Produto produto = item.getProdutoVinculado();

                    return produto != null
                            && produto.getId() == produtoId;
                })
                .findFirst();
    }

    @Override
    public synchronized List<ItemCardapio> listarTodos() {
        return new ArrayList<>(itens);
    }
}
