package controller;

import model.ClasseFuncionario;
import model.Permissao;
import model.Usuario;
import repository.InMemoryLogRepository;
import repository.InMemoryUsuarioRepository;
import repository.UsuarioRepository;
import service.AutenticacaoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AutenticacaoService autenticacaoService;

    public AuthController() {
        UsuarioRepository usuarioRepository = new InMemoryUsuarioRepository();
        popularUsuariosPadrao(usuarioRepository);
        this.autenticacaoService = new AutenticacaoService(
                usuarioRepository,
                new InMemoryLogRepository()
        );
    }

    private static void popularUsuariosPadrao(UsuarioRepository repo) {
        ClasseFuncionario classeSuperior = new ClasseFuncionario(1, "SUPERIOR", "Acesso total ao sistema");
        for (Permissao p : Permissao.values()) classeSuperior.adicionarPermissao(p);

        ClasseFuncionario classeGerenteEstoque = new ClasseFuncionario(2, "GERENTE_ESTOQUE", "Gerencia estoque e compras");
        classeGerenteEstoque.adicionarPermissao(Permissao.VER_ESTOQUE);
        classeGerenteEstoque.adicionarPermissao(Permissao.EDITAR_ESTOQUE);
        classeGerenteEstoque.adicionarPermissao(Permissao.VER_COMPRAS);
        classeGerenteEstoque.adicionarPermissao(Permissao.VER_FINANCAS);
        classeGerenteEstoque.adicionarPermissao(Permissao.GERENCIAR_FUNCIONARIOS);
        classeGerenteEstoque.adicionarPermissao(Permissao.VER_LOGS);

        ClasseFuncionario classeEstoquista = new ClasseFuncionario(3, "ESTOQUISTA", "Movimenta itens de estoque");
        classeEstoquista.adicionarPermissao(Permissao.VER_ESTOQUE);
        classeEstoquista.adicionarPermissao(Permissao.EDITAR_ESTOQUE);

        ClasseFuncionario classeCaixa = new ClasseFuncionario(4, "CAIXA", "Responsável por vendas");
        classeCaixa.adicionarPermissao(Permissao.VER_VENDAS);
        classeCaixa.adicionarPermissao(Permissao.VER_ESTOQUE);

        repo.salvar(new Usuario(0, "Admin", "Superior",  "000.000.000-00", "1234", classeSuperior,       Usuario.Perfil.ADMIN));
        repo.salvar(new Usuario(0, "Maria", "Gerente",   "111.111.111-11", "1234", classeGerenteEstoque, Usuario.Perfil.OPERADOR));
        repo.salvar(new Usuario(0, "Joao",  "Silva",     "222.222.222-22", "1234", classeEstoquista,     Usuario.Perfil.OPERADOR));
        repo.salvar(new Usuario(0, "Ana",   "Caixa",     "333.333.333-33", "1234", classeCaixa,          Usuario.Perfil.OPERADOR));
    }

    @PostMapping("/login")
    public ResponseEntity<UsuarioResponse> login(@RequestBody LoginRequest request) {
        Usuario usuario = autenticacaoService.autenticar(
                request.nome(),
                request.sobrenome(),
                request.senha()
        );

        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(UsuarioResponse.from(usuario));
    }

    public record LoginRequest(String nome, String sobrenome, String senha) {}

    public record UsuarioResponse(
            long ru,
            String nome,
            String sobrenome,
            String cpf,
            String perfil,
            boolean ativo
    ) {
        public static UsuarioResponse from(Usuario u) {
            return new UsuarioResponse(
                    u.getRu(),
                    u.getNome(),
                    u.getSobrenome(),
                    u.getCpf(),
                    u.getPerfil() != null ? u.getPerfil().name() : null,
                    u.isAtivo()
            );
        }
    }
}
