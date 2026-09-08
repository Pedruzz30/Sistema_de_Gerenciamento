package repository;

import model.VendaItemConsumo;

import java.util.Collection;
import java.util.List;

public interface VendaItemConsumoRepository {

    VendaItemConsumo salvar(VendaItemConsumo consumo);

    List<VendaItemConsumo> listarPorVenda(long vendaId);

    List<VendaItemConsumo> listarPorVendaItem(long vendaItemId);

    List<VendaItemConsumo> listarPorVendaItens(Collection<Long> vendaItemIds);
}
