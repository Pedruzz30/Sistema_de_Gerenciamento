
package controller;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import org.springframework.http.converter.HttpMessageNotReadableException;

import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import service.ErroNegocioException;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Tratamento centralizado de excecoes REST do StockOS.
 *
 * Formato de resposta:
 *
 * {
 *   "erro": "Mensagem do erro",
 *   "codigo": "CODIGO_DO_ERRO",
 *   "detalhes": {}
 * }
 *
 * Responsabilidades:
 * - Traduzir excecoes para status HTTP;
 * - Preservar codigos de negocio;
 * - Tratar erros de parametros e JSON;
 * - Evitar vazamento de excecoes internas;
 * - Registrar logs sem copiar X-User-RU.
 *
 * IMPORTANTE:
 * A autenticacao e autorizacao devem ser
 * realizadas em seus componentes especificos.
 * Este handler apenas traduz os erros.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final Pattern ID_SEGURO =
            Pattern.compile("[A-Za-z0-9._-]{1,80}");

    private static final String ERRO_INTERNO =
            "Erro interno do servidor.";

    private static final String ERRO_REQUISICAO =
            "Requisicao invalida.";

    // =====================================================
    // CONTRATO DE RESPOSTA
    // =====================================================

    public record ErroResponse(
            String erro,
            String codigo,
            Map<String, Object> detalhes
    ) {}

    // =====================================================
    // ERROS DE NEGOCIO
    // =====================================================

    /**
     * Preserva os codigos produzidos pelos services.
     *
     * Exemplos:
     * - Compra ja confirmada -> 409;
     * - Ficha nao encontrada -> 404;
     * - Regra de negocio invalida -> 400.
     */
    @ExceptionHandler(ErroNegocioException.class)
    public ResponseEntity<ErroResponse> handleErroNegocio(
            ErroNegocioException ex,
            HttpServletRequest request
    ) {
        HttpStatus status = statusErroNegocio(
                ex.getCodigo()
        );

        return responder(
                status,
                ex.getCodigo(),
                ex.getMessage(),
                ex.getDetalhes(),
                request
        );
    }

    // =====================================================
    // ARGUMENTOS INVALIDOS
    // =====================================================

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErroResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request
    ) {
        String codigo = codigoPorMensagem(
                ex.getMessage(),
                "REQUISICAO_INVALIDA"
        );

        return responder(
                HttpStatus.BAD_REQUEST,
                codigo,
                mensagemOuPadrao(ex.getMessage(), ERRO_REQUISICAO),
                Map.of(),
                request
        );
    }

    // =====================================================
    // ESTADO INVALIDO
    // =====================================================

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErroResponse> handleIllegalState(
            IllegalStateException ex,
            HttpServletRequest request
    ) {
        String codigo = codigoPorMensagem(
                ex.getMessage(),
                "ESTADO_INVALIDO"
        );

        // Preserva o conflito da ficha imutavel.
        HttpStatus status =
                "FICHA_VERSAO_IMUTAVEL".equals(codigo)
                        ? HttpStatus.CONFLICT
                        : HttpStatus.BAD_REQUEST;

        return responder(
                status,
                codigo,
                mensagemOuPadrao(
                        ex.getMessage(),
                        "Operacao nao permitida no estado atual."
                ),
                Map.of(),
                request
        );
    }

    // =====================================================
    // AUTORIZACAO LEGADA
    // =====================================================

    /**
     * Preserva o status 403 dos controllers legados.
     *
     * Nao confundir esta excecao com a autenticacao
     * do Spring Security, que deve possuir tratamento
     * especifico quando for integrada.
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErroResponse> handleSecurity(
            SecurityException ex,
            HttpServletRequest request
    ) {
        return responder(
                HttpStatus.FORBIDDEN,
                "ACESSO_NEGADO",
                mensagemOuPadrao(
                        ex.getMessage(),
                        "Acesso negado."
                ),
                Map.of(),
                request
        );
    }

    // =====================================================
    // JSON INVALIDO OU CORPO INCOMPATIVEL
    // =====================================================

    /**
     * Exemplos:
     * - JSON malformado;
     * - Tipo de campo incompatível;
     * - Corpo obrigatorio ausente.
     *
     * Nao devolve mensagens internas do Jackson.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResponse> handleJsonInvalido(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        return responder(
                HttpStatus.BAD_REQUEST,
                "JSON_INVALIDO",
                "Corpo da requisicao ausente, invalido ou incompatível.",
                Map.of(),
                request
        );
    }

    // =====================================================
    // PARAMETROS OBRIGATORIOS
    // =====================================================

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErroResponse> handleParametroAusente(
            MissingServletRequestParameterException ex,
            HttpServletRequest request
    ) {
        return responder(
                HttpStatus.BAD_REQUEST,
                "PARAMETRO_OBRIGATORIO",
                "Parametro obrigatorio ausente: "
                        + ex.getParameterName(),
                Map.of(),
                request
        );
    }

    // =====================================================
    // HEADER OBRIGATORIO
    // =====================================================

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErroResponse> handleHeaderAusente(
            MissingRequestHeaderException ex,
            HttpServletRequest request
    ) {
        return responder(
                HttpStatus.BAD_REQUEST,
                "HEADER_OBRIGATORIO",
                "Cabecalho obrigatorio ausente: "
                        + ex.getHeaderName(),
                Map.of(),
                request
        );
    }

    // =====================================================
    // TIPO DE PARAMETRO INCORRETO
    // =====================================================

    /**
     * Exemplo:
     * GET /api/fornecedores/abc
     *
     * Quando o controller espera um long,
     * a API deve retornar 400 em vez de 500.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErroResponse> handleTipoInvalido(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {
        return responder(
                HttpStatus.BAD_REQUEST,
                "TIPO_PARAMETRO_INVALIDO",
                "Tipo invalido para o parametro: "
                        + ex.getName(),
                Map.of(),
                request
        );
    }

    // =====================================================
    // VALIDACAO DE OBJETOS
    // =====================================================

    /**
     * Compativel com futuras validacoes @Valid
     * ou @Validated nos DTOs.
     *
     * Retorna nomes dos campos invalidos,
     * sem expor os valores recebidos.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponse> handleValidacao(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        List<String> campos = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(erro -> erro.getField())
                .distinct()
                .limit(20)
                .toList();

        return responder(
                HttpStatus.BAD_REQUEST,
                "VALIDACAO_INVALIDA",
                "Existem campos invalidos na requisicao.",
                Map.of("campos", campos),
                request
        );
    }

    // =====================================================
    // METODO HTTP NAO PERMITIDO
    // =====================================================

    /**
     * Exemplo:
     * DELETE em um endpoint que aceita apenas GET.
     *
     * Preserva tambem o cabecalho HTTP Allow.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErroResponse> handleMetodoNaoPermitido(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.METHOD_NOT_ALLOWED;

        registrarAviso(
                request,
                status,
                "METODO_NAO_PERMITIDO"
        );

        ResponseEntity.BodyBuilder builder =
                ResponseEntity.status(status);

        String[] metodosPermitidos =
                ex.getSupportedMethods();

        if (metodosPermitidos != null
                && metodosPermitidos.length > 0) {
            builder.header(
                    HttpHeaders.ALLOW,
                    String.join(", ", metodosPermitidos)
            );
        }

        return builder.body(
                new ErroResponse(
                        "Metodo HTTP nao permitido para esta rota.",
                        "METODO_NAO_PERMITIDO",
                        Map.of()
                )
        );
    }

    // =====================================================
    // TIPO DE CONTEUDO NAO SUPORTADO
    // =====================================================

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErroResponse> handleTipoConteudo(
            HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request
    ) {
        return responder(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "TIPO_CONTEUDO_NAO_SUPORTADO",
                "Tipo de conteudo nao suportado para esta operacao.",
                Map.of(),
                request
        );
    }

    // =====================================================
    // RECURSO ESTATICO OU ROTA INEXISTENTE
    // =====================================================

    /**
     * Evita transformar um recurso nao encontrado
     * em erro HTTP 500.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErroResponse> handleRecursoAusente(
            NoResourceFoundException ex,
            HttpServletRequest request
    ) {
        return responder(
                HttpStatus.NOT_FOUND,
                "RECURSO_NAO_ENCONTRADO",
                "Recurso nao encontrado.",
                Map.of(),
                request
        );
    }

    // =====================================================
    // STATUS HTTP EXPLICITO
    // =====================================================

    /**
     * Preserva status declarados com
     * ResponseStatusException.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErroResponse> handleResponseStatus(
            ResponseStatusException ex,
            HttpServletRequest request
    ) {
        HttpStatusCode status = ex.getStatusCode();

        if (status.is5xxServerError()) {
            LOGGER.error(
                    "Falha HTTP interna. status={} metodo={} uri={}",
                    status.value(),
                    request.getMethod(),
                    request.getRequestURI(),
                    ex
            );

            return responder(
                    status,
                    "ERRO_INTERNO",
                    ERRO_INTERNO,
                    Map.of(),
                    request
            );
        }

        return responder(
                status,
                "HTTP_" + status.value(),
                mensagemOuPadrao(
                        ex.getReason(),
                        "Requisicao nao pode ser processada."
                ),
                Map.of(),
                request
        );
    }

    // =====================================================
    // ERROS INESPERADOS
    // =====================================================

    /**
     * Falhas desconhecidas recebem resposta
     * generica para nao expor dados internos.
     *
     * O stack trace permanece apenas nos logs.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponse> handleGeneric(
            Exception ex,
            HttpServletRequest request
    ) {
        LOGGER.error(
                "Erro inesperado na API. metodo={} uri={} "
                        + "correlationId={} requestId={}",
                request.getMethod(),
                request.getRequestURI(),
                identificadorSeguro(request, "X-Correlation-Id"),
                identificadorSeguro(request, "X-Request-Id"),
                ex
        );

        return ResponseEntity.status(
                HttpStatus.INTERNAL_SERVER_ERROR
        ).body(
                new ErroResponse(
                        ERRO_INTERNO,
                        "ERRO_INTERNO",
                        Map.of()
                )
        );
    }

    // =====================================================
    // RESPOSTA PADRONIZADA
    // =====================================================

    private static ResponseEntity<ErroResponse> responder(
            HttpStatusCode status,
            String codigo,
            String mensagem,
            Map<String, Object> detalhes,
            HttpServletRequest request
    ) {
        registrarAviso(request, status, codigo);

        Map<String, Object> dados =
                detalhes == null ? Map.of() : detalhes;

        return ResponseEntity.status(status)
                .body(
                        new ErroResponse(
                                mensagem,
                                codigo,
                                dados
                        )
                );
    }

    // =====================================================
    // STATUS DE ERROS DE NEGOCIO
    // =====================================================

    /**
     * Preserva os codigos de status dos
     * erros de negocio ja utilizados no StockOS.
     */
    private static HttpStatus statusErroNegocio(
            String codigo
    ) {
        return switch (codigo) {

            case "IDEMPOTENCY_KEY_REUSED",
                 "CHAVE_IDEMPOTENCIA_REUTILIZADA_COM_PAYLOAD_DIFERENTE",
                 "COMPRA_JA_CONFIRMADA",
                 "CONCORRENCIA_ESTOQUE",
                 "MOVIMENTO_JA_ESTORNADO",
                 "MOVIMENTO_NAO_PODE_SER_ESTORNADO",
                 "MOVIMENTO_TOTALMENTE_ESTORNADO",
                 "QUANTIDADE_ESTORNO_EXCEDE_DISPONIVEL",
                 "ESTOQUE_INSUFICIENTE_PARA_ESTORNO",
                 "FICHA_TECNICA_JA_EXISTE",
                 "FICHA_RASCUNHO_JA_EXISTE",
                 "FICHA_VERSAO_IMUTAVEL",
                 "FICHA_CONCORRENCIA",
                 "PUBLICACAO_CONCORRENTE",
                 "VARIACAO_CODIGO_DUPLICADO",
                 "VARIACAO_PADRAO_DUPLICADA",
                 "VENDA_JA_FINALIZADA",
                 "VENDA_EM_PROCESSAMENTO",
                 "VENDA_JA_CANCELADA",
                 "ITEM_VENDA_JA_CANCELADO",
                 "PRECO_VARIACAO_ALTERADO",
                 "FICHA_PUBLICADA_ALTERADA",
                 "MODIFICADOR_CONFIGURACAO_ALTERADA",
                 "CONFIGURACAO_MULTISSABOR_ALTERADA",
                 "OPCOES_INCOMPATIVEIS",
                 "CANCELAMENTO_NAO_PERMITIDO",
                 "PAUSA_JA_ENCERRADA",
                 "REGRA_HORARIO_SOBREPOSTA"
                    -> HttpStatus.CONFLICT;

            case "VENDA_NAO_ENCONTRADA",
                 "ITEM_VENDA_NAO_ENCONTRADO",
                 "VARIACAO_NAO_ENCONTRADA",
                 "ITEM_CARDAPIO_NAO_ENCONTRADO",
                 "FICHA_TECNICA_NAO_ENCONTRADA",
                 "GRUPO_MODIFICADOR_NAO_ENCONTRADO",
                 "OPCAO_MODIFICADOR_NAO_ENCONTRADA",
                 "PAUSA_NAO_ENCONTRADA",
                 "REGRA_HORARIO_NAO_ENCONTRADA"
                    -> HttpStatus.NOT_FOUND;

            default -> HttpStatus.BAD_REQUEST;
        };
    }

    // =====================================================
    // COMPATIBILIDADE COM MENSAGENS LEGADAS
    // =====================================================

    /**
     * Algumas excecoes legadas usam apenas
     * mensagens em vez de codigos de negocio.
     *
     * Mantemos o reconhecimento existente.
     *
     * Novos services devem preferir
     * ErroNegocioException com codigo explicito.
     */
    private static String codigoPorMensagem(
            String mensagem,
            String codigoPadrao
    ) {
        if (mensagem == null) {
            return codigoPadrao;
        }

        String normalizada = Normalizer.normalize(
                mensagem,
                Normalizer.Form.NFD
        )
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT);

        if (normalizada.contains("unidades incompativeis")) {
            return "UNIDADE_INCOMPATIVEL";
        }

        if (normalizada.contains("movimento ja foi estornado")) {
            return "MOVIMENTO_JA_ESTORNADO";
        }

        if (normalizada.contains("ja confirmada")) {
            return "COMPRA_JA_CONFIRMADA";
        }

        if (normalizada.contains("imutavel")) {
            return "FICHA_VERSAO_IMUTAVEL";
        }

        return codigoPadrao;
    }

    // =====================================================
    // MENSAGEM PADRAO
    // =====================================================

    private static String mensagemOuPadrao(
            String mensagem,
            String padrao
    ) {
        return mensagem == null || mensagem.isBlank()
                ? padrao
                : mensagem;
    }

    // =====================================================
    // LOGS
    // =====================================================

    /**
     * Registra somente o codigo HTTP e
     * identificadores de diagnostico validados.
     *
     * Nao registra X-User-RU, CPF, token,
     * senha ou corpo da requisicao.
     */
    private static void registrarAviso(
            HttpServletRequest request,
            HttpStatusCode status,
            String codigo
    ) {
        LOGGER.warn(
                "Requisicao REST rejeitada. status={} codigo={} "
                        + "metodo={} uri={} correlationId={} requestId={}",
                status.value(),
                codigo,
                request.getMethod(),
                request.getRequestURI(),
                identificadorSeguro(request, "X-Correlation-Id"),
                identificadorSeguro(request, "X-Request-Id")
        );
    }

    /**
     * Aceita somente identificadores curtos,
     * com caracteres seguros para logs.
     *
     * O valor ainda nao e uma identidade
     * autenticada.
     */
    private static String identificadorSeguro(
            HttpServletRequest request,
            String nome
    ) {
        String valor = request.getHeader(nome);

        if (valor == null
                || !ID_SEGURO.matcher(valor).matches()) {
            return null;
        }

        return valor;
    }
}

