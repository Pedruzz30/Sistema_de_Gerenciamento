package controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import service.SaasComandaService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** SaaS APIs require a verified JWT and scope every operation by company/branch. */
@RestController
@RequestMapping("/api/saas/empresas/{empresaId}/filiais/{filialId}/comandas")
public class SaasComandaController {
    private final SaasComandaService service;
    public SaasComandaController(SaasComandaService service) {this.service=service;}

    @GetMapping
    public List<Map<String,Object>> listar(@PathVariable UUID empresaId,@PathVariable UUID filialId){
        return service.listar(empresaId,filialId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> abrir(@PathVariable UUID empresaId,@PathVariable UUID filialId,
                                     @RequestBody AbrirRequest request){
        return service.abrir(empresaId,filialId,request.numero());
    }

    @GetMapping("/{comandaId}/itens")
    public List<Map<String,Object>> itens(@PathVariable UUID empresaId,@PathVariable UUID filialId,
                                           @PathVariable UUID comandaId){
        return service.itens(empresaId,filialId,comandaId);
    }

    @PostMapping("/{comandaId}/itens")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> adicionar(@PathVariable UUID empresaId,@PathVariable UUID filialId,
                                         @PathVariable UUID comandaId,@RequestBody ItemRequest request,
                                         @RequestHeader("Idempotency-Key") String key){
        return service.adicionar(empresaId,filialId,comandaId,request.produtoId(),request.quantidade(),key);
    }

    @DeleteMapping("/{comandaId}/itens/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(@PathVariable UUID empresaId,@PathVariable UUID filialId,
                          @PathVariable UUID comandaId,@PathVariable UUID itemId){
        service.cancelarItem(empresaId,filialId,comandaId,itemId);
    }

    @PostMapping("/{comandaId}/pagamentos")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> pagamento(@PathVariable UUID empresaId,@PathVariable UUID filialId,
                                         @PathVariable UUID comandaId,@RequestBody PagamentoRequest request,
                                         @RequestHeader("Idempotency-Key") String key){
        return service.pagamento(empresaId,filialId,comandaId,request.modalidade(),request.valor(),key);
    }

    @PostMapping("/{comandaId}/fechar")
    public Map<String,Object> fechar(@PathVariable UUID empresaId,@PathVariable UUID filialId,
                                       @PathVariable UUID comandaId){
        return service.fechar(empresaId,filialId,comandaId);
    }

    public record AbrirRequest(int numero){}
    public record ItemRequest(UUID produtoId,int quantidade){}
    public record PagamentoRequest(String modalidade,BigDecimal valor){}
}
