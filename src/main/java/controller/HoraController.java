package controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Hora do sistema, no fuso da loja (stockos.timezone). O painel mostra o relógio por ela, e
 * não pelo fuso configurado no Windows.
 */
@RestController
public class HoraController {

    private final Clock clock;

    public HoraController(Clock clock) {
        this.clock = clock;
    }

    @GetMapping("/api/sistema/hora")
    public ResponseEntity<HoraResponse> hora() {
        return ResponseEntity.ok(new HoraResponse(clock.millis(), clock.getZone().getId(),
                clock.getZone().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("pt-BR"))));
    }

    public record HoraResponse(long agora, String fuso, String nomeFuso) {}
}
