package repository;

import model.StatusVendaItem;
import model.VendaItemParte;

import java.util.List;

public interface VendaItemParteRepository {

    VendaItemParte salvar(VendaItemParte parte);

    List<VendaItemParte> salvarTodos(List<VendaItemParte> partes);

    List<VendaItemParte> listarPorItem(long vendaItemId);

    List<VendaItemParte> listarPorVenda(long vendaId);

    List<VendaItemParte> listarPorItemEStatus(long vendaItemId, StatusVendaItem status);

    void removerPorItem(long vendaItemId);
}
