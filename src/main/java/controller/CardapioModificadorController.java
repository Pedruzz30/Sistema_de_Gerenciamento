
package controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import model.CanalVenda;
import model.GrupoModificador;
import model.OpcaoModificador;
import model.OpcaoModificadorComponente;
import model.Permissao;
import model.PoliticaFaltaIngrediente;
import model.TipoEfeitoModificador;
import model.TipoSelecaoModificador;
import model.Usuario;
import model.VariacaoGrupoModificador;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import repository.UsuarioRepository;

import service.AssociacaoVariacaoModificadorService;
import service.CalculadoraDisponibilidadeConfiguracaoService;
import service.DisponibilidadeDtos;
import service.GrupoModificadorService;
import service.MontagemConfiguracaoItemService;
import service.OpcaoModificadorService;
import service.VariacaoItemCardapioService;

/**
 * API de modificadores do cardapio StockOS.
 *
 * Gerencia grupos, opcoes, componentes de estoque
 * e associacoes entre grupos e variacoes.
 *
 * As regras de negocio e transacoes permanecem
 * nos respectivos services.
 *
 * ATENCAO:
 * X-User-RU e um mecanismo legado e nao constitui
 * autenticacao segura para ambientes multi-tenant.
 */
@RestController
@RequestMapping("/api/cardapio")
public class CardapioModificadorController {

    private static final String ERRO_CONSULTA =
            "Voce nao tem permissao para consultar modificadores.";

    private static final String ERRO_EDICAO =
            "Voce nao tem permissao para editar modificadores.";

    private final GrupoModificadorService grupoService;
    private final OpcaoModificadorService opcaoService;
    private final AssociacaoVariacaoModificadorService associacaoService;

    private final MontagemConfiguracaoItemService montagemConfiguracaoItemService;

    private final CalculadoraDisponibilidadeConfiguracaoService disponibilidadeConfiguracaoService;

    private final VariacaoItemCardapioService variacaoItemCardapioService;
    private final UsuarioRepository usuarioRepository;

    public CardapioModificadorController(
            GrupoModificadorService grupoService,
            OpcaoModificadorService opcaoService,
            AssociacaoVariacaoModificadorService associacaoService,
            MontagemConfiguracaoItemService montagemConfiguracaoItemService,
            CalculadoraDisponibilidadeConfiguracaoService disponibilidadeConfiguracaoService,
            VariacaoItemCardapioService variacaoItemCardapioService,
            UsuarioRepository usuarioRepository
    ) {
        this.grupoService = grupoService;
        this.opcaoService = opcaoService;
        this.associacaoService = associacaoService;
        this.montagemConfiguracaoItemService = montagemConfiguracaoItemService;
        this.disponibilidadeConfiguracaoService = disponibilidadeConfiguracaoService;
        this.variacaoItemCardapioService = variacaoItemCardapioService;
        this.usuarioRepository = usuarioRepository;
    }

    // =====================================================
    // GRUPOS DE MODIFICADORES
    // =====================================================

