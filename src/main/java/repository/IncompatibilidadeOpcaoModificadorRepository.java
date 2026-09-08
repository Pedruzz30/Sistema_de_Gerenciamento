package repository;

import model.IncompatibilidadeOpcaoModificador;

import java.util.List;

public interface IncompatibilidadeOpcaoModificadorRepository {

    IncompatibilidadeOpcaoModificador salvar(IncompatibilidadeOpcaoModificador incompatibilidade);

    List<IncompatibilidadeOpcaoModificador> listarAtivasPorOpcoes(List<Long> opcaoIds);
}
