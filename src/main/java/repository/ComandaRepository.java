package repository;

import model.Comanda;

import java.util.List;
import java.util.Optional;

public interface ComandaRepository {
    Comanda salvar(Comanda comanda);
    Optional<Comanda> buscarAbertaPorNumero(int numero);
    Optional<Comanda> buscarPorVendaId(long vendaId);
    List<Comanda> listarAbertas();
}
