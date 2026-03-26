package repository;

import model.Caixa;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CaixaRepository {

    Caixa salvar(Caixa caixa);

    Optional<Caixa> buscarPorId(long id);

    // Busca o caixa de um número específico que está ABERTO agora
    Optional<Caixa> buscarAbertoporNumero(int numeroCaixa);

    // Histórico de todos os caixas de um número (para consolidação mensal)
    List<Caixa> buscarHistoricoPorNumero(int numeroCaixa);

    // Todos os caixas encerrados em uma data (para consolidação do dia)
    List<Caixa> buscarEncerradosPorData(LocalDate data);

    List<Caixa> listarTodos();
}
