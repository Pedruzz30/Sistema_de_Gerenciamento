package repository;

import model.Pedido;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PedidoRepositoryAdapter implements PedidoRepository {

    private final JpaPedidoRepository delegate;

    public PedidoRepositoryAdapter(JpaPedidoRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Pedido salvar(Pedido pedido) {
        return delegate.save(pedido);
    }

    @Override
    public Optional<Pedido> buscarPorId(int id) {
        return delegate.findById(id);
    }

    @Override
    public java.util.Map<Integer,Integer> quantidadesVendidasPorProduto() {
        java.util.Map<Integer,Integer> map = new java.util.HashMap<>();
        for (JpaPedidoRepository.QuantidadeVendida row : delegate.somarQuantidadesVendidasPorProduto())
            map.put(row.getProdutoId(),Math.toIntExact(row.getQuantidadeTotal()));
        return map;
    }
    @Override
    public List<Pedido> listarRecentes(int limite) {
        if (limite < 1 || limite > 500) throw new IllegalArgumentException("Limite deve estar entre 1 e 500.");
        return delegate.findAllByOrderByDataHoraDescIdDesc(org.springframework.data.domain.PageRequest.of(0, limite));
    }

    @Override
    public List<Pedido> listarTodos() {
        return delegate.findAll(
                Sort.by(Sort.Direction.DESC, "dataHora")
                        .and(Sort.by(Sort.Direction.DESC, "id"))
        );
    }
}
