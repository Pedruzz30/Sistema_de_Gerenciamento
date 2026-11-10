
package controller;

import java.math.BigDecimal;
import java.util.List;

import model.FichaTecnica;
import model.FichaTecnicaCustoSnapshot;
import model.FichaTecnicaIngrediente;
import model.FichaTecnicaIngredienteCustoSnapshot;
import model.FichaTecnicaVersao;
import model.Permissao;
import model.PoliticaFaltaIngrediente;
import model.Usuario;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import repository.FichaTecnicaCustoSnapshotRepository;
import repository.FichaTecnicaIngredienteCustoSnapshotRepository;
import repository.FichaTecnicaIngredienteRepository;
import repository.UsuarioRepository;

import service.CalculadoraCustoFichaTecnicaService;
import service.CustoFichaTecnicaResultado;
import service.FichaTecnicaIngredienteService;
import service.FichaTecnicaService;
import service.PublicacaoFichaTecnicaService;
import service.SimulacaoPrecoFichaTecnicaService;

/**
 * API de fichas tecnicas do StockOS.
 *
 * Responsabilidades:
 * - Criar fichas tecnicas;
 * - Consultar fichas e versoes;
 * - Editar e descartar rascunhos;
 * - Gerenciar ingredientes;
 * - Publicar versoes;
 * - Calcular custos;
 * - Simular precos de venda.
 *
 * As transacoes e regras de negocio permanecem
 * nos services correspondentes.
 *
 * ATENCAO:
 * O mecanismo X-User-RU e legado.
 *
 * O isolamento por estabelecimento e filial deve
 * ser implementado no backend antes de expor
 * estas operacoes comercialmente.
 */
@RestController
@RequestMapping("/api")
public class FichaTecnicaController {

    /**
     * Compatibilidade com o fluxo legado.
     *
     * Esta constante NAO representa o estabelecimento
     * autenticado e deve ser eliminada na migracao
     * definitiva para multi-tenancy.
     */
    private static final Long FILIAL_PADRAO_ID = 1L;

    private static final String ERRO_VISUALIZACAO =
            "Voce nao tem permissao para visualizar ficha tecnica.";

    private static final String ERRO_EDICAO =
            "Voce nao tem permissao para editar ficha tecnica.";

    private static final String ERRO_CUSTO =
            "Voce nao tem permissao para visualizar custos de ficha tecnica.";

    private static final BigDecimal CEM =
            new BigDecimal("100");

    private final FichaTecnicaService fichaTecnicaService;
    private final FichaTecnicaIngredienteService ingredienteService;
    private final CalculadoraCustoFichaTecnicaService calculadoraCusto;
    private final PublicacaoFichaTecnicaService publicacaoService;
    private final SimulacaoPrecoFichaTecnicaService simulacaoService;

    private final FichaTecnicaIngredienteRepository ingredienteRepository;
    private final FichaTecnicaCustoSnapshotRepository snapshotRepository;
    private final FichaTecnicaIngredienteCustoSnapshotRepository ingredienteSnapshotRepository;

    private final UsuarioRepository usuarioRepository;

    public FichaTecnicaController(
            FichaTecnicaService fichaTecnicaService,
            FichaTecnicaIngredienteService ingredienteService,
            CalculadoraCustoFichaTecnicaService calculadoraCusto,
            PublicacaoFichaTecnicaService publicacaoService,
            SimulacaoPrecoFichaTecnicaService simulacaoService,
            FichaTecnicaIngredienteRepository ingredienteRepository,
            FichaTecnicaCustoSnapshotRepository snapshotRepository,
            FichaTecnicaIngredienteCustoSnapshotRepository ingredienteSnapshotRepository,
            UsuarioRepository usuarioRepository
    ) {
        this.fichaTecnicaService = fichaTecnicaService;
        this.ingredienteService = ingredienteService;
        this.calculadoraCusto = calculadoraCusto;
        this.publicacaoService = publicacaoService;
        this.simulacaoService = simulacaoService;
        this.ingredienteRepository = ingredienteRepository;
        this.snapshotRepository = snapshotRepository;
        this.ingredienteSnapshotRepository = ingredienteSnapshotRepository;
        this.usuarioRepository = usuarioRepository;
    }

    // =====================================================
    // CRIACAO DE FICHA TECNICA
    // =====================================================

