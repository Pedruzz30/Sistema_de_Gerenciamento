package repository;

import model.StatusVenda;
import model.Venda;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VendaRepository {

    Venda salvar(Venda venda);

    Optional<Venda> buscarPorId(long id);

    Optional<Venda> buscarPorIdComLock(long id);

    /** Vendas finalizadas no período (inclusive as canceladas depois), pela hora em que foram pagas. */
    default List<Venda> listarFinalizadasEntre(java.time.LocalDateTime inicio, java.time.LocalDateTime fim) {
        return listar(null, null, null, null, null).stream()
                .filter(v -> v.getFinalizadaEm() != null && !v.getFinalizadaEm().isBefore(inicio) && v.getFinalizadaEm().isBefore(fim))
                .toList();
    }

    List<Venda> listar(Long filialId,
                       StatusVenda status,
                       Long usuarioId,
                       LocalDateTime dataInicio,
                       LocalDateTime dataFim);
}
