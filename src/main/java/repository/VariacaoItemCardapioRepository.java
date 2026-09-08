package repository;

import model.VariacaoItemCardapio;

import java.util.List;
import java.util.Optional;

public interface VariacaoItemCardapioRepository {

    VariacaoItemCardapio salvar(VariacaoItemCardapio variacao);

    Optional<VariacaoItemCardapio> buscarPorId(long id);

    List<VariacaoItemCardapio> listarPorItem(int itemCardapioId);

    List<VariacaoItemCardapio> listarPorIds(List<Long> ids);

    Optional<VariacaoItemCardapio> buscarPadraoAtivaPorItem(int itemCardapioId);

    boolean existeCodigoNoItem(int itemCardapioId, String codigo, Long ignorarId);

    boolean existePadraoAtivaNoItem(int itemCardapioId, Long ignorarId);

    List<VariacaoItemCardapio> listarTodos();
}