    @PostMapping("/grupos-modificadores")
    public ResponseEntity<GrupoResponse> criarGrupo(
            @RequestBody(required = false) GrupoRequest request,
            @RequestHeader(value = "X-User-RU", required = false)
            String ruHeader
    ) {
        Usuario usuario = validarEdicao(ruHeader);

        exigirCorpo(request, "grupo");

        GrupoModificador grupo = grupoService.criar(
                request.nome(),
                request.descricao(),
                request.codigo(),
                request.tipoSelecao(),
                request.obrigatorio(),
                request.quantidadeMinima(),
                request.quantidadeMaxima(),
                request.permiteRepeticao(),
                request.ativo(),
                request.ordemExibicao(),
                usuario
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(GrupoResponse.from(grupo));
    }

    @GetMapping("/grupos-modificadores")
    public ResponseEntity<List<GrupoResponse>> listarGrupos(
            @RequestHeader(value = "X-User-RU", required = false)
            String ruHeader
    ) {
        validarConsulta(ruHeader);

        return ResponseEntity.ok(
                grupoService.listar()
                        .stream()
                        .map(GrupoResponse::from)
                        .toList()
        );
    }

    @GetMapping("/grupos-modificadores/{id}")
    public ResponseEntity<GrupoResponse> buscarGrupo(
            @PathVariable long id,
            @RequestHeader(value = "X-User-RU", required = false)
            String ruHeader
    ) {
        validarConsulta(ruHeader);
        validarId(id, "grupo");

        return ResponseEntity.ok(
                GrupoResponse.from(
                        grupoService.buscarObrigatorio(id)
                )
        );
    }

    @GetMapping("/grupos-modificadores/{id}/opcoes")
    public ResponseEntity<List<OpcaoResponse>> listarOpcoesGrupo(
            @PathVariable long id,
            @RequestHeader(value = "X-User-RU", required = false)
            String ruHeader
    ) {
        validarConsulta(ruHeader);
        validarId(id, "grupo");

        grupoService.buscarObrigatorio(id);

        return ResponseEntity.ok(
                opcaoService.listarPorGrupo(id)
                        .stream()
                        .map(OpcaoResponse::from)
                        .toList()
        );
    }

    @PutMapping("/grupos-modificadores/{id}")
    public ResponseEntity<GrupoResponse> atualizarGrupo(
            @PathVariable long id,
            @RequestBody(required = false) GrupoRequest request,
            @RequestHeader(value = "X-User-RU", required = false)
            String ruHeader
    ) {
        Usuario usuario = validarEdicao(ruHeader);

        validarId(id, "grupo");
        exigirCorpo(request, "grupo");

        GrupoModificador grupo = grupoService.atualizar(
                id,
                request.nome(),
                request.descricao(),
                request.codigo(),
                request.tipoSelecao(),
                request.obrigatorio(),
                request.quantidadeMinima(),
                request.quantidadeMaxima(),
                request.permiteRepeticao(),
                request.ativo(),
                request.ordemExibicao(),
                usuario
        );

        return ResponseEntity.ok(GrupoResponse.from(grupo));
    }

    @PatchMapping("/grupos-modificadores/{id}/status")
    public ResponseEntity<GrupoResponse> statusGrupo(
            @PathVariable long id,
            @RequestBody(required = false) StatusRequest request,
            @RequestHeader(value = "X-User-RU", required = false)
            String ruHeader
    ) {
        Usuario usuario = validarEdicao(ruHeader);

        validarId(id, "grupo");
        validarStatusRequest(request);

        GrupoModificador grupo = grupoService.alterarStatus(
                id,
                request.ativo(),
                usuario
        );

        return ResponseEntity.ok(GrupoResponse.from(grupo));
    }

    // =====================================================
    // OPCOES DE MODIFICADORES
    // =====================================================

    @PostMapping("/grupos-modificadores/{id}/opcoes")
    public ResponseEntity<OpcaoResponse> criarOpcao(
            @PathVariable long id,
            @RequestBody(required = false) OpcaoRequest request,
            @RequestHeader(value = "X-User-RU", required = false)
            String ruHeader
    ) {
        Usuario usuario = validarEdicao(ruHeader);

        validarId(id, "grupo");
        exigirCorpo(request, "opcao");

        OpcaoModificador opcao = opcaoService.criarOpcao(
                id,
                request.nome(),
                request.descricao(),
                request.codigo(),
                request.acrescimoPreco(),
                request.quantidadeMaximaPorItem(),
                request.selecionadaPorPadrao(),
                request.ativa(),
                request.ordemExibicao(),
                usuario
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OpcaoResponse.from(opcao));
    }

    @PutMapping("/opcoes-modificadores/{id}")
    public ResponseEntity<OpcaoResponse> atualizarOpcao(
            @PathVariable long id,
            @RequestBody(required = false) OpcaoRequest request,
            @RequestHeader(value = "X-User-RU", required = false)
            String ruHeader
    ) {
        Usuario usuario = validarEdicao(ruHeader);

        validarId(id, "opcao");
        exigirCorpo(request, "opcao");

        OpcaoModificador opcao = opcaoService.atualizarOpcao(
                id,
                request.nome(),
                request.descricao(),
                request.codigo(),
                request.acrescimoPreco(),
                request.quantidadeMaximaPorItem(),
                request.selecionadaPorPadrao(),
                request.ativa(),
                request.ordemExibicao(),
                usuario
        );

        return ResponseEntity.ok(OpcaoResponse.from(opcao));
    }

    @PatchMapping("/opcoes-modificadores/{id}/status")
    public ResponseEntity<OpcaoResponse> statusOpcao(
            @PathVariable long id,
            @RequestBody(required = false) StatusRequest request,
            @RequestHeader(value = "X-User-RU", required = false)
            String ruHeader
    ) {
        Usuario usuario = validarEdicao(ruHeader);

        validarId(id, "opcao");
        validarStatusRequest(request);

        OpcaoModificador opcao = opcaoService.alterarStatus(
                id,
                request.ativo(),
                usuario
        );

        return ResponseEntity.ok(OpcaoResponse.from(opcao));
    }

    // =====================================================
    // COMPONENTES DAS OPCOES
    // =====================================================

    @GetMapping("/opcoes-modificadores/{id}/componentes")
    public ResponseEntity<List<ComponenteResponse>>
    listarComponentesOpcao(
            @PathVariable long id,
            @RequestHeader(value = "X-User-RU", required = false)
            String ruHeader
    ) {
        validarConsulta(ruHeader);
        validarId(id, "opcao");

        opcaoService.buscarOpcao(id);

        return ResponseEntity.ok(
                opcaoService.listarComponentes(id)
                        .stream()
                        .map(ComponenteResponse::from)
                        .toList()
        );
    }

    @PostMapping("/opcoes-modificadores/{id}/componentes")
    public ResponseEntity<ComponenteResponse> criarComponente(
            @PathVariable long id,
            @RequestBody(required = false) ComponenteRequest request,
            @RequestHeader(value = "X-User-RU", required = false)
            String ruHeader
    ) {
        Usuario usuario = validarEdicao(ruHeader);

        validarId(id, "opcao");
        exigirCorpo(request, "componente");

        OpcaoModificadorComponente componente =
                opcaoService.criarComponente(
                        id,
                        request.tipoEfeito(),
                        request.produtoOrigemId(),
                        request.produtoDestinoId(),
                        request.quantidadeBase(),
                        request.unidadeBaseId(),
                        request.multiplicador(),
                        request.politicaFalta(),
                        request.observacao(),
                        request.ordem(),
                        usuario
                );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ComponenteResponse.from(componente));
    }