    @PostMapping("/cardapio/variacoes/{variacaoId}/ficha-tecnica")
    public ResponseEntity<VersaoResponse> criarFicha(
            @PathVariable long variacaoId,
            @RequestBody(required = false) CriarFichaRequest request,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario usuario = validarEdicao(ruHeader);

        validarId(variacaoId, "variacao");
        validarCriarFichaRequest(request);

        FichaTecnicaVersao versao =
                fichaTecnicaService.criarFicha(
                        variacaoId,
                        request.nome(),
                        request.rendimentoQuantidade(),
                        request.rendimentoUnidadeId(),
                        request.quantidadePorPorcao(),
                        request.tempoPreparoMinutos(),
                        request.observacaoInterna(),
                        usuario
                );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(versaoResponse(versao));
    }

    // =====================================================
    // CONSULTA DE FICHA
    // =====================================================

    @GetMapping("/fichas-tecnicas/{fichaId}")
    public ResponseEntity<FichaResponse> buscarFicha(
            @PathVariable long fichaId,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        validarVisualizacao(ruHeader);
        validarId(fichaId, "ficha");

        FichaTecnica ficha =
                fichaTecnicaService.buscarFichaObrigatoria(fichaId);

        return ResponseEntity.ok(FichaResponse.from(ficha));
    }

    // =====================================================
    // LISTAGEM DE VERSOES
    // =====================================================

    @GetMapping("/fichas-tecnicas/{fichaId}/versoes")
    public ResponseEntity<List<VersaoResponse>> listarVersoes(
            @PathVariable long fichaId,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        validarVisualizacao(ruHeader);
        validarId(fichaId, "ficha");

        List<VersaoResponse> versoes =
                fichaTecnicaService.listarVersoes(fichaId)
                        .stream()
                        .map(this::versaoResponse)
                        .toList();

        return ResponseEntity.ok(versoes);
    }

    // =====================================================
    // CONSULTA DE VERSAO
    // =====================================================

    @GetMapping("/fichas-tecnicas/{fichaId}/versoes/{versaoId}")
    public ResponseEntity<VersaoResponse> buscarVersao(
            @PathVariable long fichaId,
            @PathVariable long versaoId,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        validarVisualizacao(ruHeader);
        validarIdsVersao(fichaId, versaoId);

        FichaTecnicaVersao versao =
                fichaTecnicaService.buscarVersaoObrigatoria(
                        fichaId,
                        versaoId
                );

        return ResponseEntity.ok(versaoResponse(versao));
    }

    // =====================================================
    // ATUALIZACAO DE RASCUNHO
    // =====================================================

    @PutMapping("/fichas-tecnicas/{fichaId}/versoes/{versaoId}")
    public ResponseEntity<VersaoResponse> atualizarRascunho(
            @PathVariable long fichaId,
            @PathVariable long versaoId,
            @RequestBody(required = false) AtualizarVersaoRequest request,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario usuario = validarEdicao(ruHeader);

        validarIdsVersao(fichaId, versaoId);
        validarAtualizarVersaoRequest(request);

        FichaTecnicaVersao versao =
                fichaTecnicaService.atualizarRascunho(
                        fichaId,
                        versaoId,
                        request.rendimentoQuantidade(),
                        request.rendimentoUnidadeId(),
                        request.quantidadePorPorcao(),
                        request.tempoPreparoMinutos(),
                        request.observacaoInterna(),
                        request.motivoAlteracao(),
                        usuario
                );

        return ResponseEntity.ok(versaoResponse(versao));
    }

    // =====================================================
    // NOVA VERSAO
    // =====================================================

    @PostMapping("/fichas-tecnicas/{fichaId}/nova-versao")
    public ResponseEntity<VersaoResponse> novaVersao(
            @PathVariable long fichaId,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario usuario = validarEdicao(ruHeader);
        validarId(fichaId, "ficha");

        FichaTecnicaVersao versao =
                fichaTecnicaService.novaVersao(
                        fichaId,
                        usuario
                );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(versaoResponse(versao));
    }

    // =====================================================
    // PUBLICACAO DE VERSAO
    // =====================================================

