package controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import service.SaasEmployeeInvitations;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/saas")
public class SaasEmployeeInvitationController {
    private final SaasEmployeeInvitations invitations;
    public SaasEmployeeInvitationController(SaasEmployeeInvitations invitations) {
        this.invitations=invitations;
    }

    @PostMapping("/empresas/{empresaId}/convites")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> create(@PathVariable UUID empresaId,@RequestBody CreateRequest request) {
        return invitations.create(empresaId,request.email(),request.papel());
    }

    @GetMapping("/empresas/{empresaId}/convites")
    public List<Map<String,Object>> list(@PathVariable UUID empresaId) {
        return invitations.list(empresaId);
    }

    @DeleteMapping("/empresas/{empresaId}/convites/{conviteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID empresaId,@PathVariable UUID conviteId) {
        invitations.revoke(empresaId,conviteId);
    }

    @PostMapping("/convites/aceitar")
    public Map<String,Object> accept(@RequestBody AcceptRequest request) {
        return invitations.accept(request.codigo());
    }

    public record CreateRequest(String email,String papel) {}
    public record AcceptRequest(String codigo) {}
}
