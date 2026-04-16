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

    @Override
    public long proximoId() {
        return sequenceId.get();
    }

    @Override
    public synchronized CotacaoMensal salvar(CotacaoMensal cotacao) {
        if (cotacao == null) {
            throw new IllegalArgumentException("Cotação não pode ser nula.");
        }
        CotacaoMensal persistida = cotacao;
        if (cotacao.getId() <= 0) {
            persistida = cotacao.comId(sequenceId.getAndIncrement());
        } else {
            sequenceId.updateAndGet(atual -> Math.max(atual, cotacao.getId() + 1));
        }
        final CotacaoMensal cotacaoPersistida = persistida;
        // Se já existe cotação para esse produto+mês, substitui
        cotacoes.removeIf(c ->
                c.getProduto().getId() == cotacaoPersistida.getProduto().getId()
                        && c.getMesReferencia().equals(cotacaoPersistida.getMesReferencia())
        );
        cotacoes.add(cotacaoPersistida);
        return cotacaoPersistida;
    }

    @Override
    public synchronized Optional<CotacaoMensal> buscarPorProdutoEMes(int produtoId, YearMonth mes) {
        return cotacoes.stream()
                .filter(c -> c.getProduto().getId() == produtoId
                        && c.getMesReferencia().equals(mes))
                .findFirst();
    }

    @Override
    public synchronized List<CotacaoMensal> buscarHistoricoPorProduto(int produtoId) {
        return cotacoes.stream()
                .filter(c -> c.getProduto().getId() == produtoId)
                .sorted(Comparator.comparing(CotacaoMensal::getMesReferencia))
                .collect(Collectors.toList());
    }

    @Override
    public synchronized List<CotacaoMensal> listarTodas() {
        return new ArrayList<>(cotacoes);
    }
}
