package controller;

import model.ClasseFuncionario;
import model.Usuario;
import repository.UsuarioRepository;
import app.security.LinkedStockosUserFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import service.AutenticacaoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AutenticacaoService autenticacaoService;
    private final UsuarioRepository usuarioRepository;

    @Value("${stockos.auth.mode:legacy}")
    private String authMode;

    @Value("${stockos.auth.supabase-url:}")
    private String supabaseUrl;

    @Value("${stockos.auth.publishable-key:}")
    private String publishableKey;

    public AuthController(AutenticacaoService autenticacaoService, UsuarioRepository usuarioRepository) {
        this.autenticacaoService = autenticacaoService;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/config")
    public AuthConfig authConfig() {
        if ("supabase".equals(authMode)) {
            if (supabaseUrl.isBlank() || publishableKey.isBlank()) {
                throw new IllegalStateException("Supabase Auth nao configurado.");
            }
            return new AuthConfig("supabase", supabaseUrl, publishableKey);
        }
        return new AuthConfig("legacy", null, null);
    }

    @GetMapping("/me")
    public UsuarioResponse me(HttpServletRequest request) {
        Object verified = request.getAttribute(LinkedStockosUserFilter.VERIFIED_USER_ATTRIBUTE);
        if (!(verified instanceof Usuario usuario)) {
            throw new SecurityException("Sessao Supabase nao vinculada.");
        }
        return UsuarioResponse.from(usuario);
    }

    public record AuthConfig(String mode, String supabaseUrl, String publishableKey) {}

    @PostMapping("/login")
    public ResponseEntity<UsuarioResponse> login(@RequestBody LoginRequest request) {
        return autenticacaoService.autenticar(
                        request.nome(),
                        request.sobrenome(),
                        request.senha()
                )
                .map(u -> ResponseEntity.ok(UsuarioResponse.from(u)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    public record LoginRequest(String nome, String sobrenome, String senha) {}

    public record UsuarioResponse(
            long ru,
            String nome,
            String sobrenome,
            String nomeClasse,
            Set<String> permissoes,
            String perfil,
            boolean ativo
    ) {
        public static UsuarioResponse from(Usuario u) {
            ClasseFuncionario classe = u.getClasse();
            Set<String> permissoes = classe == null
                    ? Set.of()
                    : classe.getPermissoes().stream()
                    .map(Enum::name)
                    .collect(Collectors.toSet());

            return new UsuarioResponse(
                    u.getRu(),
                    u.getNome(),
                    u.getSobrenome(),
                    classe != null ? classe.getNome() : null,
                    permissoes,
                    u.getPerfil() != null ? u.getPerfil().name() : null,
                    u.isAtivo()
            );
        }
    }
}
