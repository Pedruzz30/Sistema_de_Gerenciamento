package controller;

import org.springframework.web.bind.annotation.*;
import service.SaasReportsService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/saas/empresas/{empresaId}/filiais/{filialId}/relatorios")
public class SaasReportsController {
    private final SaasReportsService service;
    public SaasReportsController(SaasReportsService service){this.service=service;}

    @GetMapping("/financeiro")
    public Map<String,Object> financeiro(@PathVariable UUID empresaId,@PathVariable UUID filialId,
           @RequestParam OffsetDateTime inicio,@RequestParam OffsetDateTime fim) {
        return service.financeiro(empresaId,filialId,inicio,fim);
    }

    @GetMapping("/estoque-baixo")
    public List<Map<String,Object>> estoqueBaixo(@PathVariable UUID empresaId,@PathVariable UUID filialId,
          @RequestParam(defaultValue="5") int quantidadeMaxima,
          @RequestParam(defaultValue="50") int limite) {
        return service.estoqueBaixo(empresaId,filialId,quantidadeMaxima,limite);
    }

    @GetMapping("/produtos-mais-entregues")
    public List<Map<String,Object>> produtosMaisEntregues(@PathVariable UUID empresaId,@PathVariable UUID filialId,
         @RequestParam OffsetDateTime inicio,@RequestParam OffsetDateTime fim,
         @RequestParam(defaultValue="20") int limite) {
        return service.produtosMaisEntregues(empresaId,filialId,inicio,fim,limite);
    }
}
