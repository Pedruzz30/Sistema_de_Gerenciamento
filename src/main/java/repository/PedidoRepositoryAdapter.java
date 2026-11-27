package repository;

import model.Pedido;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Repository
public class PedidoRepositoryAdapter implements PedidoRepository {

    private static final Sort ORDENACAO_PADRAO =
            Sort.by(Sort.Direction.DESC, "dataHora", "id");

    private final JpaPedidoRepository delegate;

    public PedidoRepositoryAdapter(JpaPedidoRepository delegate) {
        this.delegate = Objects.requireNonNull(
                delegate, "Repositorio JPA de pedido e obrigatorio.");
    }

    @Override
    public Pedido salvar(Pedido pedido) {
        if (pedido == null) {
            throw new IllegalArgumentException(
                    "Pedido e obrigatorio.");
        }

        return delegate.save(pedido);
    }

    @Override
    public Optional<Pedido> buscarPorId(int id) {
        return delegate.findById(id);
    }

    @Override
    public Map<Integer, Integer> quantidadesVendidasPorProduto() {
        Map<Integer, Integer> quantidades = new HashMap<>();

        for (JpaPedidoRepository.QuantidadeVendida registro
                : delegate.somarQuantidadesVendidasPorProduto()) {

            quantidades.put(
                    registro.getProdutoId(),
                    Math.toIntExact(registro.getQuantidadeTotal()));
        }

        return quantidades;
    }

    @Override
    public List<Pedido> listarRecentes(int limite) {
        if (limite < 1 || limite > 500) {
            throw new IllegalArgumentException(
                    "Limite deve estar entre 1 e 500.");
        }

        PageRequest paginacao = PageRequest.of(0, limite);

        return delegate.findAllByOrderByDataHoraDescIdDesc(
                Objects.requireNonNull(paginacao));
    }

    @Override
    public List<Pedido> listarTodos() {
        return delegate.findAll(
                Objects.requireNonNull(ORDENACAO_PADRAO));
    }
}
