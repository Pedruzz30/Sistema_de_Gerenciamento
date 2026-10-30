package repository;

import model.Pedido;

import java.util.List;
import java.util.Optional;

public interface PedidoRepository {

    Pedido salvar(Pedido pedido);

    Optional<Pedido> buscarPorId(int id);

    List<Pedido> listarTodos();

    /** Mantém o histórico completo disponível, mas evita carregar tudo no dashboard. */
    default List<Pedido> listarRecentes(int limite) {
        if (limite < 1 || limite > 500) throw new IllegalArgumentException("Limite deve estar entre 1 e 500.");
        return listarTodos().stream()
                .sorted(java.util.Comparator.comparing(Pedido::getDataHora).reversed()
                        .thenComparing(java.util.Comparator.comparingInt(Pedido::getId).reversed()))
                .limit(limite).toList();
    }
    default java.util.Map<Integer,Integer> quantidadesVendidasPorProduto() {
        java.util.Map<Integer,Integer> map = new java.util.HashMap<>();
        for (Pedido p : listarTodos())
            if(p!=null && p.ehSaida() && p.getProduto()!=null && p.getDescricao()!=null &&
               p.getDescricao().toLowerCase(java.util.Locale.ROOT).contains("venda"))
                map.merge(p.getProduto().getId(),p.getQuantidade(),Integer::sum);
        return map;
    }
}
