package repository;

import model.FichaTecnicaVersao;

import java.util.List;
import java.util.Optional;

public interface FichaTecnicaVersaoRepository {

    FichaTecnicaVersao salvar(FichaTecnicaVersao versao);

    Optional<FichaTecnicaVersao> buscarPorId(long id);

    List<FichaTecnicaVersao> listarPorFicha(long fichaTecnicaId);

    Optional<FichaTecnicaVersao> buscarRascunhoAtivoPorFicha(long fichaTecnicaId);

    int proximoNumeroVersao(long fichaTecnicaId);
}
