package repository;

import model.VendaItemModificador;

import java.util.List;

public interface VendaItemModificadorRepository {

    VendaItemModificador salvar(VendaItemModificador modificador);

    List<VendaItemModificador> salvarTodos(List<VendaItemModificador> modificadores);

    List<VendaItemModificador> listarPorItem(long vendaItemId);

    List<VendaItemModificador> listarPorItens(List<Long> vendaItemIds);

    List<VendaItemModificador> listarPorVenda(long vendaId);

    void removerPorItem(long vendaItemId);
}
