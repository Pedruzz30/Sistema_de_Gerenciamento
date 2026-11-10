package repository;

import model.SessaoUsuario;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/** Implementação em memória para testes de unidade. */
public class InMemorySessaoUsuarioRepository implements SessaoUsuarioRepository {

    private final List<SessaoUsuario> sessoes = new ArrayList<>();
    private final AtomicLong sequencia = new AtomicLong(1);

    @Override
    public synchronized SessaoUsuario salvar(SessaoUsuario sessao) {
        if (sessao.getId() == 0) {
            sessao.atribuirIdGerado(sequencia.getAndIncrement());
            sessoes.add(sessao);
        }
        return sessao;
    }

    @Override
    public synchronized Optional<SessaoUsuario> buscarPorTokenHash(String tokenHash) {
        return sessoes.stream().filter(s -> s.getTokenHash().equals(tokenHash)).findFirst();
    }

    @Override
    public synchronized void removerExpiradas(LocalDateTime agora) {
        sessoes.removeIf(s -> s.expirada(agora));
    }

    @Override
    public synchronized void encerrarDoUsuario(long ru, LocalDateTime agora) {
        sessoes.stream()
                .filter(s -> s.getUsuario().getRu() == ru && !s.expirada(agora))
                .forEach(s -> s.encerrar(agora));
    }
}
