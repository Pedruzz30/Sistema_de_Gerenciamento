package repository;

import model.VariacaoGrupoModificador;

import java.util.List;
import java.util.Optional;

public interface VariacaoGrupoModificadorRepository {

    VariacaoGrupoModificador salvar(VariacaoGrupoModificador associacao);

    Optional<VariacaoGrupoModificador> buscarPorId(long id);

    Optional<VariacaoGrupoModificador> buscarPorVariacaoEGrupo(long variacaoId, long grupoId);

    List<VariacaoGrupoModificador> listarPorVariacao(long variacaoId);

    List<VariacaoGrupoModificador> listarAtivasPorVariacao(long variacaoId);

    List<VariacaoGrupoModificador> listarAtivasPorVariacoes(List<Long> variacaoIds);
}
