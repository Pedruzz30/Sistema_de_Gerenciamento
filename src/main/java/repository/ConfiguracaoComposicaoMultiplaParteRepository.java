package repository;

import model.ConfiguracaoComposicaoMultiplaParte;

import java.util.List;
import java.util.Optional;

public interface ConfiguracaoComposicaoMultiplaParteRepository {

    ConfiguracaoComposicaoMultiplaParte salvar(ConfiguracaoComposicaoMultiplaParte parte);

    Optional<ConfiguracaoComposicaoMultiplaParte> buscarPorConfiguracaoEVariacaoParte(long configuracaoId, long variacaoParteId);

    List<ConfiguracaoComposicaoMultiplaParte> listarPorConfiguracao(long configuracaoId);

    List<ConfiguracaoComposicaoMultiplaParte> listarAtivasPorConfiguracao(long configuracaoId);

    void remover(ConfiguracaoComposicaoMultiplaParte parte);
}
