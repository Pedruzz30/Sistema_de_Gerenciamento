package repository;

import model.CotacaoMensal;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public interface CotacaoRepository {

    /** Returns the next available ID without consuming it from the sequence. */
    long proximoId();

    CotacaoMensal salvar(CotacaoMensal cotacao);

    // Busca a cotação de um produto num mês específico
    Optional<CotacaoMensal> buscarPorProdutoEMes(int produtoId, YearMonth mes);

    // Histórico completo de um produto, ordenado do mais antigo ao mais recente
    List<CotacaoMensal> buscarHistoricoPorProduto(int produtoId);

    List<CotacaoMensal> listarTodas();
}
