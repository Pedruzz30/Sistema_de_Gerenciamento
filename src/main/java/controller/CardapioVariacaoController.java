
package controller;

import java.math.BigDecimal;
import java.util.List;

import model.ConfiguracaoComposicaoMultiplaParte;
import model.ItemCardapio;
import model.Permissao;
import model.PoliticaArredondamentoComposicaoMultipla;
import model.PoliticaPrecoComposicaoMultipla;
import model.Produto;
import model.TipoComposicaoVariacao;
import model.Usuario;
import model.VariacaoItemCardapio;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import repository.UsuarioRepository;

import service.ComposicaoMultiplaService;
import service.VariacaoItemCardapioService;

/**
 * API de variacoes do cardapio StockOS.
 *
 * Responsabilidades:
 * - Consultar e cadastrar variacoes;
 * - Atualizar precos e configuracoes;
 * - Ativar ou desativar variacoes;
 * - Gerenciar composicoes multiplas;
 * - Simular precos de composicoes;
 * - Gerar variacoes padrao.
 *
 * ATENCAO:
 * A autorizacao por X-User-RU e legada.
 * O acesso ao estabelecimento deve ser validado
 * por identidade autenticada antes do uso comercial.
 */
@RestController
@RequestMapping("/api/cardapio")
public class CardapioVariacaoController {

    private static final String ERRO_VISUALIZACAO =
            "Voce nao tem permissao para visualizar "
                    + "variacoes de cardapio.";

    private static final String ERRO_EDICAO =
            "Voce nao tem permissao para editar "
                    + "variacoes de cardapio.";

    private final VariacaoItemCardapioService variacaoService;

    private final ComposicaoMultiplaService composicaoMultiplaService;

    private final UsuarioRepository usuarioRepository;

    public CardapioVariacaoController(
            VariacaoItemCardapioService variacaoService,
            ComposicaoMultiplaService composicaoMultiplaService,
            UsuarioRepository usuarioRepository
    ) {
        this.variacaoService = variacaoService;
        this.composicaoMultiplaService = composicaoMultiplaService;
        this.usuarioRepository = usuarioRepository;
    }

    // =====================================================
    // LISTAR VARIACOES
    // =====================================================

    @GetMapping("/itens/{itemId}/variacoes")
    public ResponseEntity<List<VariacaoResponse>> listar(
            @PathVariable int itemId,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario usuario = validarVisualizacao(ruHeader);

        validarItemId(itemId);

        List<VariacaoResponse> resposta = variacaoService
                .listarPorItem(itemId, usuario)
                .stream()
                .map(VariacaoResponse::from)
                .toList();

        return ResponseEntity.ok(resposta);
    }

    // =====================================================
    // CRIAR VARIACAO
    // =====================================================

