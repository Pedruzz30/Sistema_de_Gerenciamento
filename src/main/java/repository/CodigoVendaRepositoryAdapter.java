package repository;

import model.CodigoVenda;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CodigoVendaRepositoryAdapter implements CodigoVendaRepository {

    private final JpaCodigoVendaRepository delegate;

    public CodigoVendaRepositoryAdapter(JpaCodigoVendaRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public CodigoVenda salvar(CodigoVenda codigo) {
        // saveAndFlush: a troca de padrão precisa chegar ao banco antes do
        // próximo insert, por causa do índice único parcial (codigo, padrao).
        return delegate.saveAndFlush(codigo);
    }

    @Override
    public Optional<CodigoVenda> buscarPorId(long id) {
        return delegate.findById(id);
    }

    @Override
    public List<CodigoVenda> listarAtivosPorCodigo(String codigo) {
        return delegate.findByCodigoAndAtivoTrueOrderByPadraoDescIdAsc(codigo);
    }

    @Override
    public List<CodigoVenda> listarAtivosPorVariacao(long variacaoId) {
        return delegate.findByVariacaoIdAndAtivoTrueOrderByIdAsc(variacaoId);
    }

    @Override
    public List<CodigoVenda> listarAtivos() {
        return delegate.findByAtivoTrueOrderByCodigoAscPadraoDescIdAsc();
    }
}
