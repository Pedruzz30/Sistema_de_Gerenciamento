package controller;

import model.LogAcao;
import model.Permissao;
import model.Usuario;
import repository.LogRepository;
import repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    private final LogRepository logRepository;
    private final UsuarioRepository usuarioRepository;

    public LogController(LogRepository logRepository,
                         UsuarioRepository usuarioRepository) {
        this.logRepository = logRepository;
        this.usuarioRepository = usuarioRepository;
    }

    // GET /api/logs — returns all logs, latest first (requires VER_LOGS)
    @GetMapping
    public ResponseEntity<?> listarLogs(
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository);
        if (ator.getClasse() == null || !ator.getClasse().possuiPermissao(Permissao.VER_LOGS)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErroResponse("Você não tem permissão para visualizar os logs."));
        }
        List<LogResponse> logs = logRepository.listarTodos().stream()
                .sorted(Comparator.comparing(LogAcao::getDataHora).reversed())
                .map(LogResponse::from)
                .toList();
        return ResponseEntity.ok(logs);
    }

    // ── Records ───────────────────────────────────────────────────────────────

    public record LogResponse(
            long id,
            Long usuarioRu,
            String acao,
            String descricao,
            String dataHora
    ) {
        public static LogResponse from(LogAcao log) {
            return new LogResponse(
                    log.getId(),
                    log.getUsuarioRu(),
                    log.getAcao(),
                    log.getDescricao(),
                    log.getDataHora().toString()
            );
        }
    }

    public record ErroResponse(String erro) {}
}
