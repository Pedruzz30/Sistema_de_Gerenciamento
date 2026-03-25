package repository;

import model.CotacaoMensal;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class InMemoryCotacaoRepository implements CotacaoRepository {

    private final List<CotacaoMensal> cotacoes = new ArrayList<>();
    private final AtomicLong sequenceId = new AtomicLong(1);

    public long proximoId() {
        return sequenceId.getAndIncrement();
    }

    @Override
    public CotacaoMensal salvar(CotacaoMensal cotacao) {
        if (cotacao == null) {
            throw new IllegalArgumentException("Cotação não pode ser nula.");
        }
        // Se já existe cotação para esse produto+mês, substitui
        cotacoes.removeIf(c ->
                c.getProduto().getId() == cotacao.getProduto().getId()
                        && c.getMesReferencia().equals(cotacao.getMesReferencia())
        );
        cotacoes.add(cotacao);
        return cotacao;
    }

    @Override
    public Optional<CotacaoMensal> buscarPorProdutoEMes(int produtoId, YearMonth mes) {
        return cotacoes.stream()
                .filter(c -> c.getProduto().getId() == produtoId
                        && c.getMesReferencia().equals(mes))
                .findFirst();
    }

    @Override
    public List<CotacaoMensal> buscarHistoricoPorProduto(int produtoId) {
        return cotacoes.stream()
                .filter(c -> c.getProduto().getId() == produtoId)
                .sorted(Comparator.comparing(CotacaoMensal::getMesReferencia))
                .collect(Collectors.toList());
    }

    @Override
    public List<CotacaoMensal> listarTodas() {
        return new ArrayList<>(cotacoes);
    }
}