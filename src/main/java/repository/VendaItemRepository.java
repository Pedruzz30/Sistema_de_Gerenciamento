package repository;

import model.VendaItem;

import java.util.List;
import java.util.Optional;

public interface VendaItemRepository {

    VendaItem salvar(VendaItem item);

    Optional<VendaItem> buscarPorId(long id);

    List<VendaItem> listarPorVenda(long vendaId);

    List<VendaItem> listarAtivosPorVenda(long vendaId);

    void remover(VendaItem item);
}
