package repository;

import model.EmbalagemProduto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryEmbalagemProdutoRepository
        implements EmbalagemProdutoRepository {

    private static final Comparator<EmbalagemProduto> ORDENACAO_POR_NOME =
            Comparator.comparing(
                    embalagem -> embalagem.getNome(),
                    String.CASE_INSENSITIVE_ORDER);

    private final List<EmbalagemProduto> embalagens = new ArrayList<>();
    private final AtomicLong sequenceId = new AtomicLong(1);

    @Override
    public synchronized EmbalagemProduto salvar(EmbalagemProduto embalagem) {
        if (embalagem == null) {
            throw new IllegalArgumentException(
                    "Embalagem do produto e obrigatoria.");
        }

        EmbalagemProduto persistida = embalagem;

        if (embalagem.getId() <= 0) {
            persistida = embalagem.comId(sequenceId.getAndIncrement());
        } else {
            sequenceId.updateAndGet(atual ->
                    Math.max(atual, embalagem.getId() + 1));
        }

        final EmbalagemProduto embalagemPersistida = persistida;

        embalagens.removeIf(registro ->
                registro.getId() == embalagemPersistida.getId());

        embalagens.add(embalagemPersistida);

        return embalagemPersistida;
    }

    @Override
    public synchronized Optional<EmbalagemProduto> buscarPorId(long id) {
        return embalagens.stream()
                .filter(embalagem -> embalagem.getId() == id)
                .findFirst();
    }

    @Override
    public synchronized List<EmbalagemProduto> listarPorProduto(int produtoId) {
        return embalagens.stream()
                .filter(embalagem -> embalagem.pertenceAoProduto(produtoId))
                .sorted(ORDENACAO_POR_NOME)
                .toList();
    }
}
