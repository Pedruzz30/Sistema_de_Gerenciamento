package repository;

import model.NotaFiscal;

import java.util.List;
import java.util.Optional;

public interface NotaFiscalRepository {
    NotaFiscal salvar(NotaFiscal nota);

    Optional<NotaFiscal> buscarPorId(long id);

    // Busca todas as notas de um fornecedor especifico
    List<NotaFiscal> buscarPorFornecedor(long fornecedorId);

    List<NotaFiscal> listarTodos();
}