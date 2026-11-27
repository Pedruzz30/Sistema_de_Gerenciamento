package repository;

import model.Produto;
import org.springframework.lang.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class InMemoryProdutoRepository implements ProdutoRepository {

    private final List<Produto> produtos = new ArrayList<>();
    private final AtomicInteger sequenceId = new AtomicInteger(1);

    @Override
    public synchronized Produto salvar(@NonNull Produto produto) {
        if (produto == null) {
            throw new IllegalArgumentException("Produto não pode ser nulo.");
        }

        Produto persistido = produto;
        if (produto.getId() <= 0) {
            persistido = new Produto(
                    sequenceId.getAndIncrement(),
                    produto.getNome(),
                    produto.getQuantidadeAtual(),
                    produto.getQuantidadeMinima(),
                    produto.getPrecoUnitario(),
                    produto.getCategoriaEstoque(),
                    produto.isControladoPorEstoque(),
                    produto.getUnidadeBase(),
                    produto.getPoliticaFalta(),
                    produto.isAtivo()
            );
        } else {
            sequenceId.updateAndGet(atual -> Math.max(atual, produto.getId() + 1));
        }

        final int idPersistido = persistido.getId();
        produtos.removeIf(p -> p.getId() == idPersistido);
        produtos.add(persistido);
        return persistido;
    }

    @Override
    public synchronized Optional<Produto> buscarPorId(int id) {
        return produtos.stream()
                .filter(p -> p.getId() == id)
                .findFirst();
    }

    @Override
    public synchronized Optional<Produto> buscarPorNome(String nome) {
        return produtos.stream()
                .filter(p -> p.getNome().equalsIgnoreCase(nome))
                .findFirst();
    }

    @Override
    public synchronized List<Produto> listarTodos() {
        List<Produto> ordenados = new ArrayList<>(produtos);
        ordenados.sort(Comparator.comparingInt(Produto::getId));
        return ordenados;
    }
}