    /**
     * Publica uma versao de ficha tecnica.
     *
     * A verificacao do estado RASCUNHO, a
     * consistencia dos custos, o snapshot e
     * as transacoes ficam no service.
     *
     * A verificacao de acesso a filial ainda
     * precisa ser implementada no backend.
     */
    @PostMapping(
            "/fichas-tecnicas/{fichaId}/versoes/{versaoId}/publicar"
    )
    public ResponseEntity<VersaoResponse> publicar(
            @PathVariable long fichaId,
            @PathVariable long versaoId,
            @RequestBody(required = false) PublicarFichaRequest request,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario usuario = validarPublicacao(ruHeader);

        validarIdsVersao(fichaId, versaoId);
        exigirCorpo(request, "publicacao");

        Long filialId = resolverFilialLegada(
                request.filialReferenciaId()
        );

        FichaTecnicaVersao versao =
                publicacaoService.publicar(
                        fichaId,
                        versaoId,
                        filialId,
                        request.motivo(),
                        usuario
                );

        return ResponseEntity.ok(versaoResponse(versao));
    }

    // =====================================================
    // DESCARTE DE RASCUNHO
    // =====================================================

    @PostMapping(
            "/fichas-tecnicas/{fichaId}/versoes/{versaoId}/descartar"
    )
    public ResponseEntity<VersaoResponse> descartar(
            @PathVariable long fichaId,
            @PathVariable long versaoId,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario usuario = validarEdicao(ruHeader);
        validarIdsVersao(fichaId, versaoId);

        FichaTecnicaVersao versao =
                fichaTecnicaService.descartarRascunho(
                        fichaId,
                        versaoId,
                        usuario
                );

        return ResponseEntity.ok(versaoResponse(versao));
    }

    // =====================================================
    // ADICIONAR INGREDIENTE
    // =====================================================

    @PostMapping(
            "/fichas-tecnicas/{fichaId}/versoes/{versaoId}/ingredientes"
    )
    public ResponseEntity<IngredienteResponse> adicionarIngrediente(
            @PathVariable long fichaId,
            @PathVariable long versaoId,
            @RequestBody(required = false) IngredienteRequest request,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario usuario = validarEdicao(ruHeader);

        validarIdsVersao(fichaId, versaoId);
        validarIngredienteRequest(request);

        FichaTecnicaIngrediente ingrediente =
                ingredienteService.adicionar(
                        fichaId,
                        versaoId,
                        request.produtoId(),
                        request.unidadeInformadaId(),
                        request.quantidadeInformada(),
                        request.aplicarPerda(),
                        request.percentualPerda(),
                        request.ingredienteCritico(),
                        request.politicaFalta(),
                        request.ordem(),
                        request.observacao(),
                        usuario
                );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(IngredienteResponse.from(ingrediente));
    }

    // =====================================================
    // ATUALIZAR INGREDIENTE
    // =====================================================

    @PutMapping(
            "/fichas-tecnicas/{fichaId}/versoes/{versaoId}/ingredientes/{ingredienteId}"
    )
    public ResponseEntity<IngredienteResponse> atualizarIngrediente(
            @PathVariable long fichaId,
            @PathVariable long versaoId,
            @PathVariable long ingredienteId,
            @RequestBody(required = false) IngredienteRequest request,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario usuario = validarEdicao(ruHeader);

        validarIdsVersao(fichaId, versaoId);
        validarId(ingredienteId, "ingrediente");
        validarIngredienteRequest(request);

        FichaTecnicaIngrediente ingrediente =
                ingredienteService.atualizar(
                        fichaId,
                        versaoId,
                        ingredienteId,
                        request.produtoId(),
                        request.unidadeInformadaId(),
                        request.quantidadeInformada(),
                        request.aplicarPerda(),
                        request.percentualPerda(),
                        request.ingredienteCritico(),
                        request.politicaFalta(),
                        request.ordem(),
                        request.observacao(),
                        usuario
                );

        return ResponseEntity.ok(
                IngredienteResponse.from(ingrediente)
        );
    }

    // =====================================================
    // REMOVER INGREDIENTE
    // =====================================================

    @DeleteMapping(
            "/fichas-tecnicas/{fichaId}/versoes/{versaoId}/ingredientes/{ingredienteId}"
    )
    public ResponseEntity<Void> removerIngrediente(
            @PathVariable long fichaId,
            @PathVariable long versaoId,
            @PathVariable long ingredienteId,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario usuario = validarEdicao(ruHeader);

        validarIdsVersao(fichaId, versaoId);
        validarId(ingredienteId, "ingrediente");

        ingredienteService.remover(
                fichaId,
                versaoId,
                ingredienteId,
                usuario
        );

        return ResponseEntity.noContent().build();
    }

