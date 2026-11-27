package repository;

import model.IncompatibilidadeOpcaoModificador;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

@Repository
public class IncompatibilidadeOpcaoModificadorRepositoryAdapter
        implements IncompatibilidadeOpcaoModificadorRepository {

    private final JpaIncompatibilidadeOpcaoModificadorRepository delegate;

    public IncompatibilidadeOpcaoModificadorRepositoryAdapter(
            JpaIncompatibilidadeOpcaoModificadorRepository delegate) {

        this.delegate = Objects.requireNonNull(
                delegate,
                "Repositorio JPA de incompatibilidade de modificadores e obrigatorio.");
    }

    @Override
    public IncompatibilidadeOpcaoModificador salvar(
            IncompatibilidadeOpcaoModificador incompatibilidade) {

        if (incompatibilidade == null) {
            throw new IllegalArgumentException(
                    "Incompatibilidade de opcao modificadora e obrigatoria.");
        }

        return delegate.save(incompatibilidade);
    }

    @Override
    public List<IncompatibilidadeOpcaoModificador> listarAtivasPorOpcoes(
            List<Long> opcaoIds) {

        if (opcaoIds == null || opcaoIds.isEmpty()) {
            return List.of();
        }

        return delegate.findAtivasByOpcoes(opcaoIds);
    }
}
