package repository;

import model.ItemCardapio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class InMemoryItemCardapioRepository implements ItemCardapioRepository {

    private final List<ItemCardapio> itens = new ArrayList<>();
    private final AtomicInteger sequenceId = new AtomicInteger(1);

    @Override
    public synchronized ItemCardapio salvar(ItemCardapio itemCardapio) {
        if (itemCardapio == null) {
            throw new IllegalArgumentException("Item de cardapio nao pode ser nulo.");
        }

        ItemCardapio persistido = itemCardapio;
        if (itemCardapio.getId() <= 0) {
            persistido = itemCardapio.comId(sequenceId.getAndIncrement());
        } else {
            sequenceId.updateAndGet(atual -> Math.max(atual, itemCardapio.getId() + 1));
        }

        itens.removeIf(item -> item.getId() == persistido.getId());
        itens.add(persistido);
        itens.sort(Comparator
                .comparingInt((ItemCardapio item) -> item.getCategoriaCardapio().getOrdemExibicao())
                .thenComparing(item -> item.getCategoriaCardapio().getNomeExibicao(), String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(ItemCardapio::getOrdemExibicao)
                .thenComparing(ItemCardapio::getNome, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(ItemCardapio::getId));
        return persistido;
    }

    @Override
    public synchronized Optional<ItemCardapio> buscarPorId(int id) {
        return itens.stream()
                .filter(item -> item.getId() == id)
                .findFirst();
    }

    @Override
    public synchronized Optional<ItemCardapio> buscarPorCodigo(String codigo) {
        if (codigo == null) {
            return Optional.empty();
        }
        return itens.stream()
                .filter(item -> item.getCodigo().equalsIgnoreCase(codigo.trim()))
                .findFirst();
    }

    @Override
    public synchronized Optional<ItemCardapio> buscarPrimeiroPorProdutoVinculadoId(int produtoId) {
        return itens.stream()
                .filter(ItemCardapio::possuiProdutoVinculado)
                .filter(item -> item.getProdutoVinculado().getId() == produtoId)
                .findFirst();
    }

    @Override
    public synchronized List<ItemCardapio> listarTodos() {
        return new ArrayList<>(itens);
    }
}
