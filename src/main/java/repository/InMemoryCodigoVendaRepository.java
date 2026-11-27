package repository;

import model.CodigoVenda;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/** Implementacao em memoria para testes de unidade. */
public class InMemoryCodigoVendaRepository
        implements CodigoVendaRepository {

    private static final Comparator<CodigoVenda> ORDENACAO_POR_ID =
            Comparator.comparingLong(codigo -> codigo.getId());

    private static final Comparator<CodigoVenda> ORDENACAO_POR_PADRAO =
            Comparator.comparing(
                    (CodigoVenda codigo) -> codigo.isPadrao())
                    .reversed()
                    .thenComparing(ORDENACAO_POR_ID);

    private static final Comparator<CodigoVenda> ORDENACAO_GERAL =
            Comparator.comparing(
                    (CodigoVenda codigo) -> codigo.getCodigo())
                    .thenComparing(ORDENACAO_POR_PADRAO);

    private final List<CodigoVenda> codigos = new ArrayList<>();
    private final AtomicLong sequence = new AtomicLong(1);

    @Override
    public synchronized CodigoVenda salvar(@NonNull CodigoVenda codigo) {
        Assert.notNull(
                codigo, "Codigo de venda nao pode ser nulo.");

        if (codigo.getId() == 0) {
            codigo.atribuirIdGerado(sequence.getAndIncrement());
        } else {
            sequence.updateAndGet(atual ->
                    Math.max(atual, codigo.getId() + 1));
        }

        codigos.removeIf(registro ->
                registro.getId() == codigo.getId());

        codigos.add(codigo);

        return codigo;
    }

    @Override
    public synchronized Optional<CodigoVenda> buscarPorId(long id) {
        return codigos.stream()
                .filter(codigo -> codigo.getId() == id)
                .findFirst();
    }

    @Override
    public synchronized List<CodigoVenda> listarAtivosPorCodigo(
            String codigo) {

        return codigos.stream()
                .filter(registro -> registro.isAtivo())
                .filter(registro -> registro.getCodigo().equals(codigo))
                .sorted(ORDENACAO_POR_PADRAO)
                .toList();
    }

    @Override
    public synchronized List<CodigoVenda> listarAtivosPorVariacao(
            long variacaoId) {

        return codigos.stream()
                .filter(codigo -> codigo.isAtivo())
                .filter(codigo ->
                        codigo.getVariacao().getId() == variacaoId)
                .sorted(ORDENACAO_POR_ID)
                .toList();
    }

    @Override
    public synchronized List<CodigoVenda> listarAtivos() {
        return codigos.stream()
                .filter(codigo -> codigo.isAtivo())
                .sorted(ORDENACAO_GERAL)
                .toList();
    }
}
