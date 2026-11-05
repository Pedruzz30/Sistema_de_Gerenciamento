package repository;

import model.VendaPagamento;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Implementação em memória para testes de unidade. */
public class InMemoryVendaPagamentoRepository implements VendaPagamentoRepository {

    private final List<VendaPagamento> pagamentos = new CopyOnWriteArrayList<>();

    @Override
    public VendaPagamento salvar(VendaPagamento pagamento) {
        pagamentos.add(pagamento);
        return pagamento;
    }

    @Override
    public List<VendaPagamento> listarPorVenda(long vendaId) {
        List<VendaPagamento> resultado = new ArrayList<>();
        for (VendaPagamento pagamento : pagamentos) {
            if (pagamento.getVenda().getId() == vendaId) {
                resultado.add(pagamento);
            }
        }
        return resultado;
    }
}
