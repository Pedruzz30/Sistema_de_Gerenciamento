
package controller;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import model.ClasseFuncionario;
import model.Permissao;
import model.Usuario;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import repository.UsuarioRepository;
import service.CadastroFuncionarioService;

/**
 * API de gerenciamento de funcionarios do StockOS.
 *
 * Permite:
 * - Consultar funcionarios;
 * - Cadastrar funcionarios;
 * - Alterar classes;
 * - Desativar funcionarios;
 * - Consultar classes disponiveis.
 *
 * As operacoes de persistencia e auditoria
 * permanecem no CadastroFuncionarioService.
 *
 * ATENCAO:
 * A identidade baseada em X-User-RU e legada.
 * Este controller nao substitui autenticacao real
 * nem isolamento por estabelecimento.
 */
@RestController
@RequestMapping("/api/funcionarios")
public class FuncionarioController {

    private static final String ERRO_GERENCIAMENTO =
            "Voce nao tem permissao para gerenciar funcionarios.";

    private static final String CLASSE_SUPERIOR = "SUPERIOR";
    private static final String CLASSE_GERENTE = "GERENTE_ESTOQUE";
    private static final String CLASSE_ESTOQUISTA = "ESTOQUISTA";
    private static final String CLASSE_CAIXA = "CAIXA";

    private static final List<ClasseResponse> CLASSES_DISPONIVEIS =
            List.of(
                    new ClasseResponse(
                            CLASSE_SUPERIOR,
                            "Acesso total ao sistema"
                    ),
                    new ClasseResponse(
                            CLASSE_GERENTE,
                            "Gerencia estoque e compras"
                    ),
                    new ClasseResponse(
                            CLASSE_ESTOQUISTA,
                            "Movimenta itens de estoque"
                    ),
                    new ClasseResponse(
                            CLASSE_CAIXA,
                            "Responsavel por vendas"
                    )
            );

    private final CadastroFuncionarioService funcionarioService;
    private final UsuarioRepository usuarioRepository;

    public FuncionarioController(
            CadastroFuncionarioService funcionarioService,
            UsuarioRepository usuarioRepository
    ) {
        this.funcionarioService = funcionarioService;
        this.usuarioRepository = usuarioRepository;
    }

    // =====================================================
    // LISTAR FUNCIONARIOS
    // =====================================================

    @GetMapping
    public ResponseEntity<List<FuncionarioResponse>> listar(
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario ator = validarGerenciamento(ruHeader);

        List<FuncionarioResponse> funcionarios =
                funcionarioService.listarFuncionarios(ator)
                        .stream()
                        .map(FuncionarioResponse::from)
                        .toList();

        return ResponseEntity.ok(funcionarios);
    }

    // =====================================================
    // CADASTRAR FUNCIONARIO
    // =====================================================

    /**
     * Cadastra um funcionario.
     *
     * Somente um usuario SUPERIOR pode cadastrar
     * funcionarios com classes privilegiadas:
     * SUPERIOR ou GERENTE_ESTOQUE.
     */
    @PostMapping
    public ResponseEntity<FuncionarioResponse> cadastrar(
            @RequestBody(required = false)
            CadastroFuncionarioRequest request,

            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario ator = validarGerenciamento(ruHeader);

        validarCadastroRequest(request);

        ClasseFuncionario classe =
                criarClasse(request.nomeClasse());

        validarClasseDestino(ator, classe);

        Usuario usuario =
                funcionarioService.cadastrarFuncionario(
                        ator,
                        request.nome().trim(),
                        request.sobrenome().trim(),
                        request.cpf().trim(),
                        request.senha(),
                        classe
                );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(FuncionarioResponse.from(usuario));
    }

    // =====================================================
    // ALTERAR CLASSE
    // =====================================================

    /**
     * Altera a classe de um funcionario.
     *
     * Restricoes:
     * - Nao permite alterar a propria classe;
     * - Gerentes nao podem alterar administradores;
     * - Apenas SUPERIOR pode atribuir cargos
     *   com permissao administrativa elevada.
     */
    @PutMapping("/{ru}/classe")
    public ResponseEntity<FuncionarioResponse> mudarClasse(
            @PathVariable long ru,

            @RequestBody(required = false)
            MudarClasseRequest request,

            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario ator = validarGerenciamento(ruHeader);

        validarRu(ru);
        validarMudarClasseRequest(request);

        ClasseFuncionario novaClasse =
                criarClasse(request.nomeClasse());

        validarClasseDestino(ator, novaClasse);
        validarAlvoGerenciamento(ator, ru);

        Usuario usuario =
                funcionarioService.mudarClasse(
                        ator,
                        ru,
                        novaClasse
                );

        return ResponseEntity.ok(
                FuncionarioResponse.from(usuario)
        );
    }

    // =====================================================
    // DESATIVAR FUNCIONARIO
    // =====================================================