    @PutMapping(
            "/opcoes-modificadores/{id}/componentes/{componenteId}"
    )
    public ResponseEntity<ComponenteResponse> atualizarComponente(
            @PathVariable long id,
            @PathVariable long componenteId,
            @RequestBody(required = false) ComponenteRequest request,
            @RequestHeader(value = "X-User-RU", required = false)
            String ruHeader
    ) {
        Usuario usuario = validarEdicao(ruHeader);

        validarId(id, "opcao");
        validarId(componenteId, "componente");
        exigirCorpo(request, "componente");

        OpcaoModificadorComponente componente =
                opcaoService.atualizarComponente(
                        id,
                        componenteId,
                        request.tipoEfeito(),
                        request.produtoOrigemId(),
                        request.produtoDestinoId(),
                        request.quantidadeBase(),
                        request.unidadeBaseId(),
                        request.multiplicador(),
                        request.politicaFalta(),
                        request.observacao(),
                        request.ordem(),
                        usuario
                );

        return ResponseEntity.ok(
                ComponenteResponse.from(componente)
        );
    }

    @DeleteMapping(
            "/opcoes-modificadores/{id}/componentes/{componenteId}"
    )
    public ResponseEntity<Void> removerComponente(
            @PathVariable long id,
            @PathVariable long componenteId,
            @RequestHeader(value = "X-User-RU", required = false)
            String ruHeader
    ) {
        Usuario usuario = validarEdicao(ruHeader);

        validarId(id, "opcao");
        validarId(componenteId, "componente");

        opcaoService.removerComponente(
                id,
                componenteId,
                usuario
        );

        return ResponseEntity.noContent().build();
    }

    // =====================================================
    // ASSOCIACOES ENTRE VARIACAO E GRUPO
    // =====================================================

