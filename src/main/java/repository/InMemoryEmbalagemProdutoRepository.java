package repository;

import model.EmbalagemProduto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryEmbalagemProdutoRepository implements EmbalagemProdutoRepository {

    private final List<EmbalagemProduto> embalagens = new ArrayList<>();
    private final AtomicLong sequenceId = new AtomicLong(1);

    @Override
    public synchronized EmbalagemProduto salvar(EmbalagemProduto embalagem) {
        EmbalagemProduto persistida = embalagem;
        if (embalagem.getId() <= 0) {
            persistida = embalagem.comId(sequenceId.getAndIncrement());
        } else {
            sequenceId.updateAndGet(atual -> Math.max(atual, embalagem.getId() + 1));
        }
        EmbalagemProduto finalPersistida = persistida;
        embalagens.removeIf(e -> e.getId() == finalPersistida.getId());
        embalagens.add(persistida);
        return persistida;
    }

    @Override
    public synchronized Optional<EmbalagemProduto> buscarPorId(long id) {
        return embalagens.stream().filter(e -> e.getId() == id).findFirst();
    }

    @Override
    public synchronized List<EmbalagemProduto> listarPorProduto(int produtoId) {
        return embalagens.stream()
                .filter(e -> e.pertenceAoProduto(produtoId))
                .sorted(Comparator.comparing(EmbalagemProduto::getNome, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
