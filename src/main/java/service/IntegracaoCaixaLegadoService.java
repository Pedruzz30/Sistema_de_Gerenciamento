package service;

import model.Usuario;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

public class IntegracaoCaixaLegadoService {

    private final CaixaService caixaService;

    public IntegracaoCaixaLegadoService(CaixaService caixaService) {
        this.caixaService = caixaService;
    }

    @Transactional
    public void registrarVendaConsolidada(Usuario usuario,
                                          Integer numeroCaixa,
                                          long vendaId,
                                          BigDecimal valorTotal) {
        if (numeroCaixa == null) {
            return;
        }
        caixaService.registrarVenda(
                usuario,
                numeroCaixa,
                valorTotal,
                "Venda decimal #" + vendaId,
                List.of()
        );
    }

    @Transactional
    public void registrarCancelamentoConsolidado(Usuario usuario,
                                                 Integer numeroCaixa,
                                                 long vendaId,
                                                 BigDecimal valorTotal) {
        if (numeroCaixa == null) {
            return;
        }
        caixaService.registrarSangria(
                usuario,
                numeroCaixa,
                valorTotal.doubleValue(),
                "Cancelamento venda decimal #" + vendaId
        );
    }
}
