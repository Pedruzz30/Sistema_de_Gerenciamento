package repository;

import model.VariacaoItemCardapio;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Repository
public class VariacaoItemCardapioRepositoryAdapter implements VariacaoItemCardapioRepository {

    private final JpaVariacaoItemCardapioRepository delegate;

    public VariacaoItemCardapioRepositoryAdapter(JpaVariacaoItemCardapioRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public VariacaoItemCardapio salvar(VariacaoItemCardapio variacao) {
        return delegate.save(variacao);
    }

    @Override
    public Optional<VariacaoItemCardapio> buscarPorId(long id) {
        return delegate.findById(id);
    }

    @Override
    public List<VariacaoItemCardapio> listarPorItem(int itemCardapioId) {
        return delegate.findByItemOrdenado(itemCardapioId);
    }

    @Override
    public List<VariacaoItemCardapio> listarPorIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return delegate.findByIdsComContexto(ids);
    }

    @Override
    public Optional<VariacaoItemCardapio> buscarPadraoAtivaPorItem(int itemCardapioId) {
        return delegate.findPadraoAtivaByItem(itemCardapioId);
    }

    @Override
    public boolean existeCodigoNoItem(int itemCardapioId, String codigo, Long ignorarId) {
        return delegate.existsCodigoNoItem(itemCardapioId, codigo, ignorarId);
    }

    @Override
    public boolean existePadraoAtivaNoItem(int itemCardapioId, Long ignorarId) {
        return delegate.existsPadraoAtivaNoItem(itemCardapioId, ignorarId);
    }

    @Override
    public List<VariacaoItemCardapio> listarTodos() {
        return delegate.findAllComContexto().stream()
                .sorted(Comparator
                        .comparing((VariacaoItemCardapio v) -> v.getItemCardapio().getId())
                        .thenComparing(v -> !v.isPadrao())
                        .thenComparingInt(VariacaoItemCardapio::getOrdemExibicao)
                        .thenComparing(VariacaoItemCardapio::getNome)
                        .thenComparingLong(VariacaoItemCardapio::getId))
                .toList();
    }
}
