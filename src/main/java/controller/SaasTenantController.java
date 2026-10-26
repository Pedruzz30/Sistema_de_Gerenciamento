package controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import service.SaasTenantAccess;

import java.util.List;
import java.util.Map;

/** Available companies are calculated server-side from a verified identity. */
@RestController
@RequestMapping("/api/saas")
public class SaasTenantController {
    private final SaasTenantAccess tenant;
    public SaasTenantController(SaasTenantAccess tenant) { this.tenant = tenant; }

    @GetMapping("/empresas")
    public List<Map<String, Object>> empresas() {
        return tenant.empresas();
    }
}