    // =====================================================
    // CALCULO DE CUSTO
    // =====================================================

    /**
     * Consulta o custo da versao usando o estoque
     * de referencia da filial.
     *
     * A selecao de filial ainda e legada.
     */
    @GetMapping(
            "/fichas-tecnicas/{fichaId}/versoes/{versaoId}/custo"
    )
    public ResponseEntity<CustoResponse> calcularCusto(
            @PathVariable long fichaId,
            @PathVariable long versaoId,
            @RequestParam(
                    value = "filialId",
                    required = false
            ) Long filialId,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        validarVerCusto(ruHeader);
        validarIdsVersao(fichaId, versaoId);

        Long filial = resolverFilialLegada(filialId);

        FichaTecnicaVersao versao =
                fichaTecnicaService.buscarVersaoObrigatoria(
                        fichaId,
                        versaoId
                );

        CustoFichaTecnicaResultado custo =
                calculadoraCusto.calcular(
                        versao,
                        filial,
                        versao.getFichaTecnica()
                                .getVariacaoItemCardapio()
                                .getPrecoVenda()
                );

        return ResponseEntity.ok(CustoResponse.from(custo));
    }

    // =====================================================
    // SIMULACAO DE PRECO
    // =====================================================

    /**
     * Calcula sugestoes de preco usando um CMV alvo.
     *
     * O service realiza o calculo matematico.
     */
    @PostMapping(
            "/fichas-tecnicas/{fichaId}/versoes/{versaoId}/simular-preco"
    )
    public ResponseEntity<SimulacaoPrecoResponse> simularPreco(
            @PathVariable long fichaId,
            @PathVariable long versaoId,
            @RequestBody(required = false) SimularPrecoRequest request,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        validarVerCusto(ruHeader);

        validarIdsVersao(fichaId, versaoId);
        validarSimularPrecoRequest(request);

        Long filial = resolverFilialLegada(
                request.filialId()
        );

        FichaTecnicaVersao versao =
                fichaTecnicaService.buscarVersaoObrigatoria(
                        fichaId,
                        versaoId
                );

        var resultado = simulacaoService.simular(
                versao,
                filial,
                versao.getFichaTecnica()
                        .getVariacaoItemCardapio()
                        .getPrecoVenda(),
                request.cmvAlvoPercentual()
        );

        return ResponseEntity.ok(
                SimulacaoPrecoResponse.from(resultado)
        );
    }

    // =====================================================
    // MAPEAMENTO DE VERSOES
    // =====================================================

    /**
     * Monta a resposta completa de uma versao.
     *
     * Inclui ingredientes e snapshots de custo.
     *
     * Futuramente, avaliar consultas otimizadas
     * para reduzir o problema N+1 em listagens.
     */
    private VersaoResponse versaoResponse(
            FichaTecnicaVersao versao
    ) {
        List<IngredienteResponse> ingredientes =
                ingredienteRepository
                        .listarPorVersao(versao.getId())
                        .stream()
                        .map(IngredienteResponse::from)
                        .toList();

        List<CustoSnapshotResponse> snapshots =
                snapshotRepository
                        .listarPorVersao(versao.getId())
                        .stream()
                        .map(this::snapshotResponse)
                        .toList();

        return VersaoResponse.from(
                versao,
                ingredientes,
                snapshots
        );
    }

    private CustoSnapshotResponse snapshotResponse(
            FichaTecnicaCustoSnapshot snapshot
    ) {
        List<IngredienteSnapshotResponse> ingredientes =
                ingredienteSnapshotRepository
                        .listarPorSnapshot(snapshot.getId())
                        .stream()
                        .map(IngredienteSnapshotResponse::from)
                        .toList();

        return CustoSnapshotResponse.from(
                snapshot,
                ingredientes
        );
    }

    // =====================================================
    // AUTORIZACAO LEGADA
    // =====================================================

    private Usuario validarVisualizacao(
            String ruHeader
    ) {
        return ControllerUtils
                .resolveUserAndRequireAnyPermission(
                        ruHeader,
                        usuarioRepository,
                        ERRO_VISUALIZACAO,
                        Permissao.VER_ESTOQUE,
                        Permissao.EDITAR_ESTOQUE,
                        Permissao.VER_FINANCAS,
                        Permissao.VER_VENDAS
                );
    }

