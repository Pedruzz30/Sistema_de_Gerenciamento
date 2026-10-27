package controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import service.SaasProdutoService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** StockOS SaaS company- and branch-scoped product catalog. */
@RestController
@RequestMapping("/api/saas/empresas/{empresaId}/filiais/{filialId}/produtos")
public class SaasProdutoController {
    private final SaasProdutoService service;
    public SaasProdutoController(SaasProdutoService service) {this.service=service;}

    @GetMapping
    public List<Map<String,Object>> listar(@PathVariable UUID empresaId,@PathVariable UUID filialId) {
        return service.listar(empresaId,filialId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> cadastrar(@PathVariable UUID empresaId,@PathVariable UUID filialId,
                                         @RequestBody ProdutoRequest request) {
        return service.cadastrar(empresaId,filialId,request.codigo(),request.nome(),
                request.precoUnitario(),request.estoqueInicial());
    }

    @PutMapping("/{produtoId}")
    public Map<String,Object> atualizar(@PathVariable UUID empresaId,@PathVariable UUID filialId,
                  @PathVariable UUID produtoId,@RequestBody ProdutoUpdateRequest request) {
        return service.atualizar(empresaId,filialId,produtoId,request.codigo(),
                request.nome(),request.precoUnitario(),request.ativo(),request.versaoEsperada());
    }

    @GetMapping("/{produtoId}/historico")
    public List<Map<String,Object>> historico(@PathVariable UUID empresaId,
               @PathVariable UUID filialId,@PathVariable UUID produtoId) {
        return service.historico(empresaId,filialId,produtoId);
    }

    public record ProdutoUpdateRequest(String codigo,String nome,BigDecimal precoUnitario,
            boolean ativo,long versaoEsperada) {}

    public record ProdutoRequest(String codigo,String nome,BigDecimal precoUnitario,int estoqueInicial) {}
}
