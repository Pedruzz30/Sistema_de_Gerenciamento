package repository;

import model.NotaFiscal;

import java.util.List;
import java.util.Optional;

public interface NotaFiscalRepository {

    /** Returns the next available ID without consuming it from the sequence. */
    long proximoId();

    NotaFiscal salvar(NotaFiscal nota);

    Optional<NotaFiscal> buscarPorId(long id);

    // Busca todas as notas de um fornecedor especifico
    List<NotaFiscal> buscarPorFornecedor(long fornecedorId);

    List<NotaFiscal> listarTodos();

    void deletar(long id);
}