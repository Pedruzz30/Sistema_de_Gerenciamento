package repository;

import model.Pedido;
import model.Produto;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public interface PedidoRepository {

    Pedido salvar(Pedido pedido);

    Optional<Pedido> buscarPorId(int id);

    List<Pedido> listarTodos();

    /**
     * Mantem o historico completo disponivel, limitando
     * a quantidade de registros retornados ao dashboard.
     */
    default List<Pedido> listarRecentes(int limite) {
        if (limite < 1 || limite > 500) {
            throw new IllegalArgumentException(
                    "Limite deve estar entre 1 e 500.");
        }

        Comparator<Pedido> ordenacao = Comparator
                .comparing((Pedido pedido) -> pedido.getDataHora())
                .reversed()
                .thenComparing(
                        Comparator.comparingInt(
                                (Pedido pedido) -> pedido.getId())
                                .reversed());

        return listarTodos().stream()
                .sorted(ordenacao)
                .limit(limite)
                .toList();
    }

    /**
     * Consolida as quantidades vendidas por produto.
     */
    default Map<Integer, Integer> quantidadesVendidasPorProduto() {
        Map<Integer, Integer> quantidades = new HashMap<>();

        for (Pedido pedido : listarTodos()) {
            if (pedido == null || !pedido.ehSaida()) {
                continue;
            }

            Produto produto = pedido.getProduto();
            String descricao = pedido.getDescricao();

            if (produto == null || descricao == null) {
                continue;
            }

            if (!descricao.toLowerCase(Locale.ROOT).contains("venda")) {
                continue;
            }

            quantidades.merge(
                    produto.getId(),
                    pedido.getQuantidade(),
                    (atual, adicional) -> atual + adicional);
        }

        return quantidades;
    }
}