    @PostMapping("/itens/{itemId}/variacoes")
    public ResponseEntity<VariacaoResponse> criar(
            @PathVariable int itemId,
            @RequestBody(required = false) VariacaoRequest request,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario usuario = validarEdicao(ruHeader);

        validarItemId(itemId);
        validarVariacaoRequest(request);

        VariacaoItemCardapio variacao = variacaoService.criar(
                itemId,
                request.nome(),
                request.codigo(),
                request.precoVenda(),
                request.tipoComposicao(),
                request.produtoDiretoId(),
                request.quantidadeProdutoDiretoBase(),
                request.ativa(),
                request.padrao(),
                request.ordemExibicao(),
                usuario
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(VariacaoResponse.from(variacao));
    }

    // =====================================================
    // BUSCAR VARIACAO
    // =====================================================

    @GetMapping("/variacoes/{variacaoId}")
    public ResponseEntity<VariacaoResponse> buscar(
            @PathVariable long variacaoId,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        validarVisualizacao(ruHeader);

        validarVariacaoId(variacaoId);

        VariacaoItemCardapio variacao =
                variacaoService.buscarObrigatoria(variacaoId);

        return ResponseEntity.ok(
                VariacaoResponse.from(variacao)
        );
    }

    // =====================================================
    // ATUALIZAR VARIACAO
    // =====================================================

    @PutMapping("/variacoes/{variacaoId}")
    public ResponseEntity<VariacaoResponse> atualizar(
            @PathVariable long variacaoId,
            @RequestBody(required = false) VariacaoRequest request,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario usuario = validarEdicao(ruHeader);

        validarVariacaoId(variacaoId);
        validarVariacaoRequest(request);

        VariacaoItemCardapio variacao = variacaoService.atualizar(
                variacaoId,
                request.nome(),
                request.codigo(),
                request.precoVenda(),
                request.tipoComposicao(),
                request.produtoDiretoId(),
                request.quantidadeProdutoDiretoBase(),
                request.ativa(),
                request.padrao(),
                request.ordemExibicao(),
                usuario
        );

        return ResponseEntity.ok(
                VariacaoResponse.from(variacao)
        );
    }

    // =====================================================
    // ALTERAR STATUS
    // =====================================================

    @PatchMapping("/variacoes/{variacaoId}/status")
    public ResponseEntity<VariacaoResponse> alterarStatus(
            @PathVariable long variacaoId,
            @RequestBody(required = false)
            StatusVariacaoRequest request,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario usuario = validarEdicao(ruHeader);

        validarVariacaoId(variacaoId);
        validarStatusRequest(request);

        VariacaoItemCardapio variacao =
                variacaoService.alterarStatus(
                        variacaoId,
                        request.ativa(),
                        usuario
                );

        return ResponseEntity.ok(
                VariacaoResponse.from(variacao)
        );
    }

    // =====================================================
    // GERAR VARIACOES PADRAO
    // =====================================================

    /**
     * Operacao administrativa de backfill.
     *
     * ATENCAO:
     * O service atual percorre todos os itens.
     * Em producao, esta operacao deve ser restrita
     * a um estabelecimento e a um perfil administrativo
     * explicitamente autorizado.
     */
    @PostMapping("/variacoes/backfill-padrao")
    public ResponseEntity<BackfillVariacaoResponse> backfillPadrao(
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario usuario = validarEdicao(ruHeader);

        VariacaoItemCardapioService.BackfillVariacaoResultado resultado =
                variacaoService.garantirVariacoesPadrao(usuario);

        return ResponseEntity.ok(
                new BackfillVariacaoResponse(
                        resultado.criadas(),
                        resultado.pendencias()
                )
        );
    }

    // =====================================================
    // CONSULTAR COMPOSICAO MULTIPLA
    // =====================================================

    @GetMapping("/variacoes/{variacaoId}/composicao-multipla")
    public ResponseEntity<ComposicaoMultiplaResponse>
    buscarComposicaoMultipla(
            @PathVariable long variacaoId,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        validarVisualizacao(ruHeader);

        validarVariacaoId(variacaoId);

        ComposicaoMultiplaService.ConfiguracaoDetalhada detalhada =
                composicaoMultiplaService.buscarDetalhada(variacaoId);

        return ResponseEntity.ok(
                ComposicaoMultiplaResponse.from(detalhada)
        );
    }

    // =====================================================
    // SALVAR COMPOSICAO MULTIPLA
    // =====================================================

    @PutMapping("/variacoes/{variacaoId}/composicao-multipla")
    public ResponseEntity<ComposicaoMultiplaResponse>
    salvarComposicaoMultipla(
            @PathVariable long variacaoId,
            @RequestBody(required = false)
            ComposicaoMultiplaRequest request,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        validarEdicao(ruHeader);

        validarVariacaoId(variacaoId);
        validarComposicaoMultiplaRequest(request);

        ComposicaoMultiplaService.ConfiguracaoInput input =
                new ComposicaoMultiplaService.ConfiguracaoInput(
                        request.ativo(),
                        request.minimoPartes(),
                        request.maximoPartes(),
                        request.quantidadesPermitidas(),
                        request.permiteRepetirPartes(),
                        request.proporcoesIguaisObrigatorias(),
                        request.permiteProporcaoPersonalizada(),
                        request.politicaPreco(),
                        request.politicaArredondamento()
                );

        composicaoMultiplaService.salvarConfiguracao(
                variacaoId,
                input
        );

        return ResponseEntity.ok(
                ComposicaoMultiplaResponse.from(
                        composicaoMultiplaService
                                .buscarDetalhada(variacaoId)
                )
        );
    }

    // =====================================================
    // SALVAR PARTE PERMITIDA
    // =====================================================

    @PutMapping(
            "/variacoes/{variacaoId}/composicao-multipla/partes/{variacaoParteId}"
    )
    public ResponseEntity<ComposicaoMultiplaResponse>
    salvarPartePermitida(
            @PathVariable long variacaoId,
            @PathVariable long variacaoParteId,
            @RequestBody(required = false)
            PartePermitidaRequest request,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        validarEdicao(ruHeader);

        validarVariacaoId(variacaoId);
        validarVariacaoId(variacaoParteId);
        validarPartePermitidaRequest(request);

        ComposicaoMultiplaService.PartePermitidaInput input =
                new ComposicaoMultiplaService.PartePermitidaInput(
                        request.ativa(),
                        request.ordemExibicao()
                );

        composicaoMultiplaService.salvarPartePermitida(
                variacaoId,
                variacaoParteId,
                input
        );

        return ResponseEntity.ok(
                ComposicaoMultiplaResponse.from(
                        composicaoMultiplaService
                                .buscarDetalhada(variacaoId)
                )
        );
    }

    // =====================================================
    // PREVISAO DE COMPOSICAO
    // =====================================================

    @PostMapping(
            "/variacoes/{variacaoId}/composicao-multipla/previsao"
    )
    public ResponseEntity<ComposicaoMultiplaPrevisaoResponse>
    preverComposicaoMultipla(
            @PathVariable long variacaoId,
            @RequestBody(required = false)
            ComposicaoMultiplaPrevisaoRequest request,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        validarVisualizacao(ruHeader);

        validarVariacaoId(variacaoId);
        validarPrevisaoRequest(request);

        List<ComposicaoMultiplaService.ParteInput> partes =
                request.partes()
                        .stream()
                        .map(parte ->
                                new ComposicaoMultiplaService.ParteInput(
                                        parte.variacaoParteId(),
                                        parte.proporcao(),
                                        parte.configuracaoVersao(),
                                        List.of()
                                )
                        )
                        .toList();

        VariacaoItemCardapio variacao =
                variacaoService.buscarObrigatoria(variacaoId);

        ComposicaoMultiplaService.PreparacaoComposicaoMultipla previsao =
                composicaoMultiplaService.preparar(
                        variacao,
                        partes
                );

        return ResponseEntity.ok(
                ComposicaoMultiplaPrevisaoResponse.from(previsao)
        );
    }

    // =====================================================
    // AUTORIZACAO LEGADA
    // =====================================================

    private Usuario validarVisualizacao(String ruHeader) {
        return ControllerUtils.resolveUserAndRequireAnyPermission(
                ruHeader,
                usuarioRepository,
                ERRO_VISUALIZACAO,
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

    private static void validarItemId(int itemId) {
        if (itemId <= 0) {
            throw new IllegalArgumentException(
                    "ID do item deve ser maior que zero."
            );
        }
    }

    private static void validarVariacaoId(long variacaoId) {
        if (variacaoId <= 0) {
            throw new IllegalArgumentException(
                    "ID da variacao deve ser maior que zero."
            );
        }
    }

    private static void validarVariacaoRequest(
            VariacaoRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Dados da variacao sao obrigatorios."
            );
        }

        if (request.nome() == null || request.nome().isBlank()) {
            throw new IllegalArgumentException(
                    "Nome da variacao e obrigatorio."
            );
        }

        if (request.codigo() == null || request.codigo().isBlank()) {
            throw new IllegalArgumentException(
                    "Codigo da variacao e obrigatorio."
            );
        }

        if (request.precoVenda() == null
                || request.precoVenda().signum() < 0) {
            throw new IllegalArgumentException(
                    "Preco da variacao deve ser nao negativo."
            );
        }

        if (request.tipoComposicao() == null) {
            throw new IllegalArgumentException(
                    "Tipo de composicao e obrigatorio."
            );
        }

        if (request.ordemExibicao() != null
                && request.ordemExibicao() < 0) {
            throw new IllegalArgumentException(
                    "Ordem de exibicao nao pode ser negativa."
            );
        }

        // A validacao de compatibilidade entre tipo de
        // composicao e produto direto permanece no service.
    }

    private static void validarStatusRequest(
            StatusVariacaoRequest request
    ) {
        if (request == null || request.ativa() == null) {
            throw new IllegalArgumentException(
                    "Campo ativa e obrigatorio."
            );
        }
    }

    private static void validarComposicaoMultiplaRequest(
            ComposicaoMultiplaRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Dados da composicao multipla sao obrigatorios."
            );
        }

