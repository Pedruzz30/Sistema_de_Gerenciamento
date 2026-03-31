package controller;

import model.ClasseFuncionario;
import model.Permissao;
import model.Usuario;
import repository.UsuarioRepository;
import service.CadastroFuncionarioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/funcionarios")
public class FuncionarioController {

    private final CadastroFuncionarioService funcionarioService;
    private final UsuarioRepository usuarioRepository;

    public FuncionarioController(CadastroFuncionarioService funcionarioService,
                                 UsuarioRepository usuarioRepository) {
        this.funcionarioService = funcionarioService;
        this.usuarioRepository = usuarioRepository;
    }

    // GET /api/funcionarios
    @GetMapping
    public ResponseEntity<List<FuncionarioResponse>> listar(
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository);
        return ResponseEntity.ok(
                funcionarioService.listarFuncionarios(ator).stream()
                        .map(FuncionarioResponse::from)
                        .toList()
        );
    }

    // POST /api/funcionarios
    @PostMapping
    public ResponseEntity<?> cadastrar(@RequestBody CadastroFuncionarioRequest request,
                                       @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        if (request.nome() == null || request.nome().isBlank()) {
            return ResponseEntity.badRequest().body(new ErroResponse("Nome é obrigatório."));
        }
        if (request.sobrenome() == null || request.sobrenome().isBlank()) {
            return ResponseEntity.badRequest().body(new ErroResponse("Sobrenome é obrigatório."));
        }
        if (request.cpf() == null || request.cpf().isBlank()) {
            return ResponseEntity.badRequest().body(new ErroResponse("CPF é obrigatório."));
        }
        if (request.senha() == null || request.senha().isBlank()) {
            return ResponseEntity.badRequest().body(new ErroResponse("Senha é obrigatória."));
        }
        if (request.nomeClasse() == null || request.nomeClasse().isBlank()) {
            return ResponseEntity.badRequest().body(new ErroResponse("Classe do funcionário é obrigatória."));
        }

        ClasseFuncionario classe;
        try {
            classe = criarClasse(request.nomeClasse());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }

        try {
            Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository);
            Usuario usuario = funcionarioService.cadastrarFuncionario(
                    ator,
                    request.nome(),
                    request.sobrenome(),
                    request.cpf(),
                    request.senha(),
                    classe
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(FuncionarioResponse.from(usuario));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    // PUT /api/funcionarios/{ru}/classe
    @PutMapping("/{ru}/classe")
    public ResponseEntity<?> mudarClasse(@PathVariable long ru,
                                          @RequestBody MudarClasseRequest request,
                                          @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        if (request.nomeClasse() == null || request.nomeClasse().isBlank()) {
            return ResponseEntity.badRequest().body(new ErroResponse("Classe é obrigatória."));
        }

        ClasseFuncionario novaClasse;
        try {
            novaClasse = criarClasse(request.nomeClasse());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }

        try {
            Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository);
            Usuario usuario = funcionarioService.mudarClasse(ator, ru, novaClasse);
            return ResponseEntity.ok(FuncionarioResponse.from(usuario));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    // PUT /api/funcionarios/{ru}/desativar
    @PutMapping("/{ru}/desativar")
    public ResponseEntity<?> desativar(@PathVariable long ru,
                                       @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        try {
            Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository);
            funcionarioService.desativarFuncionario(ator, ru);
            return ResponseEntity.ok(new MensagemResponse("Funcionário desativado com sucesso."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    // GET /api/funcionarios/classes — lists available employee classes
    @GetMapping("/classes")
    public ResponseEntity<List<ClasseResponse>> listarClasses() {
        return ResponseEntity.ok(List.of(
                new ClasseResponse("SUPERIOR",       "Acesso total ao sistema"),
                new ClasseResponse("GERENTE_ESTOQUE","Gerencia estoque e compras"),
                new ClasseResponse("ESTOQUISTA",     "Movimenta itens de estoque"),
                new ClasseResponse("CAIXA",          "Responsável por vendas")
        ));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ClasseFuncionario criarClasse(String nome) {
        return switch (nome.toUpperCase()) {
            case "SUPERIOR" -> {
                ClasseFuncionario c = new ClasseFuncionario(1, "SUPERIOR", "Acesso total ao sistema");
                for (Permissao p : Permissao.values()) c.adicionarPermissao(p);
                yield c;
            }
            case "GERENTE_ESTOQUE" -> {
                ClasseFuncionario c = new ClasseFuncionario(2, "GERENTE_ESTOQUE", "Gerencia estoque e compras");
                c.adicionarPermissao(Permissao.VER_ESTOQUE);
                c.adicionarPermissao(Permissao.EDITAR_ESTOQUE);
                c.adicionarPermissao(Permissao.VER_COMPRAS);
                c.adicionarPermissao(Permissao.VER_FINANCAS);
                c.adicionarPermissao(Permissao.GERENCIAR_FUNCIONARIOS);
                c.adicionarPermissao(Permissao.VER_LOGS);
                yield c;
            }
            case "ESTOQUISTA" -> {
                ClasseFuncionario c = new ClasseFuncionario(3, "ESTOQUISTA", "Movimenta itens de estoque");
                c.adicionarPermissao(Permissao.VER_ESTOQUE);
                c.adicionarPermissao(Permissao.EDITAR_ESTOQUE);
                yield c;
            }
            case "CAIXA" -> {
                ClasseFuncionario c = new ClasseFuncionario(4, "CAIXA", "Responsável por vendas");
                c.adicionarPermissao(Permissao.VER_VENDAS);
                c.adicionarPermissao(Permissao.VER_ESTOQUE);
                yield c;
            }
            default -> throw new IllegalArgumentException(
                    "Classe inválida: '" + nome + "'. Use: SUPERIOR, GERENTE_ESTOQUE, ESTOQUISTA ou CAIXA.");
        };
    }

    // ── Records ───────────────────────────────────────────────────────────────

    public record CadastroFuncionarioRequest(
            String nome, String sobrenome, String cpf, String senha, String nomeClasse
    ) {}

    public record MudarClasseRequest(String nomeClasse) {}

    public record FuncionarioResponse(
            long ru,
            String nome,
            String sobrenome,
            String nomeClasse,
            Set<String> permissoes,
            String perfil,
            boolean ativo
    ) {
        public static FuncionarioResponse from(Usuario u) {
            ClasseFuncionario classe = u.getClasse();
            Set<String> permissoes = classe == null
                    ? Set.of()
                    : classe.getPermissoes().stream().map(Enum::name).collect(Collectors.toSet());
            return new FuncionarioResponse(
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

    public record ClasseResponse(String nome, String descricao) {}

    public record MensagemResponse(String mensagem) {}

    public record ErroResponse(String erro) {}
}
