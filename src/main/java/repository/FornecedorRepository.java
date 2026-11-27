package repository;

import model.Fornecedor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Contrato do repositório de fornecedores.
 *
 * Seguindo o mesmo padrão do UsuarioRepository — definimos a interface
 * aqui, e a implementação concreta fica em InMemoryFornecedorRepository.
 * No futuro, você pode criar um JdbcFornecedorRepository sem mudar nada
 * nos services.
 */

public interface FornecedorRepository {
    Fornecedor salvar(@NonNull Fornecedor fornecedor);

    Optional<Fornecedor> buscarPorId(long id);
    Optional<Fornecedor> buscarPorCnpj(@Nullable String cnpj);

    List<Fornecedor> listarAtivos();
    List<Fornecedor> listarTodos();
}