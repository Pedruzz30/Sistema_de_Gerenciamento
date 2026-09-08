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

    List<Venda> listar(Long filialId,
                       StatusVenda status,
                       Long usuarioId,
                       LocalDateTime dataInicio,
                       LocalDateTime dataFim);
}
