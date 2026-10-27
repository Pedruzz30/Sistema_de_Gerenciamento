package controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import service.SaasCashService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Branch cash shifts, not a general-purpose billing gateway. */
@RestController
@RequestMapping("/api/saas/empresas/{empresaId}/filiais/{filialId}/caixa")
public class SaasCashController {
    private final SaasCashService service;
    public SaasCashController(SaasCashService service) {this.service=service;}

    @GetMapping("/atual")
    public Map<String,Object> atual(@PathVariable UUID empresaId,@PathVariable UUID filialId) {
        return service.atual(empresaId,filialId);
    }

    @GetMapping("/historico")
    public List<Map<String,Object>> historico(@PathVariable UUID empresaId,@PathVariable UUID filialId) {
        return service.historico(empresaId,filialId);
    }

    @PostMapping("/abrir")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> abrir(@PathVariable UUID empresaId,@PathVariable UUID filialId,
                                     @RequestBody AbrirRequest r) {
        return service.abrir(empresaId,filialId,r.valorAbertura());
    }

    @PostMapping("/movimentos")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> movimentar(@PathVariable UUID empresaId,@PathVariable UUID filialId,
                                          @RequestHeader("Idempotency-Key") String key,
                                          @RequestBody MovimentoRequest r) {
        return service.movimentar(empresaId,filialId,r.tipo(),r.valor(),r.motivo(),key);
    }

    @PostMapping("/fechar")
    public Map<String,Object> fechar(@PathVariable UUID empresaId,@PathVariable UUID filialId,
                                      @RequestBody FecharRequest r) {
        return service.fechar(empresaId,filialId,r.valorContado());
    }

    public record AbrirRequest(BigDecimal valorAbertura) {}
    public record MovimentoRequest(String tipo,BigDecimal valor,String motivo) {}
    public record FecharRequest(BigDecimal valorContado) {}
}
