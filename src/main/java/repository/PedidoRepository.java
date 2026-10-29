package repository;

import model.Pedido;

import java.util.List;
import java.util.Optional;

public interface PedidoRepository {

    Pedido salvar(Pedido pedido);

    Optional<Pedido> buscarPorId(int id);

    List<Pedido> listarTodos();
    default java.util.Map<Integer,Integer> quantidadesVendidasPorProduto() {
        java.util.Map<Integer,Integer> map = new java.util.HashMap<>();
        for (Pedido p : listarTodos())
            if(p!=null && p.ehSaida() && p.getProduto()!=null && p.getDescricao()!=null &&
               p.getDescricao().toLowerCase(java.util.Locale.ROOT).contains("venda"))
                map.merge(p.getProduto().getId(),p.getQuantidade(),Integer::sum);
        return map;
    }
}
