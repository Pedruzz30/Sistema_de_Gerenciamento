package repository;

import model.Fornecedor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class InMemoryFornecedorRepository
        implements FornecedorRepository {

    private static final Comparator<Fornecedor> ORDENACAO_POR_ID =
            Comparator.comparingLong(fornecedor -> fornecedor.getId());

    private final List<Fornecedor> fornecedores = new ArrayList<>();
    private final AtomicLong sequenceId = new AtomicLong(1);

    @Override
    public synchronized Fornecedor salvar(@NonNull Fornecedor fornecedor) {
        Assert.notNull(fornecedor, "Fornecedor não pode ser nulo.");

        // Verifica CNPJ duplicado antes de salvar.
        Optional<Fornecedor> cnpjExistente =
                buscarPorCnpj(fornecedor.getCnpjBruto());

        if (cnpjExistente.isPresent()
                && cnpjExistente.get().getId() != fornecedor.getId()) {
            throw new IllegalArgumentException(
                    "Já existe um fornecedor cadastrado com o CNPJ: "
                            + fornecedor.getCnpj());
        }

        Fornecedor persistido = fornecedor;

        if (fornecedor.getId() <= 0) {
            persistido = fornecedor.comId(
                    sequenceId.getAndIncrement());
        } else {
            sequenceId.updateAndGet(atual ->
                    Math.max(atual, fornecedor.getId() + 1));
        }

        final long idPersistido = persistido.getId();

        // Substitui o registro existente quando possui o mesmo ID.
        fornecedores.removeIf(registro ->
                registro.getId() == idPersistido);

        fornecedores.add(persistido);

        return persistido;
    }

    @Override
    public synchronized Optional<Fornecedor> buscarPorId(long id) {
        return fornecedores.stream()
                .filter(fornecedor -> fornecedor.getId() == id)
                .findFirst();
    }

    @Override
    public synchronized Optional<Fornecedor> buscarPorCnpj(
            @Nullable String cnpj) {

        if (cnpj == null || cnpj.isBlank()) {
            return Optional.empty();
        }

        String cnpjNormalizado = cnpj.replaceAll("\\D", "");

        return fornecedores.stream()
                .filter(fornecedor -> fornecedor.getCnpjBruto()
                        .equals(cnpjNormalizado))
                .findFirst();
    }

    @Override
    public synchronized List<Fornecedor> listarAtivos() {
        return fornecedores.stream()
                .filter(fornecedor -> fornecedor.isAtivo())
                .sorted(ORDENACAO_POR_ID)
                .collect(Collectors.toList());
    }

    @Override
    public synchronized List<Fornecedor> listarTodos() {
        List<Fornecedor> ordenados = new ArrayList<>(fornecedores);
        ordenados.sort(ORDENACAO_POR_ID);
        return ordenados;
    }
}
