package controller;

import model.LogAcao;
import model.Permissao;
import repository.LogRepository;
import repository.UsuarioRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Controller REST para consulta da trilha de auditoria do sistema.
 *
 * <h2>Endpoints</h2>
 * <pre>
 *   GET /api/logs              — lista todos os logs (mais recente primeiro)
 *   GET /api/logs?acao=X       — filtra por tipo de ação (ex: CAIXA_ABERTO)
 *   GET /api/logs?ru=N         — filtra por RU do usuário responsável
 *   GET /api/logs?limit=N      — limita o número de registros retornados
 * </pre>
 *
 * <h2>Autorização</h2>
 * Requer a permissão {@link Permissao#VER_LOGS}. Qualquer usuário sem essa
 * permissão recebe {@code 403 Forbidden} independente do header enviado.
 *
 * <h2>Trilha de auditoria</h2>
 * Os logs são imutáveis — nenhum endpoint de escrita é exposto aqui.
 * A gravação ocorre exclusivamente via {@link repository.LogRepository#salvar}
 * chamado pelos services após cada operação relevante.
 */
@RestController
@RequestMapping("/api/logs")
public class LogController {

    // ── Constantes ────────────────────────────────────────────────────────────

    private static final String          FORMATO_DATA = "dd/MM/yyyy HH:mm:ss";
    private static final DateTimeFormatter FORMATTER  =
            DateTimeFormatter.ofPattern(FORMATO_DATA);

    /** Limite padrão de registros quando {@code limit} não é fornecido. */
    private static final int LIMITE_PADRAO = 500;

    /** Limite máximo para evitar respostas excessivamente grandes. */
    private static final int LIMITE_MAXIMO = 2000;

    // ── Dependências ──────────────────────────────────────────────────────────

    private final LogRepository     logRepository;
    private final UsuarioRepository usuarioRepository;

    public LogController(LogRepository logRepository,
                         UsuarioRepository usuarioRepository) {
        this.logRepository     = logRepository;
        this.usuarioRepository = usuarioRepository;
    }

    // ── GET /api/logs ─────────────────────────────────────────────────────────

    /**
     * Lista os registros de auditoria com ordenação, filtragem e paginação leve.
     *
     * <p>Todos os parâmetros são opcionais e combinam-se por interseção
     * (AND lógico): {@code ?acao=LOGIN_SUCESSO&ru=3} retorna apenas os
     * logins bem-sucedidos do usuário de RU 3.
     *
     * @param ruHeader header {@code X-User-RU} do usuário requisitando os logs
     * @param acao     filtra por tipo de ação exato (case-insensitive); ex: {@code CAIXA_ABERTO}
     * @param ru       filtra por RU do usuário responsável pela ação
     * @param limit    número máximo de registros; padrão {@value #LIMITE_PADRAO},
     *                 máximo {@value #LIMITE_MAXIMO}
     * @return lista de logs mais recentes primeiro, com os filtros aplicados
     */
    @GetMapping
    public ResponseEntity<?> listarLogs(
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader,
            @RequestParam(required = false) String acao,
            @RequestParam(required = false) Long   ru,
            @RequestParam(required = false) Integer limit) {

        // ── Autorização ───────────────────────────────────────────────────
        ControllerUtils.resolveUserAndRequirePermission(
                ruHeader,
                usuarioRepository,
                Permissao.VER_LOGS,
                "Voce nao tem permissao para visualizar os logs."
        );

        // ── Limite sanitizado ─────────────────────────────────────────────
        int tamanho = Optional.ofNullable(limit)
                .filter(l -> l > 0)
                .map(l -> Math.min(l, LIMITE_MAXIMO))
                .orElse(LIMITE_PADRAO);

        // ── Pipeline: ordena → filtra → limita → projeta ─────────────────
        List<LogResponse> logs = logRepository.listarTodos().stream()
                .sorted(Comparator.comparing(LogAcao::getDataHora).reversed())
                .filter(log -> acao == null || log.getAcao().equalsIgnoreCase(acao))
                .filter(log -> ru   == null || (log.getUsuarioRu() != null
                        && log.getUsuarioRu().equals(ru)))
                .limit(tamanho)
                .map(LogResponse::from)
                .toList();

        return ResponseEntity.ok(logs);
    }

    // ── Records ───────────────────────────────────────────────────────────────

    /**
     * Representação pública de um {@link LogAcao}.
     *
     * <p>{@code dataHora} é serializado no formato {@code dd/MM/yyyy HH:mm:ss}
     * ao invés do ISO 8601 bruto — mais legível para o frontend e para logs
     * de suporte, sem exigir formatação no cliente.
     *
     * <p>{@code possuiUsuario} indica se a ação foi executada por um usuário
     * identificado ({@code true}) ou pelo sistema automaticamente ({@code false}).
     * O frontend usa esse campo para exibir "sistema" em vez de "RU: null".
     */
    public record LogResponse(
            long    id,
            Long    usuarioRu,
            boolean possuiUsuario,
            String  acao,
            String  descricao,
            String  dataHora
    ) {
        public static LogResponse from(LogAcao log) {
            return new LogResponse(
                    log.getId(),
                    log.getUsuarioRu(),
                    log.possuiUsuario(),
                    log.getAcao(),
                    log.getDescricao().isEmpty() ? null : log.getDescricao(),
                    log.getDataHora().format(FORMATTER)
            );
        }
    }

    /** Resposta de erro padronizada — consistente com {@code GlobalExceptionHandler}. */
    public record ErroResponse(String erro) {}
}
