package repository;

import model.VendaPagamento;

import java.util.List;

public interface VendaPagamentoRepository {
    VendaPagamento salvar(VendaPagamento pagamento);
    List<VendaPagamento> listarPorVenda(long vendaId);
}
