package repository;

import model.VendaItemConsumoEstorno;

import java.util.Collection;
import java.util.List;

public interface VendaItemConsumoEstornoRepository {

    VendaItemConsumoEstorno salvar(VendaItemConsumoEstorno estorno);

    List<VendaItemConsumoEstorno> listarPorConsumo(long vendaItemConsumoId);

    List<VendaItemConsumoEstorno> listarPorConsumos(Collection<Long> vendaItemConsumoIds);
}
