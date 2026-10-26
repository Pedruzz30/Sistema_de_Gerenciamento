package controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import service.SaasAdminService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/stockos")
public class SaasAdminController {
    private final SaasAdminService admin;
    public SaasAdminController(SaasAdminService admin){this.admin=admin;}

    @GetMapping("/planos")
    public List<Map<String,Object>> planos(){return admin.planos();}
    @PostMapping("/planos")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> plano(@RequestBody PlanoRequest r){
        return admin.cadastrarPlano(r.codigo(),r.nome(),r.precoMensal(),
                                  r.maxFiliais(),r.maxUsuarios(),r.incluiComandas());
    }
    @GetMapping("/empresas")
    public List<Map<String,Object>> empresas(){return admin.empresas();}
    @PostMapping("/empresas")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> empresa(@RequestBody EmpresaRequest r){
        return admin.cadastrarEmpresa(r.nome(),r.filialNome(),r.proprietarioAuthUserId());
    }
    @PutMapping("/empresas/{empresaId}/licenca")
    public Map<String,Object> licenca(@PathVariable UUID empresaId,@RequestBody LicencaRequest r){
        return admin.licenciar(empresaId,r.planoId(),r.status(),
                              r.vigenciaInicio(),r.vigenciaFim());
    }
    @GetMapping("/auditoria")
    public List<Map<String,Object>> auditoria(@RequestParam(defaultValue="50") int limite){
        return admin.auditoria(limite);
    }
    public record PlanoRequest(String codigo,String nome,BigDecimal precoMensal,
                              int maxFiliais,int maxUsuarios,boolean incluiComandas){}
    public record EmpresaRequest(String nome,String filialNome,UUID proprietarioAuthUserId){}
    public record LicencaRequest(UUID planoId,String status,
                                OffsetDateTime vigenciaInicio,OffsetDateTime vigenciaFim){}
}
