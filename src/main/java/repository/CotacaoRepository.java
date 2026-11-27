package repository;

import model.CotacaoMensal;
import model.Produto;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface CotacaoRepository {

    /**
     * Retorna o proximo ID disponivel sem consumir a sequencia.
     */
    long proximoId();

    CotacaoMensal salvar(CotacaoMensal cotacao);

    /**
     * Busca a cotacao de um produto em um mes especifico.
     */
    Optional<CotacaoMensal> buscarPorProdutoEMes(
            int produtoId, YearMonth mes);

    /**
     * Retorna o historico do produto, do mais antigo ao mais recente.
     */
    List<CotacaoMensal> buscarHistoricoPorProduto(int produtoId);

    List<CotacaoMensal> listarTodas();

    /**
     * Retorna o custo mais recente de cada produto.
     * Em caso de empate no mes de referencia, prioriza o maior ID.
     */
    default Map<Integer, BigDecimal> custosMaisRecentesPorProduto() {
        Map<Integer, BigDecimal> custos = new HashMap<>();

        Comparator<CotacaoMensal> ordenacao = Comparator
                .comparing((CotacaoMensal cotacao) ->
                        cotacao.getMesReferencia())
                .reversed()
                .thenComparing(
                        Comparator.comparingLong(
                                (CotacaoMensal cotacao) -> cotacao.getId())
                                .reversed());

        listarTodas().stream()
                .filter(cotacao -> cotacao.getProduto() != null)
                .sorted(ordenacao)
                .forEach(cotacao -> {
                    Produto produto = cotacao.getProduto();

                    if (produto != null) {
                        custos.putIfAbsent(
                                produto.getId(),
                                cotacao.getPrecoUnitario());
                    }
                });

        return custos;
    }
}
