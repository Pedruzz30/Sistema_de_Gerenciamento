package repository;

import model.ConfiguracaoComposicaoMultipla;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ConfiguracaoComposicaoMultiplaRepositoryAdapter implements ConfiguracaoComposicaoMultiplaRepository {

    private final JpaConfiguracaoComposicaoMultiplaRepository delegate;

    public ConfiguracaoComposicaoMultiplaRepositoryAdapter(JpaConfiguracaoComposicaoMultiplaRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public ConfiguracaoComposicaoMultipla salvar(ConfiguracaoComposicaoMultipla configuracao) {
        return delegate.save(configuracao);
    }

    @Override
    public Optional<ConfiguracaoComposicaoMultipla> buscarPorId(long id) {
        return delegate.findById(id);
    }

    @Override
    public Optional<ConfiguracaoComposicaoMultipla> buscarPorVariacao(long variacaoId) {
        return delegate.findByVariacaoItemCardapioId(variacaoId);
    }

    @Override
    public List<ConfiguracaoComposicaoMultipla> listarAtivasPorVariacoes(List<Long> variacaoIds) {
        if (variacaoIds == null || variacaoIds.isEmpty()) {
            return List.of();
        }
        return delegate.findByVariacaoItemCardapioIdInAndAtivoTrue(variacaoIds);
    }
}
