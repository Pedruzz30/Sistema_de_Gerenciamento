package repository;

import model.EstoqueProduto;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryEstoqueProdutoRepository implements EstoqueProdutoRepository {

    private final List<EstoqueProduto> estoques = new ArrayList<>();
    private final AtomicLong sequenceId = new AtomicLong(1);

    @Override
    public synchronized EstoqueProduto salvar(EstoqueProduto estoque) {
        EstoqueProduto persistido = estoque;
        if (estoque.getId() <= 0) {
            persistido = estoque.comId(sequenceId.getAndIncrement());
        } else {
            sequenceId.updateAndGet(atual -> Math.max(atual, estoque.getId() + 1));
        }
        EstoqueProduto finalPersistido = persistido;
        estoques.removeIf(e -> e.getId() == finalPersistido.getId()
                || (e.getFilialId().equals(finalPersistido.getFilialId())
                && e.getProduto().getId() == finalPersistido.getProduto().getId()));
        estoques.add(persistido);
        return persistido;
    }

    @Override
    public synchronized Optional<EstoqueProduto> buscarPorFilialEProduto(Long filialId, int produtoId) {
        return estoques.stream()
                .filter(e -> e.getFilialId().equals(filialId) && e.getProduto().getId() == produtoId)
                .findFirst();
    }

    @Override
    public synchronized List<EstoqueProduto> listarPorFilialEProdutos(Long filialId, List<Integer> produtoIds) {
        if (produtoIds == null || produtoIds.isEmpty()) {
            return List.of();
        }
        return estoques.stream()
                .filter(e -> e.getFilialId().equals(filialId) && produtoIds.contains(e.getProduto().getId()))
                .toList();
    }

    @Override
    public synchronized Optional<EstoqueProduto> buscarPorFilialEProdutoComLock(Long filialId, int produtoId) {
        return buscarPorFilialEProduto(filialId, produtoId);
    }

    @Override
    public synchronized List<EstoqueProduto> listarTodos() {
        return new ArrayList<>(estoques);
    }
}
