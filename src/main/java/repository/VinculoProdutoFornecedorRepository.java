package repository;

import model.VinculoProdutoFornecedor;

import java.util.Optional;

public interface VinculoProdutoFornecedorRepository {
    VinculoProdutoFornecedor salvar(VinculoProdutoFornecedor vinculo);
    Optional<VinculoProdutoFornecedor> buscar(long fornecedorId, String codigoProduto);
}