    @PostMapping("/variacoes/{variacaoId}/grupos-modificadores")
    public ResponseEntity<AssociacaoResponse> associarGrupo(
            @PathVariable long variacaoId,
            @RequestBody(required = false) AssociacaoRequest request,
            @RequestHeader(value = "X-User-RU", required = false)
            String ruHeader
    ) {
        Usuario usuario = validarEdicao(ruHeader);

        validarId(variacaoId, "variacao");
        exigirCorpo(request, "associacao");
        validarId(request.grupoId(), "grupo");

        VariacaoGrupoModificador associacao =
                associacaoService.associar(
                        variacaoId,
                        request.grupoId(),
                        request.obrigatorioOverride(),
                        request.quantidadeMinimaOverride(),
                        request.quantidadeMaximaOverride(),
                        request.ordemExibicao(),
                        request.ativo(),
                        usuario
                );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AssociacaoResponse.from(associacao));
    }

    @GetMapping("/variacoes/{variacaoId}/grupos-modificadores")
    public ResponseEntity<List<AssociacaoResponse>>
    listarAssociacoesVariacao(
            @PathVariable long variacaoId,
            @RequestHeader(value = "X-User-RU", required = false)
            String ruHeader
    ) {
        validarConsulta(ruHeader);
        validarId(variacaoId, "variacao");

        variacaoItemCardapioService.buscarObrigatoria(variacaoId);

        return ResponseEntity.ok(
                associacaoService.listarPorVariacao(variacaoId)
                        .stream()
                        .map(AssociacaoResponse::from)
                        .toList()
        );
    }

    @PutMapping(
            "/variacoes/{variacaoId}/grupos-modificadores/{associacaoId}"
    )
    public ResponseEntity<AssociacaoResponse> atualizarAssociacao(
            @PathVariable long variacaoId,
            @PathVariable long associacaoId,
            @RequestBody(required = false) AssociacaoRequest request,
            @RequestHeader(value = "X-User-RU", required = false)
            String ruHeader
    ) {
        Usuario usuario = validarEdicao(ruHeader);

        validarId(variacaoId, "variacao");
        validarId(associacaoId, "associacao");
        exigirCorpo(request, "associacao");

        // O grupoId nao e utilizado pela operacao de
        // atualizacao existente no service.
        VariacaoGrupoModificador associacao =
                associacaoService.atualizar(
                        variacaoId,
                        associacaoId,
                        request.obrigatorioOverride(),
                        request.quantidadeMinimaOverride(),
                        request.quantidadeMaximaOverride(),
                        request.ordemExibicao(),
                        request.ativo(),
                        usuario
                );

        return ResponseEntity.ok(
                AssociacaoResponse.from(associacao)
        );
    }

    @DeleteMapping(
            "/variacoes/{variacaoId}/grupos-modificadores/{associacaoId}"
    )
    public ResponseEntity<Void> removerAssociacao(
            @PathVariable long variacaoId,
            @PathVariable long associacaoId,
            @RequestHeader(value = "X-User-RU", required = false)
            String ruHeader
    ) {
        Usuario usuario = validarEdicao(ruHeader);

        validarId(variacaoId, "variacao");
        validarId(associacaoId, "associacao");

        associacaoService.inativar(
                variacaoId,
                associacaoId,
                usuario
        );

        return ResponseEntity.noContent().build();
    }

    // =====================================================
    // CONSULTA DE CONFIGURACAO
    // =====================================================

    @GetMapping("/variacoes/{variacaoId}/configuracao")
    public ResponseEntity<ConfiguracaoResponse> configuracao(
            @PathVariable long variacaoId,
            @RequestParam(required = false) Long filialId,
            @RequestParam(required = false) CanalVenda canal,
            @RequestHeader(value = "X-User-RU", required = false)
            String ruHeader
    ) {
        validarConsulta(ruHeader);
        validarId(variacaoId, "variacao");

        validarContextoOpcional(filialId, canal);

        var variacao =
                variacaoItemCardapioService.buscarObrigatoria(variacaoId);

        if (filialId != null) {
            var avaliacao =
                    disponibilidadeConfiguracaoService
                            .avaliarConfiguracaoOpcoes(
                                    variacao,
                                    filialId,
                                    canal
                            );

            return ResponseEntity.ok(
                    ConfiguracaoResponse.from(
                            avaliacao.configuracao(),
                            avaliacao.opcoesPorId()
                    )
            );
        }

        MontagemConfiguracaoItemService.ConfiguracaoVariacao configuracao =
                montagemConfiguracaoItemService.configurar(variacao);

        return ResponseEntity.ok(
                ConfiguracaoResponse.from(
                        configuracao,
                        Map.of()
                )
        );
    }

    // =====================================================
    // VALIDACAO DE CONFIGURACAO DE VENDA
    // =====================================================

    @PostMapping("/variacoes/{variacaoId}/validar-configuracao")
    public ResponseEntity<
            CalculadoraDisponibilidadeConfiguracaoService
                    .ResultadoValidacaoConfiguracao
    > validarConfiguracao(
            @PathVariable long variacaoId,
            @RequestBody(required = false)
            ValidarConfiguracaoRequest request,
            @RequestHeader(value = "X-User-RU", required = false)
            String ruHeader
    ) {
        validarConsulta(ruHeader);

        validarId(variacaoId, "variacao");
        validarConfiguracaoRequest(request);

        BigDecimal quantidade = request.quantidadeItem() == null
                ? BigDecimal.ONE
                : request.quantidadeItem();

        var resultado = disponibilidadeConfiguracaoService.validar(
                variacaoId,
                request.filialId(),
                request.canal(),
                quantidade,
                request.configuracaoVersao(),
                selecoes(request.selecoes())
        );

        return ResponseEntity.ok(resultado);
    }

    // =====================================================
    // AUTORIZACAO LEGADA
    // =====================================================

    private Usuario validarConsulta(String ruHeader) {
        return ControllerUtils.resolveUserAndRequireAnyPermission(
                ruHeader,
                usuarioRepository,
                ERRO_CONSULTA,
                Permissao.VER_VENDAS,
                Permissao.VER_ESTOQUE,
                Permissao.VER_FINANCAS
        );
    }

    private Usuario validarEdicao(String ruHeader) {
        return ControllerUtils.resolveUserAndRequirePermission(
                ruHeader,
                usuarioRepository,
                Permissao.EDITAR_ESTOQUE,
                ERRO_EDICAO
        );
    }

    // =====================================================
    // VALIDACOES
    // =====================================================

    private static void exigirCorpo(
            Object request,
            String tipo
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Dados de " + tipo + " sao obrigatorios."
            );
        }
    }

    private static void validarId(
            long id,
            String tipo
    ) {
        if (id <= 0) {
            throw new IllegalArgumentException(
                    "ID de " + tipo + " deve ser maior que zero."
            );
        }
    }

    private static void validarStatusRequest(
            StatusRequest request
    ) {
        if (request == null || request.ativo() == null) {
            throw new IllegalArgumentException(
                    "Campo ativo e obrigatorio."
            );
        }
    }

    /**
     * Aceita ambos os parametros ou nenhum.
     *
     * Evita interpretar uma consulta parcial como
     * uma consulta sem contexto de filial e canal.
     */
    private static void validarContextoOpcional(
            Long filialId,
            CanalVenda canal
    ) {
        if ((filialId == null) != (canal == null)) {
            throw new IllegalArgumentException(
                    "Filial e canal devem ser informados juntos."
            );
        }

        if (filialId != null) {
            validarId(filialId, "filial");
        }
    }

    private static void validarConfiguracaoRequest(
            ValidarConfiguracaoRequest request
    ) {
        exigirCorpo(request, "validacao de configuracao");

        if (request.filialId() == null
                || request.filialId() <= 0) {
            throw new IllegalArgumentException(
                    "Filial valida e obrigatoria."
            );
        }

        if (request.canal() == null) {
            throw new IllegalArgumentException(
                    "Canal de venda e obrigatorio."
            );
        }

        if (request.quantidadeItem() != null
                && request.quantidadeItem()
                        .compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Quantidade do item deve ser positiva."
            );
        }

        if (request.selecoes() != null) {
            for (SelecaoRequest selecao : request.selecoes()) {
                validarSelecao(selecao);
            }
        }
    }

    private static void validarSelecao(
            SelecaoRequest selecao
    ) {
        if (selecao == null) {
            throw new IllegalArgumentException(
                    "Selecao de modificador nao pode ser nula."
            );
        }

        if (selecao.grupoId() == null
                || selecao.grupoId() <= 0) {
            throw new IllegalArgumentException(
                    "Selecao deve informar grupoId valido."
            );
        }

        if (selecao.opcaoId() == null
                || selecao.opcaoId() <= 0) {
            throw new IllegalArgumentException(
                    "Selecao deve informar opcaoId valido."
            );
        }

        // Quantidade nula continua representando
        // uma unidade no service atual.
        if (selecao.quantidade() != null
                && selecao.quantidade() <= 0) {
            throw new IllegalArgumentException(
                    "Quantidade da selecao deve ser positiva."
            );
        }
    }

    private static List<
            MontagemConfiguracaoItemService.SelecaoModificadorInput
    > selecoes(List<SelecaoRequest> entradas) {

        if (entradas == null || entradas.isEmpty()) {
            return List.of();
        }

        return entradas.stream()
                .map(selecao ->
                        new MontagemConfiguracaoItemService
                                .SelecaoModificadorInput(
                                        selecao.grupoId(),
                                        selecao.opcaoId(),
                                        selecao.quantidade()
                                )
                )
                .toList();
    }

    // =====================================================
    // REQUEST DTOs
    // =====================================================

    public record GrupoRequest(
            String nome,
            String descricao,
            String codigo,
            TipoSelecaoModificador tipoSelecao,
            Boolean obrigatorio,
            Integer quantidadeMinima,
            Integer quantidadeMaxima,
            Boolean permiteRepeticao,
            Boolean ativo,
            Integer ordemExibicao
    ) {}

    public record OpcaoRequest(
            String nome,
            String descricao,
            String codigo,
            BigDecimal acrescimoPreco,
            Integer quantidadeMaximaPorItem,
            Boolean selecionadaPorPadrao,
            Boolean ativa,
            Integer ordemExibicao
    ) {}

    public record ComponenteRequest(
            TipoEfeitoModificador tipoEfeito,
            Integer produtoOrigemId,
            Integer produtoDestinoId,
            BigDecimal quantidadeBase,
            Long unidadeBaseId,
            BigDecimal multiplicador,
            PoliticaFaltaIngrediente politicaFalta,
            String observacao,
            Integer ordem
    ) {}

    public record AssociacaoRequest(
            long grupoId,
            Boolean obrigatorioOverride,
            Integer quantidadeMinimaOverride,
            Integer quantidadeMaximaOverride,
            Integer ordemExibicao,
            Boolean ativo
    ) {}

    /**
     * Boolean permite distinguir campo ausente
     * de desativacao explicita.
     */
    public record StatusRequest(
            Boolean ativo
    ) {}

    public record ValidarConfiguracaoRequest(
            Long filialId,
            CanalVenda canal,
            BigDecimal quantidadeItem,
            String configuracaoVersao,
            List<SelecaoRequest> selecoes
    ) {}

    public record SelecaoRequest(
            Long grupoId,
            Long opcaoId,
            Integer quantidade
    ) {}

    // =====================================================
    // RESPONSE DTOs
    // =====================================================

    public record GrupoResponse(
            long id,
            String nome,
            String descricao,
            String codigo,
            TipoSelecaoModificador tipoSelecao,
            boolean obrigatorio,
            int quantidadeMinima,
            int quantidadeMaxima,
            boolean permiteRepeticao,
            boolean ativo,
            int ordemExibicao,
            long version
    ) {
        static GrupoResponse from(GrupoModificador grupo) {
            return new GrupoResponse(
                    grupo.getId(),
                    grupo.getNome(),
                    grupo.getDescricao(),
                    grupo.getCodigo(),
                    grupo.getTipoSelecao(),
                    grupo.isObrigatorio(),
                    grupo.getQuantidadeMinima(),
                    grupo.getQuantidadeMaxima(),
                    grupo.isPermiteRepeticao(),
                    grupo.isAtivo(),
                    grupo.getOrdemExibicao(),
                    grupo.getVersion()
            );
        }
    }

    public record OpcaoResponse(
            long id,
            long grupoId,
            String nome,
            String descricao,
            String codigo,
            BigDecimal acrescimoPreco,
            Integer quantidadeMaximaPorItem,
            boolean selecionadaPorPadrao,
            boolean ativa,
            int ordemExibicao,
            long version
    ) {
        static OpcaoResponse from(OpcaoModificador opcao) {
            return new OpcaoResponse(
                    opcao.getId(),
                    opcao.getGrupoModificador().getId(),
                    opcao.getNome(),
                    opcao.getDescricao(),
                    opcao.getCodigo(),
                    opcao.getAcrescimoPreco(),
                    opcao.getQuantidadeMaximaPorItem(),
                    opcao.isSelecionadaPorPadrao(),
                    opcao.isAtiva(),
                    opcao.getOrdemExibicao(),
                    opcao.getVersion()
            );
        }
    }

    public record ComponenteResponse(
            long id,
            long opcaoId,
            TipoEfeitoModificador tipoEfeito,
            Integer produtoOrigemId,
            Integer produtoDestinoId,
            BigDecimal quantidadeBase,
            Long unidadeBaseId,
            BigDecimal multiplicador,
            PoliticaFaltaIngrediente politicaFalta,
            String observacao,
            int ordem
    ) {
        static ComponenteResponse from(
                OpcaoModificadorComponente componente
        ) {
            return new ComponenteResponse(
                    componente.getId(),
                    componente.getOpcaoModificador().getId(),
                    componente.getTipoEfeito(),
                    componente.getProdutoOrigem() == null
                            ? null
                            : componente.getProdutoOrigem().getId(),
                    componente.getProdutoDestino() == null
                            ? null
                            : componente.getProdutoDestino().getId(),
                    componente.getQuantidadeBase(),
                    componente.getUnidadeBase() == null
                            ? null
                            : componente.getUnidadeBase().getId(),
                    componente.getMultiplicador(),
                    componente.getPoliticaFalta(),
                    componente.getObservacao(),
                    componente.getOrdem()
            );
        }
    }

    public record AssociacaoResponse(
            long id,
            long variacaoId,
            long grupoId,
            Boolean obrigatorioOverride,
            Integer quantidadeMinimaOverride,
            Integer quantidadeMaximaOverride,
            boolean obrigatorioEfetivo,
            int minimoEfetivo,
            int maximoEfetivo,
            int ordemExibicao,
            boolean ativo,
            long version
    ) {
        static AssociacaoResponse from(
                VariacaoGrupoModificador associacao
        ) {
            return new AssociacaoResponse(
                    associacao.getId(),
                    associacao.getVariacaoItemCardapio().getId(),
                    associacao.getGrupoModificador().getId(),
                    associacao.getObrigatorioOverride(),
                    associacao.getQuantidadeMinimaOverride(),
                    associacao.getQuantidadeMaximaOverride(),
                    associacao.obrigatorioEfetivo(),
                    associacao.minimoEfetivo(),
                    associacao.maximoEfetivo(),
                    associacao.getOrdemExibicao(),
                    associacao.isAtivo(),
                    associacao.getVersion()
            );
        }
    }

    public record ConfiguracaoResponse(
            long variacaoId,
            String nome,
            BigDecimal precoBase,
            String configuracaoVersao,
            List<GrupoConfiguracaoResponse> grupos
    ) {
        static ConfiguracaoResponse from(
                MontagemConfiguracaoItemService.ConfiguracaoVariacao configuracao
        ) {
            return from(configuracao, Map.of());
        }

        static ConfiguracaoResponse from(
                MontagemConfiguracaoItemService.ConfiguracaoVariacao configuracao,
                Map<Long,
                        CalculadoraDisponibilidadeConfiguracaoService
                                .OpcaoDisponibilidade> disponibilidadePorOpcao
        ) {
            return new ConfiguracaoResponse(
                    configuracao.variacao().getId(),
                    configuracao.variacao().getNome(),
                    configuracao.variacao().getPrecoVenda(),
                    configuracao.configuracaoHash(),
                    configuracao.grupos()
                            .stream()
                            .map(grupo ->
                                    GrupoConfiguracaoResponse.from(
                                            grupo,
                                            disponibilidadePorOpcao
                                    )
                            )
                            .toList()
            );
        }
    }

    public record GrupoConfiguracaoResponse(
            long associacaoId,
            long grupoId,
            String nome,
            String codigo,
            String descricao,
            TipoSelecaoModificador tipoSelecao,
            boolean obrigatorio,
            int minimo,
            int maximo,
            boolean permiteRepeticao,
            boolean ativo,
            int ordemExibicao,
            List<OpcaoConfiguracaoResponse> opcoes
    ) {
        static GrupoConfiguracaoResponse from(
                MontagemConfiguracaoItemService.GrupoConfiguracao grupo,
                Map<Long,
                        CalculadoraDisponibilidadeConfiguracaoService
                                .OpcaoDisponibilidade> disponibilidadePorOpcao
        ) {
            return new GrupoConfiguracaoResponse(
                    grupo.associacaoId(),
                    grupo.grupoId(),
                    grupo.nome(),
                    grupo.codigo(),
                    grupo.descricao(),
                    grupo.tipoSelecao(),
                    grupo.obrigatorio(),
                    grupo.minimo(),
                    grupo.maximo(),
                    grupo.permiteRepeticao(),
                    grupo.ativo(),
                    grupo.ordemExibicao(),
                    grupo.opcoes()
                            .stream()
                            .map(opcao ->
                                    OpcaoConfiguracaoResponse.from(
                                            opcao,
                                            disponibilidadePorOpcao.get(
                                                    opcao.opcaoId()
                                            )
                                    )
                            )
                            .toList()
            );
        }
    }

    public record OpcaoConfiguracaoResponse(
            long opcaoId,
            String nome,
            String codigo,
            String descricao,
            BigDecimal acrescimo,
            Integer quantidadeMaximaPorItem,
            boolean selecionadaPorPadrao,
            boolean ativa,
            boolean disponivel,
            String estado,
            Integer capacidadeEstimada,
            boolean aplicacaoAutomaticaPermitida,
            boolean requerConfirmacao,
            List<DisponibilidadeDtos.MotivoDisponibilidade> motivos,
            List<DisponibilidadeDtos.MotivoDisponibilidade> alertas,
            int ordemExibicao
    ) {
        static OpcaoConfiguracaoResponse from(
                MontagemConfiguracaoItemService.OpcaoConfiguracao opcao,
                CalculadoraDisponibilidadeConfiguracaoService
                        .OpcaoDisponibilidade disponibilidade
        ) {
            boolean disponivel = disponibilidade == null
                    ? opcao.ativa()
                    : disponibilidade.disponivel();

            boolean gratuito = opcao.acrescimo() == null
                    || opcao.acrescimo()
                            .compareTo(BigDecimal.ZERO) == 0;

            return new OpcaoConfiguracaoResponse(
                    opcao.opcaoId(),
                    opcao.nome(),
                    opcao.codigo(),
                    opcao.descricao(),
                    opcao.acrescimo(),
                    opcao.quantidadeMaximaPorItem(),
                    opcao.selecionadaPorPadrao(),
                    opcao.ativa(),
                    disponivel,
                    disponibilidade == null
                            ? (opcao.ativa()
                                    ? "DISPONIVEL"
                                    : "INDISPONIVEL")
                            : disponibilidade.estado(),
                    disponibilidade == null
                            ? null
                            : disponibilidade.capacidadeEstimada(),
                    opcao.selecionadaPorPadrao()
                            && opcao.ativa()
                            && disponivel
                            && gratuito,
                    opcao.selecionadaPorPadrao() && !gratuito,
                    disponibilidade == null
                            ? List.of()
                            : disponibilidade.motivos(),
                    disponibilidade == null
                            ? List.of()
                            : disponibilidade.alertas(),
                    opcao.ordemExibicao()
            );
        }
    }
}

