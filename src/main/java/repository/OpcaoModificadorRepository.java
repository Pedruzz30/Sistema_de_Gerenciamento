package repository;

import model.OpcaoModificador;

import java.util.List;
import java.util.Optional;

public interface OpcaoModificadorRepository {

    OpcaoModificador salvar(OpcaoModificador opcao);

    Optional<OpcaoModificador> buscarPorId(long id);

    List<OpcaoModificador> listarPorGrupo(long grupoId);

    List<OpcaoModificador> listarPorGrupos(List<Long> grupoIds);

    List<OpcaoModificador> listarPorIds(List<Long> ids);

    boolean existeCodigoNoGrupo(long grupoId, String codigo, Long ignorarId);
}
