package service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bloqueia temporariamente depois de várias tentativas erradas. Essencial para
 * o PIN: com 4 dígitos, sem limite, ele seria descoberto em segundos.
 *
 * <p>Fica em memória: reiniciar o sistema zera os contadores, o que é aceitável
 * para uma loja com um servidor só.
 */
public class LimiteTentativas {

    public static final int MAX_FALHAS = 5;
    public static final Duration BLOQUEIO = Duration.ofMinutes(5);

    private record Estado(int falhas, Instant bloqueadoAte) {}

    private final Map<String, Estado> estados = new ConcurrentHashMap<>();
    private final Clock relogio;

    public LimiteTentativas() {
        this(Clock.systemUTC());
    }

    public LimiteTentativas(Clock relogio) {
        this.relogio = relogio;
    }

    /** Recusa a tentativa se a chave estiver bloqueada. */
    public void verificar(String chave) {
        Estado estado = estados.get(chave);
        if (estado == null || estado.bloqueadoAte() == null) {
            return;
        }
        if (relogio.instant().isBefore(estado.bloqueadoAte())) {
            throw new ErroNegocioException("MUITAS_TENTATIVAS",
                    "Muitas tentativas erradas. Aguarde " + BLOQUEIO.toMinutes() + " minutos e tente de novo.");
        }
        estados.remove(chave);
    }

    public void registrarFalha(String chave) {
        estados.compute(chave, (k, atual) -> {
            int falhas = (atual == null ? 0 : atual.falhas()) + 1;
            Instant bloqueio = falhas >= MAX_FALHAS ? relogio.instant().plus(BLOQUEIO) : null;
            return new Estado(falhas, bloqueio);
        });
    }

    public void registrarSucesso(String chave) {
        estados.remove(chave);
    }

    /** Desbloqueia na hora, quando o gerente redefine a senha. */
    public void liberar(String chave) {
        estados.remove(chave);
    }
}
