package repository;

import model.OpcaoModificadorComponente;

import java.util.List;
import java.util.Optional;

public interface OpcaoModificadorComponenteRepository {

    OpcaoModificadorComponente salvar(OpcaoModificadorComponente componente);

    Optional<OpcaoModificadorComponente> buscarPorId(long id);

    List<OpcaoModificadorComponente> listarPorOpcao(long opcaoId);

    List<OpcaoModificadorComponente> listarPorOpcoes(List<Long> opcaoIds);

    void remover(OpcaoModificadorComponente componente);
}
