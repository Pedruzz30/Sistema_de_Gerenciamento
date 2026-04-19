package controller;

import model.ClasseFuncionario;
import model.Usuario;
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

    public AuthController(AutenticacaoService autenticacaoService) {
        this.autenticacaoService = autenticacaoService;
    }

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
