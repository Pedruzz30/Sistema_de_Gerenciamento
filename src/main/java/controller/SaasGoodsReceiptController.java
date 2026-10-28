package controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import service.SaasGoodsReceiptService;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pilot goods intake for authenticated managers; does not issue fiscal documents. */
@RestController
@RequestMapping("/api/saas/empresas/{empresaId}")
public class SaasGoodsReceiptController {
    private final SaasGoodsReceiptService service;
    public SaasGoodsReceiptController(SaasGoodsReceiptService service){this.service=service;}

    @GetMapping("/fornecedores")
    public List<Map<String,Object>> fornecedores(@PathVariable UUID empresaId){
        return service.fornecedores(empresaId);
    }
    @PostMapping("/fornecedores")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> criarFornecedor(@PathVariable UUID empresaId,
                                              @RequestBody FornecedorRequest body){
        return service.cadastrarFornecedor(empresaId,body.nome(),body.documento());
    }

    @GetMapping("/filiais/{filialId}/entradas-mercadoria")
    public List<Map<String,Object>> entradas(@PathVariable UUID empresaId,
          @PathVariable UUID filialId,@RequestParam(defaultValue="30") int limite){
        return service.entradas(empresaId,filialId,limite);
    }

    @PostMapping("/filiais/{filialId}/entradas-mercadoria")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> receber(@PathVariable UUID empresaId,@PathVariable UUID filialId,
             @RequestHeader("Idempotency-Key") String chave,@RequestBody EntradaRequest body){
        return service.receber(empresaId,filialId,body.fornecedorId(),
                body.documentoReferencia(),body.itens(),chave);
    }
    public record FornecedorRequest(String nome,String documento){}
    public record EntradaRequest(UUID fornecedorId,String documentoReferencia,
                       List<SaasGoodsReceiptService.ItemInput> itens){}
}
