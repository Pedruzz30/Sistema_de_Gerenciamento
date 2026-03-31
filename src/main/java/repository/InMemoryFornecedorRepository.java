package repository;

import model.Fornecedor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class InMemoryFornecedorRepository implements FornecedorRepository {

    private final List<Fornecedor> fornecedores = new ArrayList<>();
    private final AtomicLong sequenceId = new AtomicLong(1);

    @Override
    public synchronized Fornecedor salvar(Fornecedor fornecedor) {
        if (fornecedor == null) {
            throw new IllegalArgumentException("Fornecedor não pode ser nulo.");
        }

        // Verifica CNPJ duplicado antes de salvar
        Optional<Fornecedor> cnpjExistente = buscarPorCnpj(fornecedor.getCnpj());
        if (cnpjExistente.isPresent() && cnpjExistente.get().getId() != fornecedor.getId()) {
            throw new IllegalArgumentException(
                    "Já existe um fornecedor cadastrado com o CNPJ: " + fornecedor.getCnpj()
            );
        }

        // Se já existe com mesmo ID, substitui (comportamento de update)
        fornecedores.removeIf(f -> f.getId() == fornecedor.getId());
        fornecedores.add(fornecedor);
        return fornecedor;
    }

    @Override
    public synchronized Optional<Fornecedor> buscarPorId(long id) {
        return fornecedores.stream()
                .filter(f -> f.getId() == id)
                .findFirst();
    }

    @Override
    public synchronized Optional<Fornecedor> buscarPorCnpj(String cnpj) {
        if (cnpj == null) return Optional.empty();
        return fornecedores.stream()
                .filter(f -> f.getCnpj().equals(cnpj.trim()))
                .findFirst();
    }

    @Override
    public synchronized List<Fornecedor> listarAtivos() {
        return fornecedores.stream()
                .filter(Fornecedor::isAtivo)
                .collect(Collectors.toList());
    }

    @Override
    public synchronized List<Fornecedor> listarTodos() {
        return new ArrayList<>(fornecedores);
    }
}