        if (request.ativo() == null
                || request.permiteRepetirPartes() == null
                || request.proporcoesIguaisObrigatorias() == null
                || request.permiteProporcaoPersonalizada() == null) {
            throw new IllegalArgumentException(
                    "Configuracoes booleanas da composicao sao obrigatorias."
            );
        }

        if (request.minimoPartes() == null
                || request.maximoPartes() == null) {
            throw new IllegalArgumentException(
                    "Minimo e maximo de partes sao obrigatorios."
            );
        }

        if (request.minimoPartes() <= 0
                || request.maximoPartes() < request.minimoPartes()) {
            throw new IllegalArgumentException(
                    "Limites de partes invalidos."
            );
        }

        // Politicas, combinacoes de limites e quantidades
        // permitidas continuam sendo validadas pelo dominio.
    }

    private static void validarPartePermitidaRequest(
            PartePermitidaRequest request
    ) {
        if (request == null
                || request.ativa() == null
                || request.ordemExibicao() == null) {
            throw new IllegalArgumentException(
                    "Status e ordem da parte sao obrigatorios."
            );
        }

        if (request.ordemExibicao() < 0) {
            throw new IllegalArgumentException(
                    "Ordem da parte nao pode ser negativa."
            );
        }
    }

    private static void validarPrevisaoRequest(
            ComposicaoMultiplaPrevisaoRequest request
    ) {
        if (request == null
                || request.partes() == null
                || request.partes().isEmpty()) {
            throw new IllegalArgumentException(
                    "Informe ao menos uma parte para a previsao."
            );
        }

        for (PartePrevisaoRequest parte : request.partes()) {
            if (parte == null || parte.variacaoParteId() <= 0) {
                throw new IllegalArgumentException(
                        "Todas as partes devem informar uma variacao valida."
                );
            }

            // Proporcao nula pode ser permitida para
            // calculo automatico de partes iguais.
            if (parte.proporcao() != null
                    && parte.proporcao().signum() <= 0) {
                throw new IllegalArgumentException(
                        "Proporcao informada deve ser positiva."
                );
            }
        }
    }

    // =====================================================
    // REQUEST DTOs
    // =====================================================

    public record VariacaoRequest(
            String nome,
            String codigo,
            BigDecimal precoVenda,
            TipoComposicaoVariacao tipoComposicao,
            Integer produtoDiretoId,
            BigDecimal quantidadeProdutoDiretoBase,
            Boolean ativa,
            Boolean padrao,
            Integer ordemExibicao
    ) {
    }

    /**
     * Boolean diferencia campo ausente de false.
     */
    public record StatusVariacaoRequest(
            Boolean ativa
    ) {
    }

    public record ComposicaoMultiplaRequest(
            Boolean ativo,
            Integer minimoPartes,
            Integer maximoPartes,
            String quantidadesPermitidas,
            Boolean permiteRepetirPartes,
            Boolean proporcoesIguaisObrigatorias,
            Boolean permiteProporcaoPersonalizada,
            PoliticaPrecoComposicaoMultipla politicaPreco,
            PoliticaArredondamentoComposicaoMultipla politicaArredondamento
    ) {
    }

    public record PartePermitidaRequest(
            Boolean ativa,
            Integer ordemExibicao
    ) {
    }

    public record ComposicaoMultiplaPrevisaoRequest(
            List<PartePrevisaoRequest> partes
    ) {
    }

    public record PartePrevisaoRequest(
            long variacaoParteId,
            BigDecimal proporcao,
            String configuracaoVersao
    ) {
    }

    // =====================================================
    // RESPONSE DTOs
    // =====================================================

    public record BackfillVariacaoResponse(
            int criadas,
            List<String> pendencias
    ) {
    }

    public record PartePrevisaoResponse(
            long variacaoParteId,
            String variacao,
            BigDecimal proporcao,
            BigDecimal precoReferencia
    ) {
    }

    public record ComposicaoMultiplaPrevisaoResponse(
            BigDecimal precoUnitarioBase,
            String configuracaoHash,
            String resumoPartes,
            List<PartePrevisaoResponse> partes
    ) {

        public static ComposicaoMultiplaPrevisaoResponse from(
                ComposicaoMultiplaService.PreparacaoComposicaoMultipla previsao
        ) {
            return new ComposicaoMultiplaPrevisaoResponse(
                    previsao.precoUnitarioBase(),
                    previsao.configuracaoHash(),
                    previsao.resumoPartes(),
                    previsao.partes()
                            .stream()
                            .map(parte -> new PartePrevisaoResponse(
                                    parte.variacaoParte().getId(),
                                    parte.variacaoParte().getNome(),
                                    parte.proporcao(),
                                    parte.variacaoParte().getPrecoVenda()
                            ))
                            .toList()
            );
        }
    }

    public record PartePermitidaResponse(
            long id,
            long variacaoParteId,
            int itemParteId,
            String item,
            String variacao,
            String codigoVariacao,
            boolean ativa,
            int ordemExibicao
    ) {

        public static PartePermitidaResponse from(
                ConfiguracaoComposicaoMultiplaParte parte
        ) {
            VariacaoItemCardapio variacaoParte =
                    parte.getVariacaoParte();

            ItemCardapio item =
                    variacaoParte.getItemCardapio();

            return new PartePermitidaResponse(
                    parte.getId(),
                    variacaoParte.getId(),
                    item.getId(),
                    item.getNome(),
                    variacaoParte.getNome(),
                    variacaoParte.getCodigo(),
                    parte.isAtiva(),
                    parte.getOrdemExibicao()
            );
        }
    }

    public record ComposicaoMultiplaResponse(
            Long id,
            Long variacaoId,
            boolean ativo,
            Integer minimoPartes,
            Integer maximoPartes,
            String quantidadesPermitidas,
            Boolean permiteRepetirPartes,
            Boolean proporcoesIguaisObrigatorias,
            Boolean permiteProporcaoPersonalizada,
            PoliticaPrecoComposicaoMultipla politicaPreco,
            PoliticaArredondamentoComposicaoMultipla politicaArredondamento,
            List<PartePermitidaResponse> partesPermitidas
    ) {

        public static ComposicaoMultiplaResponse from(
                ComposicaoMultiplaService.ConfiguracaoDetalhada detalhada
        ) {
            if (detalhada == null) {
                return new ComposicaoMultiplaResponse(
                        null,
                        null,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of()
                );
            }

            var configuracao = detalhada.configuracao();

            List<PartePermitidaResponse> partes =
                    detalhada.partesPermitidas()
                            .stream()
                            .map(PartePermitidaResponse::from)
                            .toList();

            return new ComposicaoMultiplaResponse(
                    configuracao.getId(),
                    configuracao.getVariacaoItemCardapio().getId(),
                    configuracao.isAtivo(),
                    configuracao.getMinimoPartes(),
                    configuracao.getMaximoPartes(),
                    configuracao.getQuantidadesPermitidas(),
                    configuracao.isPermiteRepetirPartes(),
                    configuracao.isProporcoesIguaisObrigatorias(),
                    configuracao.isPermiteProporcaoPersonalizada(),
                    configuracao.getPoliticaPreco(),
                    configuracao.getPoliticaArredondamento(),
                    partes
            );
        }
    }

    public record VariacaoResponse(
            long id,
            int itemCardapioId,
            String itemCodigo,
            String itemNome,
            String nome,
            String codigo,
            BigDecimal precoVenda,
            TipoComposicaoVariacao tipoComposicao,
            Integer produtoDiretoId,
            String produtoDiretoNome,
            BigDecimal quantidadeProdutoDiretoBase,
            boolean ativa,
            boolean padrao,
            int ordemExibicao,
            long version
    ) {

        public static VariacaoResponse from(
                VariacaoItemCardapio variacao
        ) {
            Produto produto = variacao.getProdutoDireto();
            ItemCardapio item = variacao.getItemCardapio();

            return new VariacaoResponse(
                    variacao.getId(),
                    item.getId(),
                    item.getCodigo(),
                    item.getNome(),
                    variacao.getNome(),
                    variacao.getCodigo(),
                    variacao.getPrecoVenda(),
                    variacao.getTipoComposicao(),
                    produto != null ? produto.getId() : null,
                    produto != null ? produto.getNome() : null,
                    variacao.getQuantidadeProdutoDiretoBase(),
                    variacao.isAtiva(),
                    variacao.isPadrao(),
                    variacao.getOrdemExibicao(),
                    variacao.getVersion()
            );
        }
    }
}

