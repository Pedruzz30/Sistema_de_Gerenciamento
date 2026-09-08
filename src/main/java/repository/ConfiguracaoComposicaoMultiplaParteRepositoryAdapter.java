package repository;

import model.ConfiguracaoComposicaoMultiplaParte;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ConfiguracaoComposicaoMultiplaParteRepositoryAdapter implements ConfiguracaoComposicaoMultiplaParteRepository {

    private final JpaConfiguracaoComposicaoMultiplaParteRepository delegate;

    public ConfiguracaoComposicaoMultiplaParteRepositoryAdapter(JpaConfiguracaoComposicaoMultiplaParteRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public ConfiguracaoComposicaoMultiplaParte salvar(ConfiguracaoComposicaoMultiplaParte parte) {
        return delegate.save(parte);
    }

    @Override
    public Optional<ConfiguracaoComposicaoMultiplaParte> buscarPorConfiguracaoEVariacaoParte(long configuracaoId, long variacaoParteId) {
        return delegate.findByConfiguracaoIdAndVariacaoParteId(configuracaoId, variacaoParteId);
    }

    @Override
    public List<ConfiguracaoComposicaoMultiplaParte> listarPorConfiguracao(long configuracaoId) {
        return delegate.findByConfiguracaoIdOrderByOrdemExibicaoAscIdAsc(configuracaoId);
    }

    @Override
    public List<ConfiguracaoComposicaoMultiplaParte> listarAtivasPorConfiguracao(long configuracaoId) {
        return delegate.findByConfiguracaoIdAndAtivaTrueOrderByOrdemExibicaoAscIdAsc(configuracaoId);
    }

    @Override
    public void remover(ConfiguracaoComposicaoMultiplaParte parte) {
        delegate.delete(parte);
    }
}
