package repository;

import model.CotacaoMensal;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Repository
public class CotacaoRepositoryAdapter implements CotacaoRepository {

    private final JpaCotacaoRepository delegate;

    public CotacaoRepositoryAdapter(JpaCotacaoRepository delegate) {
        this.delegate = Objects.requireNonNull(
                delegate, "Repositorio JPA de cotacao e obrigatorio.");
    }

    @Override
    public long proximoId() {
        return delegate.findTopByOrderByIdDesc()
                .map(cotacao -> cotacao.getId() + 1)
                .orElse(1L);
    }

    @Override
    public CotacaoMensal salvar(CotacaoMensal cotacao) {
        if (cotacao == null) {
            throw new IllegalArgumentException(
                    "Cotacao mensal e obrigatoria.");
        }

        return delegate.save(cotacao);
    }

    @Override
    public Optional<CotacaoMensal> buscarPorProdutoEMes(
            int produtoId, YearMonth mes) {

        return delegate.findByProdutoIdAndMesReferencia(
                produtoId, mes);
    }

    @Override
    public List<CotacaoMensal> buscarHistoricoPorProduto(int produtoId) {
        return delegate.findAllByProdutoIdOrderByMesReferenciaAsc(
                produtoId);
    }

    @Override
    public Map<Integer, BigDecimal> custosMaisRecentesPorProduto() {
        Map<Integer, BigDecimal> custos = new HashMap<>();

        for (JpaCotacaoRepository.UltimoCusto registro
                : delegate.buscarUltimosCustosPorProduto()) {

            custos.put(
                    registro.getProdutoId(),
                    registro.getPrecoUnitario());
        }

        return custos;
    }

    @Override
    public List<CotacaoMensal> listarTodas() {
        return delegate.findAllByOrderByMesReferenciaDescIdDesc();
    }
}
