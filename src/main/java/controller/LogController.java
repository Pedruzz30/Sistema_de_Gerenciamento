package controller;

import model.LogAcao;
import repository.LogRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    private final LogRepository logRepository;

    public LogController(LogRepository logRepository) {
        this.logRepository = logRepository;
    }

    // GET /api/logs — returns all logs, latest first
    @GetMapping
    public ResponseEntity<List<LogResponse>> listarLogs() {
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
}