    private Usuario validarEdicao(
            String ruHeader
    ) {
        return ControllerUtils
                .resolveUserAndRequirePermission(
                        ruHeader,
                        usuarioRepository,
                        Permissao.EDITAR_ESTOQUE,
                        ERRO_EDICAO
                );
    }

    private Usuario validarPublicacao(
            String ruHeader
    ) {
        // Preserva a regra de permissao atual.
        // Publicacao administrativa mais restrita
        // deve ser definida na politica de acesso.
        return validarEdicao(ruHeader);
    }

    private Usuario validarVerCusto(
            String ruHeader
    ) {
        return ControllerUtils
                .resolveUserAndRequireAnyPermission(
                        ruHeader,
                        usuarioRepository,
                        ERRO_CUSTO,
                        Permissao.VER_FINANCAS,
                        Permissao.EDITAR_ESTOQUE
                );
    }

    // =====================================================
    // VALIDACOES COMPARTILHADAS
    // =====================================================

    private static void exigirCorpo(
            Object request,
            String operacao
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Dados de " + operacao + " sao obrigatorios."
            );
        }
    }

    private static void validarId(
            long id,
            String recurso
    ) {
        if (id <= 0) {
            throw new IllegalArgumentException(
                    "ID de " + recurso + " deve ser maior que zero."
            );
        }
    }

    private static void validarIdsVersao(
            long fichaId,
            long versaoId
    ) {
        validarId(fichaId, "ficha");
        validarId(versaoId, "versao");
    }

    private static void validarPositivo(
            BigDecimal valor,
            String campo
    ) {
        if (valor == null
                || valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    campo + " deve ser maior que zero."
            );
        }
    }

    // =====================================================
    // VALIDACAO DE FICHA E VERSAO
    // =====================================================

    private static void validarCriarFichaRequest(
            CriarFichaRequest request
    ) {
        exigirCorpo(request, "criacao da ficha");

        if (request.nome() == null
                || request.nome().isBlank()) {
            throw new IllegalArgumentException(
                    "Nome da ficha tecnica e obrigatorio."
            );
        }

        validarDadosRendimento(
                request.rendimentoQuantidade(),
                request.rendimentoUnidadeId(),
                request.quantidadePorPorcao(),
                request.tempoPreparoMinutos()
        );
    }

    private static void validarAtualizarVersaoRequest(
            AtualizarVersaoRequest request
    ) {
        exigirCorpo(request, "atualizacao da versao");

        validarDadosRendimento(
                request.rendimentoQuantidade(),
                request.rendimentoUnidadeId(),
                request.quantidadePorPorcao(),
                request.tempoPreparoMinutos()
        );
    }

    private static void validarDadosRendimento(
            BigDecimal rendimentoQuantidade,
            Long rendimentoUnidadeId,
            BigDecimal quantidadePorPorcao,
            Integer tempoPreparoMinutos
    ) {
        validarPositivo(
                rendimentoQuantidade,
                "Rendimento da ficha tecnica"
        );

        if (rendimentoUnidadeId == null
                || rendimentoUnidadeId <= 0) {
            throw new IllegalArgumentException(
                    "Unidade de rendimento valida e obrigatoria."
            );
        }

        if (quantidadePorPorcao != null
                && quantidadePorPorcao.compareTo(
                        BigDecimal.ZERO
                ) <= 0) {
            throw new IllegalArgumentException(
                    "Quantidade por porcao deve ser positiva."
            );
        }

        if (tempoPreparoMinutos != null
                && tempoPreparoMinutos < 0) {
            throw new IllegalArgumentException(
                    "Tempo de preparo nao pode ser negativo."
            );
        }
    }

    // =====================================================
    // VALIDACAO DE INGREDIENTES
    // =====================================================

    private static void validarIngredienteRequest(
            IngredienteRequest request
    ) {
        exigirCorpo(request, "ingrediente");

        if (request.produtoId() == null
                || request.produtoId() <= 0) {
            throw new IllegalArgumentException(
                    "Produto valido e obrigatorio."
            );
        }

        if (request.unidadeInformadaId() == null
                || request.unidadeInformadaId() <= 0) {
            throw new IllegalArgumentException(
                    "Unidade do ingrediente e obrigatoria."
            );
        }

        validarPositivo(
                request.quantidadeInformada(),
                "Quantidade do ingrediente"
        );

        if (request.ordem() != null
                && request.ordem() < 0) {
            throw new IllegalArgumentException(
                    "Ordem do ingrediente nao pode ser negativa."
            );
        }

        BigDecimal percentualPerda = request.percentualPerda();

        if (percentualPerda != null
                && (percentualPerda.compareTo(
                        BigDecimal.ZERO
                ) < 0
                || percentualPerda.compareTo(CEM) >= 0)) {
            throw new IllegalArgumentException(
                    "Percentual de perda deve estar entre 0 e 100, "
                            + "sem incluir 100."
            );
        }

        // A compatibilidade entre produto, unidade,
        // modo de perda e politica de falta permanece
        // sob responsabilidade do ingredienteService.
        //
        // Campos booleanos nulos conservam os
        // comportamentos definidos no dominio.
    }

    // =====================================================
    // VALIDACAO DE SIMULACAO
    // =====================================================

    private static void validarSimularPrecoRequest(
            SimularPrecoRequest request
    ) {
        exigirCorpo(request, "simulacao de preco");

        BigDecimal cmv = request.cmvAlvoPercentual();

        if (cmv == null
                || cmv.compareTo(BigDecimal.ZERO) <= 0
                || cmv.compareTo(CEM) >= 0) {
            throw new IllegalArgumentException(
                    "CMV alvo deve ser maior que zero e menor que 100."
            );
        }
    }

    // =====================================================
    // FILIAL - COMPATIBILIDADE LEGADA
    // =====================================================

    /**
     * Preserva a filial padrao utilizada hoje.
     *
     * NAO faz verificacao de propriedade da filial.
     *
     * Na arquitetura multi-tenant, a filial devera
     * ser resolvida e autorizada com base na
     * identidade autenticada no servidor.
     */
    private static Long resolverFilialLegada(
            Long filialId
    ) {
        Long resolvida = filialId == null
                ? FILIAL_PADRAO_ID
                : filialId;

        validarId(resolvida, "filial");

        return resolvida;
    }

    // =====================================================
    // REQUEST DTOs
    // =====================================================

    public record CriarFichaRequest(
            String nome,
            BigDecimal rendimentoQuantidade,
            Long rendimentoUnidadeId,
            BigDecimal quantidadePorPorcao,
            Integer tempoPreparoMinutos,
            String observacaoInterna
    ) {}

    public record AtualizarVersaoRequest(
            BigDecimal rendimentoQuantidade,
            Long rendimentoUnidadeId,
            BigDecimal quantidadePorPorcao,
            Integer tempoPreparoMinutos,
            String observacaoInterna,
            String motivoAlteracao
    ) {}

    public record PublicarFichaRequest(
            Long filialReferenciaId,
            String motivo
    ) {}

    public record IngredienteRequest(
            Integer produtoId,
            Long unidadeInformadaId,
            BigDecimal quantidadeInformada,
            Boolean aplicarPerda,
            BigDecimal percentualPerda,
            Boolean ingredienteCritico,
            PoliticaFaltaIngrediente politicaFalta,
            Integer ordem,
            String observacao
    ) {}

    public record SimularPrecoRequest(
            Long filialId,
            BigDecimal cmvAlvoPercentual
    ) {}

    // =====================================================
    // RESPONSE DTO - FICHA
    // =====================================================

    public record FichaResponse(
            long id,
            long variacaoItemCardapioId,
            String nome,
            boolean ativa,
            Long versaoPublicadaAtualId,
            Integer numeroVersaoPublicadaAtual,
            String criadaEm,
            Long criadaPor,
            long version
    ) {
        public static FichaResponse from(
                FichaTecnica ficha
        ) {
            FichaTecnicaVersao publicada =
                    ficha.getVersaoPublicadaAtual();

            return new FichaResponse(
                    ficha.getId(),
                    ficha.getVariacaoItemCardapio().getId(),
                    ficha.getNome(),
                    ficha.isAtiva(),
                    publicada == null ? null : publicada.getId(),
                    publicada == null
                            ? null
                            : publicada.getNumeroVersao(),
                    ficha.getCriadaEm().toString(),
                    ficha.getCriadaPor(),
                    ficha.getVersion()
            );
        }
    }

    // =====================================================
    // RESPONSE DTO - VERSAO
    // =====================================================

    public record VersaoResponse(
            long id,
            long fichaTecnicaId,
            int numeroVersao,
            String status,
            BigDecimal rendimentoQuantidade,
            Long rendimentoUnidadeId,
            String rendimentoUnidadeCodigo,
            BigDecimal quantidadePorPorcao,
            Integer tempoPreparoMinutos,
            String observacaoInterna,
            String motivoAlteracao,
            String criadaEm,
            Long criadaPor,
            String publicadaEm,
            Long publicadaPor,
            String substituidaEm,
            long version,
            List<IngredienteResponse> ingredientes,
            List<CustoSnapshotResponse> snapshots
    ) {
        public static VersaoResponse from(
                FichaTecnicaVersao versao,
                List<IngredienteResponse> ingredientes,
                List<CustoSnapshotResponse> snapshots
        ) {
            return new VersaoResponse(
                    versao.getId(),
                    versao.getFichaTecnica().getId(),
                    versao.getNumeroVersao(),
                    versao.getStatus().name(),
                    versao.getRendimentoQuantidade(),
                    versao.getRendimentoUnidade().getId(),
                    versao.getRendimentoUnidade().getCodigo(),
                    versao.getQuantidadePorPorcao(),
                    versao.getTempoPreparoMinutos(),
                    versao.getObservacaoInterna(),
                    versao.getMotivoAlteracao(),
                    versao.getCriadaEm().toString(),
                    versao.getCriadaPor(),
                    versao.getPublicadaEm() == null
                            ? null
                            : versao.getPublicadaEm().toString(),
                    versao.getPublicadaPor(),
                    versao.getSubstituidaEm() == null
                            ? null
                            : versao.getSubstituidaEm().toString(),
                    versao.getVersion(),
                    ingredientes,
                    snapshots
            );
        }
    }

    // =====================================================
    // RESPONSE DTO - INGREDIENTE
    // =====================================================

    public record IngredienteResponse(
            long id,
            int produtoId,
            String produtoNome,
            Long unidadeInformadaId,
            String unidadeInformadaCodigo,
            BigDecimal quantidadeInformada,
            BigDecimal quantidadeLiquidaBase,
            BigDecimal percentualPerda,
            BigDecimal quantidadeBrutaBase,
            String unidadeBaseCodigo,
            boolean ingredienteCritico,
            String politicaFalta,
            int ordem,
            String observacao
    ) {
        public static IngredienteResponse from(
                FichaTecnicaIngrediente ingrediente
        ) {
            var produto = ingrediente.getProduto();
            var unidade = ingrediente.getUnidadeInformada();

            return new IngredienteResponse(
                    ingrediente.getId(),
                    produto.getId(),
                    produto.getNome(),
                    unidade.getId(),
                    unidade.getCodigo(),
                    ingrediente.getQuantidadeInformada(),
                    ingrediente.getQuantidadeLiquidaBase(),
                    ingrediente.getPercentualPerda(),
                    ingrediente.getQuantidadeBrutaBase(),
                    produto.getUnidadeBase() == null
                            ? null
                            : produto.getUnidadeBase().getCodigo(),
                    ingrediente.isIngredienteCritico(),
                    ingrediente.getPoliticaFalta().name(),
                    ingrediente.getOrdem(),
                    ingrediente.getObservacao()
            );
        }
    }

    // =====================================================
    // RESPONSE DTO - CUSTO
    // =====================================================

    public record CustoResponse(
            long fichaTecnicaId,
            long versaoId,
            int numeroVersao,
            String status,
            Long filialId,
            BigDecimal rendimentoQuantidade,
            String rendimentoUnidade,
            List<IngredienteCustoResponse> ingredientes,
            List<CustoFichaTecnicaResultado.IngredientePendente> ingredientesPendentes,
            BigDecimal custoTotal,
            BigDecimal custoPorUnidadeRendimento,
            BigDecimal precoVenda,
            BigDecimal cmvPercentual,
            BigDecimal margemBrutaEstimada,
            BigDecimal margemBrutaPercentual,
            boolean custoCompleto,
            String tipoAnalise,
            String calculadoEm
    ) {
        public static CustoResponse from(
                CustoFichaTecnicaResultado resultado
        ) {
            return new CustoResponse(
                    resultado.fichaTecnicaId(),
                    resultado.versaoId(),
                    resultado.numeroVersao(),
                    resultado.status().name(),
                    resultado.filialId(),
                    resultado.rendimentoQuantidade(),
                    resultado.rendimentoUnidadeCodigo(),
                    resultado.ingredientes()
                            .stream()
                            .map(IngredienteCustoResponse::from)
                            .toList(),
                    resultado.ingredientesPendentes(),
                    resultado.custoTotal(),
                    resultado.custoPorUnidadeRendimento(),
                    resultado.precoVenda(),
                    resultado.cmvPercentual(),
                    resultado.margemBrutaEstimada(),
                    resultado.margemBrutaPercentual(),
                    resultado.custoCompleto(),
                    resultado.tipoAnalise(),
                    resultado.calculadoEm().toString()
            );
        }
    }

    // =====================================================
    // RESPONSE DTO - CUSTO POR INGREDIENTE
    // =====================================================

    public record IngredienteCustoResponse(
            long ingredienteId,
            int produtoId,
            String produto,
            BigDecimal quantidadeBrutaBase,
            String unidadeBase,
            BigDecimal custoMedioUnitario,
            BigDecimal custoTotal,
            boolean custoDisponivel
    ) {
        public static IngredienteCustoResponse from(
                CustoFichaTecnicaResultado.IngredienteCusto ingrediente
        ) {
            return new IngredienteCustoResponse(
                    ingrediente.ingredienteId(),
                    ingrediente.produtoId(),
                    ingrediente.produto(),
                    ingrediente.quantidadeBrutaBase(),
                    ingrediente.unidadeBaseCodigo(),
                    ingrediente.custoMedioUnitario(),
                    ingrediente.custoTotal(),
                    ingrediente.custoDisponivel()
            );
        }
    }

    // =====================================================
    // RESPONSE DTO - SNAPSHOT
    // =====================================================

    public record CustoSnapshotResponse(
            long id,
            long versaoId,
            Long filialId,
            BigDecimal custoTotal,
            BigDecimal custoPorUnidadeRendimento,
            String calculadoEm,
            List<IngredienteSnapshotResponse> ingredientes
    ) {
        public static CustoSnapshotResponse from(
                FichaTecnicaCustoSnapshot snapshot,
                List<IngredienteSnapshotResponse> ingredientes
        ) {
            return new CustoSnapshotResponse(
                    snapshot.getId(),
                    snapshot.getFichaTecnicaVersao().getId(),
                    snapshot.getFilialId(),
                    snapshot.getCustoTotal(),
                    snapshot.getCustoPorUnidadeRendimento(),
                    snapshot.getCalculadoEm().toString(),
                    ingredientes
            );
        }
    }

    // =====================================================
    // RESPONSE DTO - SNAPSHOT DO INGREDIENTE
    // =====================================================

    public record IngredienteSnapshotResponse(
            long id,
            long ingredienteId,
            int produtoId,
            String produto,
            BigDecimal quantidadeBase,
            BigDecimal custoMedioUnitario,
            BigDecimal custoTotalIngrediente
    ) {
        public static IngredienteSnapshotResponse from(
                FichaTecnicaIngredienteCustoSnapshot snapshot
        ) {
            var ingrediente = snapshot.getFichaTecnicaIngrediente();
            var produto = snapshot.getProduto();

            return new IngredienteSnapshotResponse(
                    snapshot.getId(),
                    ingrediente.getId(),
                    produto.getId(),
                    produto.getNome(),
                    snapshot.getQuantidadeBase(),
                    snapshot.getCustoMedioUnitario(),
                    snapshot.getCustoTotalIngrediente()
            );
        }
    }

    // =====================================================
    // RESPONSE DTO - SIMULACAO DE PRECO
    // =====================================================

    public record SimulacaoPrecoResponse(
            CustoResponse custo,
            BigDecimal precoSugeridoMatematico,
            List<BigDecimal> sugestoesComerciais,
            boolean simulacaoCompleta
    ) {
        public static SimulacaoPrecoResponse from(
                SimulacaoPrecoFichaTecnicaService.ResultadoSimulacao resultado
        ) {
            return new SimulacaoPrecoResponse(
                    CustoResponse.from(resultado.custo()),
                    resultado.precoSugeridoMatematico(),
                    resultado.sugestoesComerciais(),
                    resultado.simulacaoCompleta()
            );
        }
    }
}

