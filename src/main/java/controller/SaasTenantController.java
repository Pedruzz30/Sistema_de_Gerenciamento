package controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import service.SaasTenantAccess;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;

/** Available companies are calculated server-side from a verified identity. */
@RestController
@RequestMapping("/api/saas")
public class SaasTenantController {
    private final SaasTenantAccess tenant;
    public SaasTenantController(SaasTenantAccess tenant) { this.tenant = tenant; }

    @GetMapping("/empresas/{empresaId}/filiais")
    public List<Map<String,Object>> filiais(@PathVariable UUID empresaId) {
        return tenant.filiais(empresaId);
    }

    @GetMapping("/empresas")
    public List<Map<String, Object>> empresas() {
        return tenant.empresas();
    }
}
