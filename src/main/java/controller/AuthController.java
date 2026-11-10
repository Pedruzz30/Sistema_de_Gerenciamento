package controller;

import jakarta.servlet.http.HttpServletRequest;
import model.ClasseFuncionario;
import model.SessaoUsuario;
import model.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import service.AutenticacaoService;
import service.SessaoService;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AutenticacaoService autenticacaoService;
    private final SessaoService sessaoService;
    private final boolean cookieSeguro;

    public AuthController(AutenticacaoService autenticacaoService,
                          SessaoService sessaoService,
                          @Value("${stockos.auth.cookie-seguro:false}") boolean cookieSeguro) {
        this.autenticacaoService = autenticacaoService;
        this.sessaoService = sessaoService;
        this.cookieSeguro = cookieSeguro;
    }

    /** A tela de login pergunta o modo antes de mostrar o formulário. */
    @GetMapping("/config")
    public Map<String, String> config() {
        return Map.of("mode", "local");
    }

    @PostMapping("/login")
    public ResponseEntity<UsuarioResponse> login(@RequestBody LoginRequest request) {
        return autenticacaoService.autenticar(
                        request.nome(),
                        request.sobrenome(),
                        request.senha()
                )
                .map(usuario -> ResponseEntity.ok()
                        .header(HttpHeaders.SET_COOKIE,
                                cookie(sessaoService.criar(usuario), sessaoService.getDuracao()).toString())
                        .body(UsuarioResponse.from(usuario, false)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        sessaoService.encerrar(SessaoFilter.tokenDoCookie(http));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                .build();
    }

    @PostMapping("/trocar-senha")
    public ResponseEntity<UsuarioResponse> trocarSenha(@RequestBody TrocarSenhaRequest request, HttpServletRequest http) {
        SessaoUsuario sessao = sessaoAtual(http);
        Usuario usuario = autenticacaoService.trocarSenha(sessao.getUsuario(), request.senhaAtual(), request.novaSenha());
        return ResponseEntity.ok(UsuarioResponse.from(usuario, sessao.isViaPin()));
    }

    @PostMapping("/pin")
    public ResponseEntity<UsuarioResponse> definirPin(@RequestBody DefinirPinRequest request, HttpServletRequest http) {
        SessaoUsuario sessao = sessaoAtual(http);
        Usuario usuario = autenticacaoService.definirPin(sessao.getUsuario(), request.senhaAtual(), request.pin());
        return ResponseEntity.ok(UsuarioResponse.from(usuario, sessao.isViaPin()));
    }

    /** Lista para a troca de operador: só nomes, de quem tem PIN e opera caixa. */
    @GetMapping("/operadores")
    public ResponseEntity<List<OperadorResponse>> operadores(HttpServletRequest http) {
        sessaoAtual(http);
        return ResponseEntity.ok(sessaoService.listarOperadores().stream().map(OperadorResponse::from).toList());
    }

    @PostMapping("/trocar-operador")
    public ResponseEntity<UsuarioResponse> trocarOperador(@RequestBody TrocarOperadorRequest request, HttpServletRequest http) {
        if (request.ru() == null) {
            throw new IllegalArgumentException("Escolha quem vai operar o caixa.");
        }
        SessaoUsuario sessao = sessaoService.trocarOperador(SessaoFilter.tokenDoCookie(http), request.ru(), request.pin());
        return ResponseEntity.ok(UsuarioResponse.from(sessao.getUsuario(), sessao.isViaPin()));
    }

    /** Celular do atendente: os nomes de quem pode entrar por PIN (sem sessão ainda). */
    @GetMapping("/celular/operadores")
    public ResponseEntity<List<OperadorResponse>> operadoresDoCelular() {
        return ResponseEntity.ok(sessaoService.listarOperadores().stream().map(OperadorResponse::from).toList());
    }

    @PostMapping("/celular/entrar")
    public ResponseEntity<UsuarioResponse> entrarPeloCelular(@RequestBody TrocarOperadorRequest request, HttpServletRequest http) {
        if (request == null || request.ru() == null) {
            throw new IllegalArgumentException("Escolha quem vai atender.");
        }
        var entrada = sessaoService.entrarPorPin(request.ru(), request.pin());
        ResponseCookie biscoito = ResponseCookie.from(SessaoFilter.COOKIE_SESSAO, entrada.getKey())
                .httpOnly(true)
                .secure(cookieSeguro || http.isSecure())
                .sameSite("Strict")
                .path("/")
                .maxAge(sessaoService.getDuracao())
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, biscoito.toString())
                .body(UsuarioResponse.from(entrada.getValue().getUsuario(), true));
    }

    @GetMapping("/celular/sessao")
    public ResponseEntity<OperadorResponse> sessaoDoCelular(HttpServletRequest http) {
        return ResponseEntity.ok(OperadorResponse.from(sessaoAtual(http).getUsuario()));
    }

    private SessaoUsuario sessaoAtual(HttpServletRequest http) {
        return sessaoService.resolver(SessaoFilter.tokenDoCookie(http))
                .orElseThrow(() -> new SecurityException("Sessao encerrada. Entre de novo."));
    }

    /** HttpOnly: o JavaScript não lê. SameSite=Strict: outro site não consegue usar a sessão. */
    private ResponseCookie cookie(String valor, Duration duracao) {
        return ResponseCookie.from(SessaoFilter.COOKIE_SESSAO, valor)
                .httpOnly(true)
                .secure(cookieSeguro)
                .sameSite("Strict")
                .path("/")
                .maxAge(duracao)
                .build();
    }

    public record LoginRequest(String nome, String sobrenome, String senha) {}

    public record TrocarSenhaRequest(String senhaAtual, String novaSenha) {}

    public record DefinirPinRequest(String senhaAtual, String pin) {}

    public record TrocarOperadorRequest(Long ru, String pin) {}

    public record OperadorResponse(long ru, String nome) {
        static OperadorResponse from(Usuario u) {
            return new OperadorResponse(u.getRu(), u.getNomeCompleto());
        }
    }

    public record UsuarioResponse(
            long ru,
            String nome,
            String sobrenome,
            String nomeClasse,
            Set<String> permissoes,
            String perfil,
            boolean ativo,
            boolean senhaProvisoria,
            boolean temPin,
            boolean viaPin
    ) {
        public static UsuarioResponse from(Usuario u, boolean viaPin) {
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
                    u.isAtivo(),
                    u.isSenhaProvisoria(),
                    u.getPinHash() != null,
                    viaPin
            );
        }
    }
}
