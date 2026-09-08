package repository;

import model.ConfiguracaoComposicaoMultipla;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JpaConfiguracaoComposicaoMultiplaRepository extends JpaRepository<ConfiguracaoComposicaoMultipla, Long> {

    Optional<ConfiguracaoComposicaoMultipla> findByVariacaoItemCardapioId(long variacaoId);

    List<ConfiguracaoComposicaoMultipla> findByVariacaoItemCardapioIdInAndAtivoTrue(List<Long> variacaoIds);
}
