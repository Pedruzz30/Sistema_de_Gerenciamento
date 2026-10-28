package controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import service.SaasPostCloseRefundService;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/saas/empresas/{empresaId}/filiais/{filialId}/comandas")
public class SaasPostCloseRefundController {
    private final SaasPostCloseRefundService refunds;
    public SaasPostCloseRefundController(SaasPostCloseRefundService refunds){this.refunds=refunds;}

    @GetMapping("/fechadas")
    public List<Map<String,Object>> fechadas(@PathVariable UUID empresaId,@PathVariable UUID filialId){
        return refunds.comandasFechadas(empresaId,filialId);
    }

    @GetMapping("/{comandaId}/pagamentos-fechados")
    public List<Map<String,Object>> pagamentos(@PathVariable UUID empresaId,@PathVariable UUID filialId,
                                                @PathVariable UUID comandaId){
        return refunds.pagamentosFechados(empresaId,filialId,comandaId);
    }

    @PostMapping("/{comandaId}/pagamentos/{pagamentoId}/devolucoes")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> registrar(@PathVariable UUID empresaId,@PathVariable UUID filialId,
                 @PathVariable UUID comandaId,@PathVariable UUID pagamentoId,
                 @RequestHeader("Idempotency-Key") String key,@RequestBody Request request){
        return refunds.registrar(empresaId,filialId,comandaId,pagamentoId,
                  request.motivo(),request.comprovanteReferencia(),
                  request.devolucaoConfirmada(),key);
    }

    public record Request(String motivo,String comprovanteReferencia,boolean devolucaoConfirmada){}
}
