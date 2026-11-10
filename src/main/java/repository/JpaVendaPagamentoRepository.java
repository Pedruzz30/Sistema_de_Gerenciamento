package repository;

import model.VendaPagamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JpaVendaPagamentoRepository extends JpaRepository<VendaPagamento, Long> {
    List<VendaPagamento> findByVendaIdOrderByIdAsc(long vendaId);
}
