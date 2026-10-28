package controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import service.SaasStockReturnService;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Receipt of physical items is strictly separate from financial refunds. */
@RestController
@RequestMapping("/api/saas/empresas/{empresaId}/filiais/{filialId}/comandas")
public class SaasStockReturnController {
    private final SaasStockReturnService stock;
    public SaasStockReturnController(SaasStockReturnService stock){this.stock=stock;}

    @GetMapping("/{comandaId}/itens-fechados")
    public List<Map<String,Object>> itens(@PathVariable UUID empresaId,@PathVariable UUID filialId,
                                           @PathVariable UUID comandaId){
        return stock.itensFechados(empresaId,filialId,comandaId);
    }

    @GetMapping("/{comandaId}/devolucoes-estoque")
    public List<Map<String,Object>> historico(@PathVariable UUID empresaId,@PathVariable UUID filialId,
                                              @PathVariable UUID comandaId){
        return stock.historico(empresaId,filialId,comandaId);
    }

    @PostMapping("/{comandaId}/itens/{itemId}/devolucoes-estoque")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> registrar(@PathVariable UUID empresaId,@PathVariable UUID filialId,
                                       @PathVariable UUID comandaId,@PathVariable UUID itemId,
                                       @RequestHeader("Idempotency-Key") String chave,
                                       @RequestBody ReintegracaoRequest r){
        return stock.registrar(empresaId,filialId,comandaId,itemId,
                r.quantidade(),r.destino(),r.motivo(),chave);
    }

    public record ReintegracaoRequest(int quantidade,String destino,String motivo){}
}
