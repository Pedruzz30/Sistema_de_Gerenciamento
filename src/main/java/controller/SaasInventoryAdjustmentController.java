package controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import service.SaasInventoryAdjustmentService;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Only verified managers may reconcile actual stock against recorded stock. */
@RestController
@RequestMapping("/api/saas/empresas/{empresaId}/filiais/{filialId}/produtos")
public class SaasInventoryAdjustmentController {
    private final SaasInventoryAdjustmentService inventory;
    public SaasInventoryAdjustmentController(SaasInventoryAdjustmentService inventory){
        this.inventory=inventory;
    }

    @GetMapping("/ajustes")
    public List<Map<String,Object>> historico(@PathVariable UUID empresaId,
            @PathVariable UUID filialId,@RequestParam(defaultValue="30") int limite){
        return inventory.historico(empresaId,filialId,limite);
    }

    @PostMapping("/{produtoId}/ajustes")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> conferir(@PathVariable UUID empresaId,
          @PathVariable UUID filialId,@PathVariable UUID produtoId,
          @RequestHeader("Idempotency-Key") String chave,@RequestBody Request body){
        return inventory.conferir(empresaId,filialId,produtoId,
                body.estoqueEsperado(),body.estoqueContado(),body.motivo(),chave);
    }

    public record Request(int estoqueEsperado,int estoqueContado,String motivo){}
}
