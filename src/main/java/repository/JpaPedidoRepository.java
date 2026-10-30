package repository;

import model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;

public interface JpaPedidoRepository extends JpaRepository<Pedido, Integer> {
    interface QuantidadeVendida {
        Integer getProdutoId();
        Long getQuantidadeTotal();
    }
    @Query("select p.produto.id as produtoId, sum(p.quantidade) as quantidadeTotal " +
           "from Pedido p where p.tipo = model.TipoMovimentacao.SAIDA " +
           "and lower(p.descricao) like '%venda%' group by p.produto.id")
    List<QuantidadeVendida> somarQuantidadesVendidasPorProduto();

    @EntityGraph(attributePaths = "produto")
    List<Pedido> findAllByOrderByDataHoraDescIdDesc(Pageable pageable);
}
