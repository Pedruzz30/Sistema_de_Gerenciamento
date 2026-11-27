package repository;

import model.CotacaoMensal;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class InMemoryCotacaoRepository
        implements CotacaoRepository {

    private static final Comparator<CotacaoMensal> ORDENACAO_HISTORICO =
            Comparator.comparing(cotacao -> cotacao.getMesReferencia());

    private static final Comparator<CotacaoMensal> ORDENACAO_RECENTES =
            ORDENACAO_HISTORICO.reversed()
                    .thenComparing(
                            Comparator.comparingLong(
                                    (CotacaoMensal cotacao) -> cotacao.getId())
                                    .reversed());

    private final List<CotacaoMensal> cotacoes = new ArrayList<>();
    private final AtomicLong sequenceId = new AtomicLong(1);

    @Override
    public long proximoId() {
        return sequenceId.get();
    }

    @Override
    public synchronized CotacaoMensal salvar(CotacaoMensal cotacao) {
        if (cotacao == null) {
            throw new IllegalArgumentException(
                    "Cotação não pode ser nula.");
        }

        CotacaoMensal persistida = cotacao;

        if (cotacao.getId() <= 0) {
            persistida = cotacao.comId(
                    sequenceId.getAndIncrement());
        } else {
            sequenceId.updateAndGet(atual ->
                    Math.max(atual, cotacao.getId() + 1));
        }

        final CotacaoMensal cotacaoPersistida = persistida;

        int produtoId = cotacaoPersistida.getProduto().getId();
        YearMonth mesReferencia = cotacaoPersistida.getMesReferencia();

        // Substitui a cotacao existente para o mesmo produto e mes.
        cotacoes.removeIf(registro ->
                registro.getProduto().getId() == produtoId
                        && registro.getMesReferencia().equals(mesReferencia));

        cotacoes.add(cotacaoPersistida);

        return cotacaoPersistida;
    }

    @Override
    public synchronized Optional<CotacaoMensal> buscarPorProdutoEMes(
            int produtoId, YearMonth mes) {

        return cotacoes.stream()
                .filter(cotacao ->
                        cotacao.getProduto().getId() == produtoId
                                && cotacao.getMesReferencia().equals(mes))
                .findFirst();
    }

    @Override
    public synchronized List<CotacaoMensal> buscarHistoricoPorProduto(
            int produtoId) {

        return cotacoes.stream()
                .filter(cotacao ->
                        cotacao.getProduto().getId() == produtoId)
                .sorted(ORDENACAO_HISTORICO)
                .collect(Collectors.toList());
    }

    @Override
    public synchronized List<CotacaoMensal> listarTodas() {
        return cotacoes.stream()
                .sorted(ORDENACAO_RECENTES)
                .collect(Collectors.toList());
    }
}