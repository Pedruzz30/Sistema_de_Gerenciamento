package repository;

import model.CodigoVenda;

import java.util.List;
import java.util.Optional;

public interface CodigoVendaRepository {
    CodigoVenda salvar(CodigoVenda codigo);
    Optional<CodigoVenda> buscarPorId(long id);
    /** Todos os registros ativos com este código (padrão primeiro). */
    List<CodigoVenda> listarAtivosPorCodigo(String codigo);
    List<CodigoVenda> listarAtivosPorVariacao(long variacaoId);
    List<CodigoVenda> listarAtivos();
}
