package repository;

import model.CategoriaCardapio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class InMemoryCategoriaCardapioRepository implements CategoriaCardapioRepository {

    private final List<CategoriaCardapio> categorias = new ArrayList<>();
    private final AtomicInteger sequenceId = new AtomicInteger(1);

    @Override
    public synchronized CategoriaCardapio salvar(CategoriaCardapio categoriaCardapio) {
        if (categoriaCardapio == null) {
            throw new IllegalArgumentException("Categoria de cardapio nao pode ser nula.");
        }

        CategoriaCardapio persistida = categoriaCardapio;
        if (categoriaCardapio.getId() <= 0) {
            persistida = categoriaCardapio.comId(sequenceId.getAndIncrement());
        } else {
            sequenceId.updateAndGet(atual -> Math.max(atual, categoriaCardapio.getId() + 1));
        }

        final CategoriaCardapio categoriaPersistida = persistida;
        categorias.removeIf(categoria -> categoria.getId() == categoriaPersistida.getId());
        categorias.add(categoriaPersistida);
        categorias.sort(Comparator
                .comparingInt(CategoriaCardapio::getOrdemExibicao)
                .thenComparing(CategoriaCardapio::getNomeExibicao, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(CategoriaCardapio::getId));
        return categoriaPersistida;
    }

    @Override
    public synchronized Optional<CategoriaCardapio> buscarPorId(int id) {
        return categorias.stream()
                .filter(categoria -> categoria.getId() == id)
                .findFirst();
    }

    @Override
    public synchronized Optional<CategoriaCardapio> buscarPorCodigo(String codigo) {
        if (codigo == null) {
            return Optional.empty();
        }
        return categorias.stream()
                .filter(categoria -> categoria.getCodigo().equalsIgnoreCase(codigo.trim()))
                .findFirst();
    }

    @Override
    public synchronized List<CategoriaCardapio> listarTodos() {
        return new ArrayList<>(categorias);
    }
}
