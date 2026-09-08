package repository;

import model.VendaItemModificadorImpacto;

import java.util.List;

public interface VendaItemModificadorImpactoRepository {

    VendaItemModificadorImpacto salvar(VendaItemModificadorImpacto impacto);

    List<VendaItemModificadorImpacto> listarPorVenda(long vendaId);
}
