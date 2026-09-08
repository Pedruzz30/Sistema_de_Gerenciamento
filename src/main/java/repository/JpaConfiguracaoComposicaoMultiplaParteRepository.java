package repository;

import model.ConfiguracaoComposicaoMultiplaParte;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JpaConfiguracaoComposicaoMultiplaParteRepository extends JpaRepository<ConfiguracaoComposicaoMultiplaParte, Long> {

    Optional<ConfiguracaoComposicaoMultiplaParte> findByConfiguracaoIdAndVariacaoParteId(long configuracaoId, long variacaoParteId);

    List<ConfiguracaoComposicaoMultiplaParte> findByConfiguracaoIdOrderByOrdemExibicaoAscIdAsc(long configuracaoId);

    List<ConfiguracaoComposicaoMultiplaParte> findByConfiguracaoIdAndAtivaTrueOrderByOrdemExibicaoAscIdAsc(long configuracaoId);
}
