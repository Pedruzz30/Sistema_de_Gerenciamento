package repository;

import model.ConfiguracaoComposicaoMultipla;

import java.util.List;
import java.util.Optional;

public interface ConfiguracaoComposicaoMultiplaRepository {

    ConfiguracaoComposicaoMultipla salvar(ConfiguracaoComposicaoMultipla configuracao);

    Optional<ConfiguracaoComposicaoMultipla> buscarPorId(long id);

    Optional<ConfiguracaoComposicaoMultipla> buscarPorVariacao(long variacaoId);

    List<ConfiguracaoComposicaoMultipla> listarAtivasPorVariacoes(List<Long> variacaoIds);
}
