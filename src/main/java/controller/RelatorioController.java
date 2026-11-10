package controller;

import model.Permissao;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import repository.UsuarioRepository;
import service.RelatorioVendasService;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/** Relatório do dia e do mês. Só para quem cuida do financeiro. */
@RestController
@RequestMapping("/api/relatorios")
public class RelatorioController {

    private final RelatorioVendasService relatorioService;
    private final UsuarioRepository usuarioRepository;

    public RelatorioController(RelatorioVendasService relatorioService, UsuarioRepository usuarioRepository) {
        this.relatorioService = relatorioService;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/dia")
    public ResponseEntity<RelatorioVendasService.Dia> dia(
            @RequestParam(required = false) String data,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        exigirFinanceiro(ruHeader);
        return ResponseEntity.ok(relatorioService.dia(data == null || data.isBlank() ? LocalDate.now() : lerData(data)));
    }

    @GetMapping("/mes")
    public ResponseEntity<RelatorioVendasService.Mes> mes(
            @RequestParam(required = false) String mes,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        exigirFinanceiro(ruHeader);
        YearMonth periodo;
        try {
            periodo = mes == null || mes.isBlank() ? YearMonth.now() : YearMonth.parse(mes);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Mes invalido. Use o formato 2026-11.");
        }
        return ResponseEntity.ok(relatorioService.mes(periodo));
    }

    /** Curva ABC do período (padrão: últimos 30 dias), por faturamento ou por margem. */
    @GetMapping("/abc")
    public ResponseEntity<RelatorioVendasService.Abc> abc(
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim,
            @RequestParam(required = false, defaultValue = "valor") String criterio,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        exigirFinanceiro(ruHeader);
        LocalDate ate = fim == null || fim.isBlank() ? LocalDate.now() : lerData(fim);
        LocalDate de = inicio == null || inicio.isBlank() ? ate.minusDays(29) : lerData(inicio);
        return ResponseEntity.ok(relatorioService.abc(de, ate, criterio));
    }

    private static LocalDate lerData(String data) {
        try {
            return LocalDate.parse(data);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Data invalida. Use o formato 2026-11-10.");
        }
    }

    private void exigirFinanceiro(String ruHeader) {
        ControllerUtils.resolveUserAndRequirePermission(
                ruHeader, usuarioRepository, Permissao.VER_FINANCAS,
                "Voce nao tem permissao para ver os relatorios.");
    }
}