    /**
     * Desativa um funcionario sem excluir
     * seus registros historicos.
     *
     * Impede autodesativacao e impede que
     * gerentes desativem outros administradores.
     */
    @PutMapping("/{ru}/desativar")
    public ResponseEntity<MensagemResponse> desativar(
            @PathVariable long ru,

            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario ator = validarGerenciamento(ruHeader);

        validarRu(ru);
        validarAlvoGerenciamento(ator, ru);

        funcionarioService.desativarFuncionario(
                ator,
                ru
        );

        return ResponseEntity.ok(
                new MensagemResponse(
                        "Funcionario desativado com sucesso."
                )
        );
    }

    // =====================================================
    // LISTAR CLASSES
    // =====================================================

    /**
     * Retorna apenas as classes que o usuario
     * esta autorizado a atribuir.
     *
     * SUPERIOR: todas as classes.
     * GERENTE_ESTOQUE: ESTOQUISTA e CAIXA.
     */
    @GetMapping("/classes")
    public ResponseEntity<List<ClasseResponse>> listarClasses(
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario ator = validarGerenciamento(ruHeader);

        if (ehSuperior(ator)) {
            return ResponseEntity.ok(CLASSES_DISPONIVEIS);
        }

        return ResponseEntity.ok(
                CLASSES_DISPONIVEIS.stream()
                        .filter(classe ->
                                CLASSE_ESTOQUISTA.equals(classe.nome())
                                        || CLASSE_CAIXA.equals(classe.nome())
                        )
                        .toList()
        );
    }

    // =====================================================
    // AUTORIZACAO
    // =====================================================

    private Usuario validarGerenciamento(
            String ruHeader
    ) {
        return ControllerUtils
                .resolveUserAndRequirePermission(
                        ruHeader,
                        usuarioRepository,
                        Permissao.GERENCIAR_FUNCIONARIOS,
                        ERRO_GERENCIAMENTO
                );
    }

    /**
     * Identifica a classe SUPERIOR do modelo legado.
     *
     * Nao utiliza Usuario.Perfil, porque o perfil
     * nao determina permissoes no dominio StockOS.
     */
    private static boolean ehSuperior(
            Usuario usuario
    ) {
        if (usuario == null
                || !usuario.isAtivo()
                || usuario.getClasse() == null) {
            return false;
        }

        ClasseFuncionario classe = usuario.getClasse();

        return classe.getId() == 1L
                && CLASSE_SUPERIOR.equals(classe.getNome())
                && classe.possuiPermissao(
                        Permissao.GERENCIAR_FUNCIONARIOS
                );
    }

    /**
     * Impede que um gerente conceda privilegios
     * administrativos iguais ou superiores aos seus.
     *
     * A verificacao tambem deve existir no service,
     * para proteger chamadas fora da API REST.
     */
    private static void validarClasseDestino(
            Usuario ator,
            ClasseFuncionario classeDestino
    ) {
        if (classeDestino == null) {
            throw new IllegalArgumentException(
                    "Classe de destino e obrigatoria."
            );
        }

        boolean classePrivilegiada =
                classeDestino.getId() == 1L
                        || classeDestino.getId() == 2L
                        || classeDestino.possuiPermissao(
                                Permissao.GERENCIAR_FUNCIONARIOS
                        );

        if (classePrivilegiada && !ehSuperior(ator)) {
            throw new SecurityException(
                    "Somente um usuario SUPERIOR pode "
                            + "atribuir classes administrativas."
            );
        }
    }

