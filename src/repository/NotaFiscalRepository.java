package repository;

import model.NotaFiscal;

import java.util.List;
import java.util.Optional;

public interface notaFiscalRepository {
    NotaFiscal salvar(NotaFiscal nota);

    Optional<NotaFiscal> buscarPorID(long id);

    // Busca todas as notas de um fornecedor especifico
    List<NotaFiscal> buscarPorFornecedor(long fornecedorId);

    List<NotaFiscal> listarTodas();
}