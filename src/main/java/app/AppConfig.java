package app;

import model.*;
import repository.*;
import service.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Central Spring configuration.
 *
 * Before this class existed, every controller created its own repository and
 * service instances, which meant each controller worked with isolated in-memory
 * state. A product created via ProdutoController was invisible to
 * NotaFiscalController, for example.
 *
 * This class registers every repository and service as a singleton Spring bean.
 * All controllers that accept these beans via constructor injection will share
 * the same instances throughout the application lifetime.
 */
@Configuration
public class AppConfig {

    private static final String CPF_ADMIN_SUPERIOR = "52998224725";

    // ── Repositories ──────────────────────────────────────────────────────────

    @Bean
    public Usuario adminSuperior(UsuarioRepository usuarioRepository) {
        popularUsuariosPadrao(usuarioRepository);
        Usuario admin = usuarioRepository.listarTodos().stream()
                .filter(usuario -> CPF_ADMIN_SUPERIOR.equals(usuario.getCpfBruto()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Usuário admin padrão não encontrado no seed."));

        if (!admin.isAtivo()) {
            admin.reativar();
            admin = usuarioRepository.salvar(admin);
        }

        return admin;
    }

    // ── Services ──────────────────────────────────────────────────────────────

    @Bean
    public EstoqueService estoqueService(ProdutoRepository produtoRepository,
                                         PedidoRepository pedidoRepository) {
        return new EstoqueService(produtoRepository, pedidoRepository);
    }

    @Bean
    public AutenticacaoService autenticacaoService(UsuarioRepository usuarioRepository,
                                                   LogRepository logRepository) {
        return new AutenticacaoService(usuarioRepository, logRepository);
    }

    @Bean
    public CaixaService caixaService(CaixaRepository caixaRepository,
                                     LogRepository logRepository,
                                     EstoqueService estoqueService,
                                     PizzaMenuCatalogService pizzaMenuCatalogService) {
        return new CaixaService(caixaRepository, logRepository, estoqueService, pizzaMenuCatalogService);
    }

    @Bean
    public PizzaMenuCatalogService pizzaMenuCatalogService() {
        return new PizzaMenuCatalogService();
    }

    @Bean
    public FornecedorService fornecedorService(FornecedorRepository fornecedorRepository,
                                               LogRepository logRepository) {
        return new FornecedorService(fornecedorRepository, logRepository);
    }

    @Bean
    public NotaFiscalService notaFiscalService(NotaFiscalRepository notaFiscalRepository,
                                               LogRepository logRepository,
                                               EstoqueService estoqueService,
                                               FornecedorService fornecedorService) {
        return new NotaFiscalService(notaFiscalRepository, logRepository, estoqueService, fornecedorService);
    }

    @Bean
    public CotacaoService cotacaoService(CotacaoRepository cotacaoRepository,
                                         LogRepository logRepository) {
        return new CotacaoService(cotacaoRepository, logRepository);
    }

    @Bean
    public CadastroFuncionarioService cadastroFuncionarioService(UsuarioRepository usuarioRepository,
                                                                 LogRepository logRepository) {
        return new CadastroFuncionarioService(usuarioRepository, logRepository);
    }

    // ── Shared privileged user ────────────────────────────────────────────────
    // ── Default seed users ────────────────────────────────────────────────────

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

        garantirUsuarioPadrao(
                repo,
                new Usuario(0, "Admin", "Superior", "529.982.247-25", "1234", classeSuperior, Usuario.Perfil.ADMIN)
        );
        garantirUsuarioPadrao(
                repo,
                new Usuario(0, "Maria", "Gerente", "111.444.777-35", "1234", classeGerenteEstoque, Usuario.Perfil.OPERADOR)
        );
        garantirUsuarioPadrao(
                repo,
                new Usuario(0, "Joao", "Silva", "123.456.789-09", "1234", classeEstoquista, Usuario.Perfil.OPERADOR)
        );
        garantirUsuarioPadrao(
                repo,
                new Usuario(0, "Ana", "Caixa", "935.411.347-80", "1234", classeCaixa, Usuario.Perfil.OPERADOR)
        );
    }

    private static void garantirUsuarioPadrao(UsuarioRepository repo, Usuario usuarioPadrao) {
        boolean jaExiste = repo.listarTodos().stream()
                .anyMatch(usuario -> usuario.getCpfBruto().equals(usuarioPadrao.getCpfBruto()));
        if (!jaExiste) {
            repo.salvar(usuarioPadrao);
        }
    }
}
