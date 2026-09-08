package repository;

import model.IncompatibilidadeOpcaoModificador;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class IncompatibilidadeOpcaoModificadorRepositoryAdapter
        implements IncompatibilidadeOpcaoModificadorRepository {

    private final JpaIncompatibilidadeOpcaoModificadorRepository delegate;

    public IncompatibilidadeOpcaoModificadorRepositoryAdapter(
            JpaIncompatibilidadeOpcaoModificadorRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public IncompatibilidadeOpcaoModificador salvar(IncompatibilidadeOpcaoModificador incompatibilidade) {
        return delegate.save(incompatibilidade);
    }

    @Override
    public List<IncompatibilidadeOpcaoModificador> listarAtivasPorOpcoes(List<Long> opcaoIds) {
        if (opcaoIds == null || opcaoIds.isEmpty()) {
            return List.of();
        }
        return delegate.findAtivasByOpcoes(opcaoIds);
    }
}
