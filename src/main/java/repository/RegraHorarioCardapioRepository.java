package repository;

import model.RegraHorarioCardapio;

import java.util.List;
import java.util.Optional;

public interface RegraHorarioCardapioRepository {

    RegraHorarioCardapio salvar(RegraHorarioCardapio regra);

    Optional<RegraHorarioCardapio> buscarPorId(long id);

    List<RegraHorarioCardapio> listarTodas();

    List<RegraHorarioCardapio> listarAtivasPorFilial(Long filialId);

    void remover(RegraHorarioCardapio regra);
}
