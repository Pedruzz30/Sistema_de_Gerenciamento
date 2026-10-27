package controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import service.ErroNegocioException;

import java.util.Map;

/**
 * Centralised error-response format for all REST controllers.
 *
 * Controllers may still catch exceptions themselves when they need
 * fine-grained 404 handling or other specialised behaviour. This handler
 * only fires for exceptions that escape those local catches.
 *
 * Response format: { "erro": "message", "codigo": "CODIGO", "detalhes": {} }
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ErroResponse(String erro, String codigo, Map<String, Object> detalhes) {}

    @ExceptionHandler(ErroNegocioException.class)
    public ResponseEntity<ErroResponse> handleErroNegocio(ErroNegocioException ex, HttpServletRequest request) {
        registrarAviso(request, ex.getCodigo(), ex.getMessage());
        return ResponseEntity.status(statusErroNegocio(ex))
                .body(new ErroResponse(ex.getMessage(), ex.getCodigo(), ex.getDetalhes()));
    }

    /** Validation errors and invalid input → 400 Bad Request */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErroResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        String codigo = codigoPorMensagem(ex.getMessage());
        registrarAviso(request, codigo, ex.getMessage());
        return ResponseEntity.badRequest().body(new ErroResponse(ex.getMessage(), codigo, Map.of()));
    }

    /** Business rule violations → 400 Bad Request */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErroResponse> handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        String codigo = codigoPorMensagem(ex.getMessage());
        registrarAviso(request, codigo, ex.getMessage());
        HttpStatus status = "FICHA_VERSAO_IMUTAVEL".equals(codigo)
                ? HttpStatus.CONFLICT
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(new ErroResponse(ex.getMessage(), codigo, Map.of()));
    }

    /** Permission / security violations → 403 Forbidden */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErroResponse> handleSecurity(SecurityException ex, HttpServletRequest request) {
        registrarAviso(request, "ACESSO_NEGADO", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErroResponse(ex.getMessage(), "ACESSO_NEGADO", Map.of()));
    }

    /** Preserve intentional HTTP status codes from SaaS authorization and validation. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErroResponse> handleResponseStatus(
            ResponseStatusException ex, HttpServletRequest request) {
        String reason = ex.getReason() == null ? "Requisicao nao autorizada ou invalida." : ex.getReason();
        registrarAviso(request, "HTTP_" + ex.getStatusCode().value(), reason);
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErroResponse(reason, "HTTP_" + ex.getStatusCode().value(), Map.of()));
    }

    /** Unexpected exceptions → 500 Internal Server Error */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        LOGGER.error(
                "Erro inesperado ao processar requisicao REST. correlationId={} requestId={} operacao={} usuario={}",
                header(request, "X-Correlation-Id"),
                header(request, "X-Request-Id"),
                request.getMethod() + " " + request.getRequestURI(),
                header(request, "X-User-RU"),
                ex
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErroResponse("Erro interno do servidor.", "ERRO_INTERNO", Map.of()));
    }

    private static HttpStatus statusErroNegocio(ErroNegocioException ex) {
        return switch (ex.getCodigo()) {
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
                 "REGRA_HORARIO_SOBREPOSTA" -> HttpStatus.CONFLICT;
            case "VENDA_NAO_ENCONTRADA",
                 "ITEM_VENDA_NAO_ENCONTRADO",
                 "VARIACAO_NAO_ENCONTRADA",
                 "ITEM_CARDAPIO_NAO_ENCONTRADO",
                 "FICHA_TECNICA_NAO_ENCONTRADA",
                 "GRUPO_MODIFICADOR_NAO_ENCONTRADO",
                 "OPCAO_MODIFICADOR_NAO_ENCONTRADA",
                 "PAUSA_NAO_ENCONTRADA",
                 "REGRA_HORARIO_NAO_ENCONTRADA" -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.BAD_REQUEST;
        };
    }

    private static String codigoPorMensagem(String mensagem) {
        if (mensagem == null) return null;
        if (mensagem.contains("Unidades incompativeis") || mensagem.contains("Unidades incompatíveis")) {
            return "UNIDADE_INCOMPATIVEL";
        }
        if (mensagem.contains("ja confirmada")) return "COMPRA_JA_CONFIRMADA";
        if (mensagem.contains("Movimento ja foi estornado")) return "MOVIMENTO_JA_ESTORNADO";
        if (mensagem.contains("imutavel")) return "FICHA_VERSAO_IMUTAVEL";
        return null;
    }

    private static void registrarAviso(HttpServletRequest request, String codigo, String mensagem) {
        LOGGER.warn(
                "Erro de negocio REST. codigo={} mensagem={} correlationId={} requestId={} operacao={} usuario={}",
                codigo,
                mensagem,
                header(request, "X-Correlation-Id"),
                header(request, "X-Request-Id"),
                request.getMethod() + " " + request.getRequestURI(),
                header(request, "X-User-RU")
        );
    }

    private static String header(HttpServletRequest request, String nome) {
        String valor = request.getHeader(nome);
        return valor == null || valor.isBlank() ? null : valor;
    }
}
