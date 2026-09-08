package repository;

import model.PausaCardapio;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PausaCardapioRepository {

    PausaCardapio salvar(PausaCardapio pausa);

    Optional<PausaCardapio> buscarPorId(long id);

    List<PausaCardapio> listarTodas();

    List<PausaCardapio> listarAtivas(Long filialId, LocalDateTime agora);
}
