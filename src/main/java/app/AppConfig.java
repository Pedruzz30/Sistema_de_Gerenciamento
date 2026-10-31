package app;

import model.*;
import repository.*;
import service.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.Clock;
import java.time.ZoneId;

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
    @ConditionalOnProperty(name = "stockos.auth.mode", havingValue = "legacy", matchIfMissing = true)
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
    public Clock appClock(@Value("${stockos.timezone:America/Sao_Paulo}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }

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
                                     FechamentoCaixaRepository fechamentoCaixaRepository,
                                     LogRepository logRepository,
                                     EstoqueService estoqueService,
                                     CardapioService cardapioService) {
        return new CaixaService(caixaRepository, fechamentoCaixaRepository, logRepository, estoqueService, cardapioService);
    }

    @Bean
    public CardapioService cardapioService(CategoriaCardapioRepository categoriaCardapioRepository,
                                           ItemCardapioRepository itemCardapioRepository,
                                           ProdutoRepository produtoRepository) {
        return new CardapioService(categoriaCardapioRepository, itemCardapioRepository, produtoRepository);
    }

    @Bean
    public VariacaoItemCardapioService variacaoItemCardapioService(ItemCardapioRepository itemCardapioRepository,
                                                                   ProdutoRepository produtoRepository,
                                                                   VariacaoItemCardapioRepository variacaoItemCardapioRepository,
                                                                   LogRepository logRepository) {
        return new VariacaoItemCardapioService(
                itemCardapioRepository,
                produtoRepository,
                variacaoItemCardapioRepository,
                logRepository
        );
    }

    @Bean
    public CalculadoraCustoFichaTecnicaService calculadoraCustoFichaTecnicaService(
            FichaTecnicaIngredienteRepository fichaTecnicaIngredienteRepository,
            EstoqueProdutoRepository estoqueProdutoRepository) {
        return new CalculadoraCustoFichaTecnicaService(
                fichaTecnicaIngredienteRepository,
                estoqueProdutoRepository
        );
    }

    @Bean
    public FichaTecnicaIngredienteService fichaTecnicaIngredienteService(
            FichaTecnicaVersaoRepository fichaTecnicaVersaoRepository,
            FichaTecnicaIngredienteRepository fichaTecnicaIngredienteRepository,
            ProdutoRepository produtoRepository,
            UnidadeMedidaService unidadeMedidaService,
            LogRepository logRepository) {
        return new FichaTecnicaIngredienteService(
                fichaTecnicaVersaoRepository,
                fichaTecnicaIngredienteRepository,
                produtoRepository,
                unidadeMedidaService,
                logRepository
        );
    }

    @Bean
    public FichaTecnicaService fichaTecnicaService(VariacaoItemCardapioService variacaoItemCardapioService,
                                                   FichaTecnicaRepository fichaTecnicaRepository,
                                                   FichaTecnicaVersaoRepository fichaTecnicaVersaoRepository,
                                                   FichaTecnicaIngredienteRepository fichaTecnicaIngredienteRepository,
                                                   UnidadeMedidaService unidadeMedidaService,
                                                   LogRepository logRepository) {
        return new FichaTecnicaService(
                variacaoItemCardapioService,
                fichaTecnicaRepository,
                fichaTecnicaVersaoRepository,
                fichaTecnicaIngredienteRepository,
                unidadeMedidaService,
                logRepository
        );
    }

    @Bean
    public PublicacaoFichaTecnicaService publicacaoFichaTecnicaService(
            FichaTecnicaRepository fichaTecnicaRepository,
            FichaTecnicaVersaoRepository fichaTecnicaVersaoRepository,
            FichaTecnicaIngredienteRepository fichaTecnicaIngredienteRepository,
            FichaTecnicaCustoSnapshotRepository fichaTecnicaCustoSnapshotRepository,
            FichaTecnicaIngredienteCustoSnapshotRepository fichaTecnicaIngredienteCustoSnapshotRepository,
            CalculadoraCustoFichaTecnicaService calculadoraCustoFichaTecnicaService,
            LogRepository logRepository) {
        return new PublicacaoFichaTecnicaService(
                fichaTecnicaRepository,
                fichaTecnicaVersaoRepository,
                fichaTecnicaIngredienteRepository,
                fichaTecnicaCustoSnapshotRepository,
                fichaTecnicaIngredienteCustoSnapshotRepository,
                calculadoraCustoFichaTecnicaService,
                logRepository
        );
    }

    @Bean
    public SimulacaoPrecoFichaTecnicaService simulacaoPrecoFichaTecnicaService(
            CalculadoraCustoFichaTecnicaService calculadoraCustoFichaTecnicaService) {
        return new SimulacaoPrecoFichaTecnicaService(calculadoraCustoFichaTecnicaService);
    }

    @Bean
    public CalculadoraCapacidadeService calculadoraCapacidadeService() {
        return new CalculadoraCapacidadeService();
    }

    @Bean
    public CalculadoraDisponibilidadeService calculadoraDisponibilidadeService(
            VariacaoItemCardapioRepository variacaoItemCardapioRepository,
            EstoqueProdutoRepository estoqueProdutoRepository,
            PausaCardapioRepository pausaCardapioRepository,
            RegraHorarioCardapioRepository regraHorarioCardapioRepository,
            VariacaoCanalDisponibilidadeRepository variacaoCanalDisponibilidadeRepository,
            ResolucaoComposicaoVariacaoService resolucaoComposicaoVariacaoService,
            CalculadoraCapacidadeService calculadoraCapacidadeService,
            CalculadoraDisponibilidadeConfiguracaoService calculadoraDisponibilidadeConfiguracaoService,
            Clock appClock,
            @Value("${stockos.cardapio.limite-alerta-capacidade-padrao:3}") int limiteAlertaPadrao) {
        return new CalculadoraDisponibilidadeService(
                variacaoItemCardapioRepository,
                estoqueProdutoRepository,
                pausaCardapioRepository,
                regraHorarioCardapioRepository,
                variacaoCanalDisponibilidadeRepository,
                resolucaoComposicaoVariacaoService,
                calculadoraCapacidadeService,
                calculadoraDisponibilidadeConfiguracaoService,
                appClock,
                limiteAlertaPadrao
        );
    }

    @Bean
    public PausaCardapioService pausaCardapioService(PausaCardapioRepository pausaCardapioRepository,
                                                     ItemCardapioRepository itemCardapioRepository,
                                                     VariacaoItemCardapioRepository variacaoItemCardapioRepository,
                                                     LogRepository logRepository,
                                                     Clock appClock) {
        return new PausaCardapioService(
                pausaCardapioRepository,
                itemCardapioRepository,
                variacaoItemCardapioRepository,
                logRepository,
                appClock
        );
    }

    @Bean
    public RegraHorarioCardapioService regraHorarioCardapioService(
            RegraHorarioCardapioRepository regraHorarioCardapioRepository,
            ItemCardapioRepository itemCardapioRepository,
            VariacaoItemCardapioRepository variacaoItemCardapioRepository,
            LogRepository logRepository) {
        return new RegraHorarioCardapioService(
                regraHorarioCardapioRepository,
                itemCardapioRepository,
                variacaoItemCardapioRepository,
                logRepository
        );
    }

    @Bean
    public DisponibilidadeCanalService disponibilidadeCanalService(
            VariacaoCanalDisponibilidadeRepository variacaoCanalDisponibilidadeRepository,
            VariacaoItemCardapioRepository variacaoItemCardapioRepository,
            LogRepository logRepository) {
        return new DisponibilidadeCanalService(
                variacaoCanalDisponibilidadeRepository,
                variacaoItemCardapioRepository,
                logRepository
        );
    }

    @Bean
    public GrupoModificadorService grupoModificadorService(GrupoModificadorRepository grupoModificadorRepository,
                                                           LogRepository logRepository) {
        return new GrupoModificadorService(grupoModificadorRepository, logRepository);
    }

    @Bean
    public OpcaoModificadorService opcaoModificadorService(
            GrupoModificadorRepository grupoModificadorRepository,
            OpcaoModificadorRepository opcaoModificadorRepository,
            OpcaoModificadorComponenteRepository opcaoModificadorComponenteRepository,
            ProdutoRepository produtoRepository,
            UnidadeMedidaRepository unidadeMedidaRepository,
            LogRepository logRepository) {
        return new OpcaoModificadorService(
                grupoModificadorRepository,
                opcaoModificadorRepository,
                opcaoModificadorComponenteRepository,
                produtoRepository,
                unidadeMedidaRepository,
                logRepository
        );
    }

    @Bean
    public AssociacaoVariacaoModificadorService associacaoVariacaoModificadorService(
            VariacaoItemCardapioRepository variacaoItemCardapioRepository,
            GrupoModificadorRepository grupoModificadorRepository,
            VariacaoGrupoModificadorRepository variacaoGrupoModificadorRepository,
            LogRepository logRepository) {
        return new AssociacaoVariacaoModificadorService(
                variacaoItemCardapioRepository,
                grupoModificadorRepository,
                variacaoGrupoModificadorRepository,
                logRepository
        );
    }

    @Bean
    public CalculadoraDisponibilidadeConfiguracaoService calculadoraDisponibilidadeConfiguracaoService(
            VariacaoItemCardapioRepository variacaoItemCardapioRepository,
            EstoqueProdutoRepository estoqueProdutoRepository,
            OpcaoModificadorComponenteRepository opcaoModificadorComponenteRepository,
            MontagemConfiguracaoItemService montagemConfiguracaoItemService,
            CalculadoraCapacidadeService calculadoraCapacidadeService,
            Clock appClock) {
        return new CalculadoraDisponibilidadeConfiguracaoService(
                variacaoItemCardapioRepository,
                estoqueProdutoRepository,
                opcaoModificadorComponenteRepository,
                montagemConfiguracaoItemService,
                calculadoraCapacidadeService,
                appClock
        );
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
    public RentabilidadeService rentabilidadeService(EstoqueService estoqueService,
                                                     CotacaoRepository cotacaoRepository) {
        return new RentabilidadeService(estoqueService, cotacaoRepository);
    }

    @Bean
    public UnidadeMedidaService unidadeMedidaService(UnidadeMedidaRepository unidadeMedidaRepository) {
        return new UnidadeMedidaService(unidadeMedidaRepository);
    }

    @Bean
    public ProdutoEstoqueService produtoEstoqueService(ProdutoRepository produtoRepository,
                                                       EmbalagemProdutoRepository embalagemProdutoRepository,
                                                       EstoqueProdutoRepository estoqueProdutoRepository,
                                                       UnidadeMedidaService unidadeMedidaService) {
        return new ProdutoEstoqueService(
                produtoRepository,
                embalagemProdutoRepository,
                estoqueProdutoRepository,
                unidadeMedidaService
        );
    }

    @Bean
    public CompraEstoqueService compraEstoqueService(CompraEstoqueRepository compraEstoqueRepository,
                                                     ProdutoRepository produtoRepository,
                                                     FornecedorRepository fornecedorRepository,
                                                     EmbalagemProdutoRepository embalagemProdutoRepository,
                                                     EstoqueProdutoRepository estoqueProdutoRepository,
                                                     MovimentoEstoqueRepository movimentoEstoqueRepository,
                                                     RegistroIdempotenciaRepository registroIdempotenciaRepository,
                                                     UnidadeMedidaService unidadeMedidaService) {
        return new CompraEstoqueService(
                compraEstoqueRepository,
                produtoRepository,
                fornecedorRepository,
                embalagemProdutoRepository,
                estoqueProdutoRepository,
                movimentoEstoqueRepository,
                registroIdempotenciaRepository,
                unidadeMedidaService
        );
    }

    @Bean
    public MovimentoEstoqueService movimentoEstoqueService(MovimentoEstoqueRepository movimentoEstoqueRepository,
                                                           EstoqueProdutoRepository estoqueProdutoRepository) {
        return new MovimentoEstoqueService(movimentoEstoqueRepository, estoqueProdutoRepository);
    }

    @Bean
    public ResolucaoComposicaoVariacaoService resolucaoComposicaoVariacaoService(
            FichaTecnicaRepository fichaTecnicaRepository,
            FichaTecnicaIngredienteRepository fichaTecnicaIngredienteRepository) {
        return new ResolucaoComposicaoVariacaoService(fichaTecnicaRepository, fichaTecnicaIngredienteRepository);
    }

    @Bean
    public MontagemConfiguracaoItemService montagemConfiguracaoItemService(
            VariacaoGrupoModificadorRepository variacaoGrupoModificadorRepository,
            OpcaoModificadorRepository opcaoModificadorRepository,
            OpcaoModificadorComponenteRepository opcaoModificadorComponenteRepository,
            IncompatibilidadeOpcaoModificadorRepository incompatibilidadeOpcaoModificadorRepository,
            ResolucaoComposicaoVariacaoService resolucaoComposicaoVariacaoService) {
        return new MontagemConfiguracaoItemService(
                variacaoGrupoModificadorRepository,
                opcaoModificadorRepository,
                opcaoModificadorComponenteRepository,
                incompatibilidadeOpcaoModificadorRepository,
                resolucaoComposicaoVariacaoService
        );
    }

    @Bean
    public ComposicaoMultiplaService composicaoMultiplaService(
            ConfiguracaoComposicaoMultiplaRepository configuracaoComposicaoMultiplaRepository,
            ConfiguracaoComposicaoMultiplaParteRepository configuracaoComposicaoMultiplaParteRepository,
            VariacaoItemCardapioRepository variacaoItemCardapioRepository,
            ResolucaoComposicaoVariacaoService resolucaoComposicaoVariacaoService) {
        return new ComposicaoMultiplaService(
                configuracaoComposicaoMultiplaRepository,
                configuracaoComposicaoMultiplaParteRepository,
                variacaoItemCardapioRepository,
                resolucaoComposicaoVariacaoService
        );
    }

    @Bean
    public MontagemComposicaoVendaService montagemComposicaoVendaService(
            ResolucaoComposicaoVariacaoService resolucaoComposicaoVariacaoService,
            MontagemConfiguracaoItemService montagemConfiguracaoItemService,
            ComposicaoMultiplaService composicaoMultiplaService,
            VendaItemModificadorRepository vendaItemModificadorRepository,
            VendaItemParteRepository vendaItemParteRepository) {
        return new MontagemComposicaoVendaService(
                resolucaoComposicaoVariacaoService,
                montagemConfiguracaoItemService,
                composicaoMultiplaService,
                vendaItemModificadorRepository,
                vendaItemParteRepository
        );
    }

    @Bean
    public CalculadoraCustoVendaService calculadoraCustoVendaService() {
        return new CalculadoraCustoVendaService();
    }

    @Bean
    public VendaItemService vendaItemService(VendaRepository vendaRepository,
                                              VendaItemRepository vendaItemRepository,
                                              VariacaoItemCardapioRepository variacaoItemCardapioRepository,
                                              FichaTecnicaRepository fichaTecnicaRepository,
                                              MontagemConfiguracaoItemService montagemConfiguracaoItemService,
                                              ComposicaoMultiplaService composicaoMultiplaService,
                                              VendaItemModificadorRepository vendaItemModificadorRepository,
                                              VendaItemParteRepository vendaItemParteRepository) {
        return new VendaItemService(
                vendaRepository,
                vendaItemRepository,
                variacaoItemCardapioRepository,
                fichaTecnicaRepository,
                montagemConfiguracaoItemService,
                composicaoMultiplaService,
                vendaItemModificadorRepository,
                vendaItemParteRepository
        );
    }

    @Bean
    public VendaService vendaService(VendaRepository vendaRepository,
                                     VendaItemRepository vendaItemRepository,
                                     VendaItemConsumoRepository vendaItemConsumoRepository,
                                     VendaItemModificadorRepository vendaItemModificadorRepository,
                                     VendaItemModificadorImpactoRepository vendaItemModificadorImpactoRepository,
                                     VendaItemParteRepository vendaItemParteRepository,
                                     VendaItemService vendaItemService,
                                     LogRepository logRepository) {
        return new VendaService(
                vendaRepository,
                vendaItemRepository,
                vendaItemConsumoRepository,
                vendaItemModificadorRepository,
                vendaItemModificadorImpactoRepository,
                vendaItemParteRepository,
                vendaItemService,
                logRepository
        );
    }

    @Bean
    public IntegracaoCaixaLegadoService integracaoCaixaLegadoService(CaixaService caixaService) {
        return new IntegracaoCaixaLegadoService(caixaService);
    }

    @Bean
    public FinalizacaoVendaService finalizacaoVendaService(
            VendaRepository vendaRepository,
            VendaItemRepository vendaItemRepository,
            VendaItemConsumoRepository vendaItemConsumoRepository,
            EstoqueProdutoRepository estoqueProdutoRepository,
            MovimentoEstoqueRepository movimentoEstoqueRepository,
            RegistroIdempotenciaRepository registroIdempotenciaRepository,
            VendaItemModificadorImpactoRepository vendaItemModificadorImpactoRepository,
            VendaItemParteRepository vendaItemParteRepository,
            MontagemComposicaoVendaService montagemComposicaoVendaService,
            CalculadoraCustoVendaService calculadoraCustoVendaService,
            IntegracaoCaixaLegadoService integracaoCaixaLegadoService,
            LogRepository logRepository) {
        return new FinalizacaoVendaService(
                vendaRepository,
                vendaItemRepository,
                vendaItemConsumoRepository,
                estoqueProdutoRepository,
                movimentoEstoqueRepository,
                registroIdempotenciaRepository,
                vendaItemModificadorImpactoRepository,
                vendaItemParteRepository,
                montagemComposicaoVendaService,
                calculadoraCustoVendaService,
                integracaoCaixaLegadoService,
                logRepository
        );
    }

    @Bean
    public CancelamentoVendaService cancelamentoVendaService(
            VendaRepository vendaRepository,
            VendaItemRepository vendaItemRepository,
            VendaItemConsumoRepository vendaItemConsumoRepository,
            VendaItemConsumoEstornoRepository vendaItemConsumoEstornoRepository,
            VendaItemParteRepository vendaItemParteRepository,
            EstoqueProdutoRepository estoqueProdutoRepository,
            MovimentoEstoqueRepository movimentoEstoqueRepository,
            RegistroIdempotenciaRepository registroIdempotenciaRepository,
            IntegracaoCaixaLegadoService integracaoCaixaLegadoService,
            LogRepository logRepository) {
        return new CancelamentoVendaService(
                vendaRepository,
                vendaItemRepository,
                vendaItemConsumoRepository,
                vendaItemConsumoEstornoRepository,
                vendaItemParteRepository,
                estoqueProdutoRepository,
                movimentoEstoqueRepository,
                registroIdempotenciaRepository,
                integracaoCaixaLegadoService,
                logRepository
        );
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
