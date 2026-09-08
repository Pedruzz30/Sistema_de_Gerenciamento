package repository;

import model.FichaTecnicaIngrediente;

import java.util.List;
import java.util.Optional;

public interface FichaTecnicaIngredienteRepository {

    FichaTecnicaIngrediente salvar(FichaTecnicaIngrediente ingrediente);

    Optional<FichaTecnicaIngrediente> buscarPorId(long id);

    List<FichaTecnicaIngrediente> listarPorVersao(long fichaTecnicaVersaoId);

    List<FichaTecnicaIngrediente> listarPorVersoes(List<Long> fichaTecnicaVersaoIds);

    void remover(FichaTecnicaIngrediente ingrediente);
}