    /**
     * Protege funcionarios administrativos
     * e impede alteracoes na propria conta.
     */
    private void validarAlvoGerenciamento(
            Usuario ator,
            long ruAlvo
    ) {
        if (ator.getRu() == ruAlvo) {
            throw new SecurityException(
                    "Nao e permitido alterar a propria "
                            + "classe ou desativar a propria conta."
            );
        }

        Usuario alvo = usuarioRepository.buscarPorRu(ruAlvo)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Funcionario nao encontrado."
                        )
                );

        ClasseFuncionario classeAlvo = alvo.getClasse();

        boolean alvoPrivilegiado =
                classeAlvo != null
                        && (
                        classeAlvo.getId() == 1L
                                || classeAlvo.getId() == 2L
                                || classeAlvo.possuiPermissao(
                                        Permissao.GERENCIAR_FUNCIONARIOS
                                )
                );

        if (alvoPrivilegiado && !ehSuperior(ator)) {
            throw new SecurityException(
                    "Somente um usuario SUPERIOR pode "
                            + "gerenciar outros administradores."
            );
        }
    }

    // =====================================================
    // VALIDACOES DE ENTRADA
    // =====================================================

    private static void validarRu(
            long ru
    ) {
        if (ru <= 0) {
            throw new IllegalArgumentException(
                    "RU do funcionario deve ser maior que zero."
            );
        }
    }

    private static void validarCadastroRequest(
            CadastroFuncionarioRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Dados do funcionario sao obrigatorios."
            );
        }

        validarTextoObrigatorio(
                request.nome(),
                "Nome",
                80
        );

        validarTextoObrigatorio(
                request.sobrenome(),
                "Sobrenome",
                120
        );

        validarTextoObrigatorio(
                request.cpf(),
                "CPF",
                20
        );

        validarTextoObrigatorio(
                request.senha(),
                "Senha",
                120
        );

        validarTextoObrigatorio(
                request.nomeClasse(),
                "Classe do funcionario",
                80
        );

        // Os digitos verificadores do CPF sao
        // validados pelo modelo Usuario.
        //
        // A senha permanece no mecanismo legado
        // e deve ser migrada para armazenamento
        // com hash antes do uso comercial.
    }

    private static void validarMudarClasseRequest(
            MudarClasseRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Dados da alteracao de classe sao obrigatorios."
            );
        }

        validarTextoObrigatorio(
                request.nomeClasse(),
                "Classe",
                80
        );
    }

    private static void validarTextoObrigatorio(
            String valor,
            String campo,
            int tamanhoMaximo
    ) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(
                    campo + " e obrigatorio."
            );
        }

        if (valor.trim().length() > tamanhoMaximo) {
            throw new IllegalArgumentException(
                    campo + " excede o limite de "
                            + tamanhoMaximo + " caracteres."
            );
        }
    }

    // =====================================================
    // CRIACAO DE CLASSES
    // =====================================================

    /**
     * Fabrica de classes do modelo legado.
     *
     * Usa ClasseFuncionario.Builder para
     * evitar chamadas repetitivas de
     * adicionarPermissao().
     */
    private static ClasseFuncionario criarClasse(
            String nome
    ) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException(
                    "Classe do funcionario e obrigatoria."
            );
        }

        String normalizado =
                nome.trim().toUpperCase(Locale.ROOT);

        return switch (normalizado) {

            case CLASSE_SUPERIOR ->
                    ClasseFuncionario.builder(
                                    1,
                                    CLASSE_SUPERIOR,
                                    "Acesso total ao sistema"
                            )
                            .todasAsPermissoes()
                            .build();

            case CLASSE_GERENTE ->
                    ClasseFuncionario.builder(
                                    2,
                                    CLASSE_GERENTE,
                                    "Gerencia estoque e compras"
                            )
                            .permissoes(
                                    Permissao.VER_ESTOQUE,
                                    Permissao.EDITAR_ESTOQUE,
                                    Permissao.VER_COMPRAS,
                                    Permissao.VER_FINANCAS,
                                    Permissao.GERENCIAR_FUNCIONARIOS,
                                    Permissao.VER_LOGS
                            )
                            .build();

            case CLASSE_ESTOQUISTA ->
                    ClasseFuncionario.builder(
                                    3,
                                    CLASSE_ESTOQUISTA,
                                    "Movimenta itens de estoque"
                            )
                            .permissoes(
                                    Permissao.VER_ESTOQUE,
                                    Permissao.EDITAR_ESTOQUE
                            )
                            .build();

            case CLASSE_CAIXA ->
                    ClasseFuncionario.builder(
                                    4,
                                    CLASSE_CAIXA,
                                    "Responsavel por vendas"
                            )
                            .permissoes(
                                    Permissao.VER_VENDAS,
                                    Permissao.VER_ESTOQUE
                            )
                            .build();

            default -> throw new IllegalArgumentException(
                    "Classe invalida. Utilize: SUPERIOR, "
                            + "GERENTE_ESTOQUE, ESTOQUISTA ou CAIXA."
            );
        };
    }

    // =====================================================
    // REQUEST DTOs
    // =====================================================

    public record CadastroFuncionarioRequest(
            String nome,
            String sobrenome,
            String cpf,
            String senha,
            String nomeClasse
    ) {
    }

    public record MudarClasseRequest(
            String nomeClasse
    ) {
    }

    // =====================================================
    // RESPONSE DTO - FUNCIONARIO
    // =====================================================

    public record FuncionarioResponse(
            long ru,
            String nome,
            String sobrenome,
            String nomeClasse,
            Set<String> permissoes,
            String perfil,
            boolean ativo
    ) {
        public static FuncionarioResponse from(
                Usuario usuario
        ) {
            ClasseFuncionario classe = usuario.getClasse();

            Set<String> permissoes = classe == null
                    ? Set.of()
                    : classe.getPermissoes()
                            .stream()
                            .map(Enum::name)
                            .collect(
                                    Collectors.toCollection(
                                            TreeSet::new
                                    )
                            );

            return new FuncionarioResponse(
                    usuario.getRu(),
                    usuario.getNome(),
                    usuario.getSobrenome(),
                    classe == null ? null : classe.getNome(),
                    permissoes,
                    usuario.getPerfil() == null
                            ? null
                            : usuario.getPerfil().name(),
                    usuario.isAtivo()
            );
        }
    }

    // =====================================================
    // RESPONSE DTO - CLASSE
    // =====================================================

    public record ClasseResponse(
            String nome,
            String descricao
    ) {
    }

    // =====================================================
    // RESPONSE DTO - MENSAGEM
    // =====================================================

    public record MensagemResponse(
            String mensagem
    ) {
    }

    // =====================================================
    // RESPONSE DTO - ERRO
    // =====================================================

    public record ErroResponse(
            String erro
    ) {
    }
}

