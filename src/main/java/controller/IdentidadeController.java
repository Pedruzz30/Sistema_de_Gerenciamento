package controller;

import model.Usuario;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import repository.UsuarioRepository;

/** Self-service identity endpoint. No client-supplied user id can select another user in JWT mode. */
@RestController
public class IdentidadeController {
    private final UsuarioRepository usuarioRepository;

    public IdentidadeController(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/api/auth/me")
    public IdentityResponse me(@RequestHeader(value = "X-User-RU", required = false) String legacyRu) {
        Usuario usuario = ControllerUtils.resolveUser(legacyRu, usuarioRepository);
        return new IdentityResponse(usuario.getRu(), usuario.getNome(), usuario.getSobrenome(),
                usuario.getAuthUserId() == null ? null : usuario.getAuthUserId().toString());
    }

    public record IdentityResponse(long ru, String nome, String sobrenome, String authUserId) {}
}
