package repository;

import model.CategoriaCardapio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class InMemoryCategoriaCardapioRepository
        implements CategoriaCardapioRepository {

    private static final Comparator<CategoriaCardapio> ORDENACAO_PADRAO =
            Comparator.comparingInt(
                    (CategoriaCardapio categoria) -> categoria.getOrdemExibicao())
                    .thenComparing(
                            categoria -> categoria.getNomeExibicao(),
                            String.CASE_INSENSITIVE_ORDER)
                    .thenComparingInt(categoria -> categoria.getId());

    private final List<CategoriaCardapio> categorias = new ArrayList<>();
    private final AtomicInteger sequenceId = new AtomicInteger(1);

    @Override
    public synchronized CategoriaCardapio salvar(
            CategoriaCardapio categoriaCardapio) {

        if (categoriaCardapio == null) {
            throw new IllegalArgumentException(
                    "Categoria de cardapio nao pode ser nula.");
        }

        CategoriaCardapio persistida = categoriaCardapio;

        if (categoriaCardapio.getId() <= 0) {
            persistida = categoriaCardapio.comId(
                    sequenceId.getAndIncrement());
        } else {
            sequenceId.updateAndGet(atual ->
                    Math.max(atual, categoriaCardapio.getId() + 1));
        }

        final CategoriaCardapio categoriaPersistida = persistida;

        categorias.removeIf(categoria ->
                categoria.getId() == categoriaPersistida.getId());

        categorias.add(categoriaPersistida);
        categorias.sort(ORDENACAO_PADRAO);

        return categoriaPersistida;
    }

    @Override
    public synchronized Optional<CategoriaCardapio> buscarPorId(int id) {
        return categorias.stream()
                .filter(categoria -> categoria.getId() == id)
                .findFirst();
    }

    @Override
    public synchronized Optional<CategoriaCardapio> buscarPorCodigo(
            String codigo) {

        if (codigo == null) {
            return Optional.empty();
        }

        String codigoNormalizado = codigo.trim();

        return categorias.stream()
                .filter(categoria -> categoria.getCodigo()
                        .equalsIgnoreCase(codigoNormalizado))
                .findFirst();
    }

    @Override
    public synchronized List<CategoriaCardapio> listarTodos() {
        return new ArrayList<>(categorias);
    }
}
