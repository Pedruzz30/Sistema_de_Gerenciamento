package repository;

import model.Comanda;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JpaComandaRepository extends JpaRepository<Comanda, Long> {

    Optional<Comanda> findFirstByNumeroAndStatus(int numero, Comanda.Status status);

    Optional<Comanda> findFirstByVendaId(long vendaId);

    List<Comanda> findByStatusOrderByNumeroAsc(Comanda.Status status);
}
