package controller;
import org.springframework.web.bind.annotation.*;
import service.SaasEmployeeService;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/saas/empresas/{empresaId}/funcionarios")
public class SaasEmployeeController {
 private final SaasEmployeeService service;
 public SaasEmployeeController(SaasEmployeeService service){this.service=service;}
 @GetMapping
 public List<Map<String,Object>> listar(@PathVariable UUID empresaId){return service.listar(empresaId);}
 @PutMapping("/{authUserId}")
 public Map<String,Object> alterar(@PathVariable UUID empresaId,@PathVariable UUID authUserId,@RequestBody UpdateRequest r){
  return service.alterar(empresaId,authUserId,r.papel(),r.ativo());
 }
 @GetMapping("/auditoria")
 public List<Map<String,Object>> auditoria(@PathVariable UUID empresaId){return service.auditoria(empresaId);}
 public record UpdateRequest(String papel,boolean ativo){}
}