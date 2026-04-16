package repository;

import model.CotacaoMensal;
import org.springframework.stereotype.Repository;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Repository
public class CotacaoRepositoryAdapter implements CotacaoRepository {

    private final JpaCotacaoRepository delegate;

    public CotacaoRepositoryAdapter(JpaCotacaoRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public long proximoId() {
        return delegate.findTopByOrderByIdDesc()
                .map(cotacao -> cotacao.getId() + 1)
                .orElse(1L);
    }

    @Override
    public CotacaoMensal salvar(CotacaoMensal cotacao) {
        return delegate.save(cotacao);
    }

    @Override
    public Optional<CotacaoMensal> buscarPorProdutoEMes(int produtoId, YearMonth mes) {
        return delegate.findByProdutoIdAndMesReferencia(produtoId, mes);
    }

    @Override
    public List<CotacaoMensal> buscarHistoricoPorProduto(int produtoId) {
        return delegate.findAllByProdutoIdOrderByMesReferenciaAsc(produtoId);
    }

    @Override
    public List<CotacaoMensal> listarTodas() {
        return delegate.findAllByOrderByMesReferenciaDescIdDesc();
    }
}
