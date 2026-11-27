package repository;

import model.SessaoUsuario;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

@Repository
public class SessaoUsuarioRepositoryAdapter
        implements SessaoUsuarioRepository {

    private final JpaSessaoUsuarioRepository delegate;

    public SessaoUsuarioRepositoryAdapter(
            JpaSessaoUsuarioRepository delegate) {

        this.delegate = Objects.requireNonNull(
                delegate, "Repositorio JPA de sessao de usuario e obrigatorio.");
    }

    @Override
    public SessaoUsuario salvar(SessaoUsuario sessao) {
        if (sessao == null) {
            throw new IllegalArgumentException(
                    "Sessao de usuario e obrigatoria.");
        }

        return delegate.save(sessao);
    }

    @Override
    public Optional<SessaoUsuario> buscarPorTokenHash(String tokenHash) {
        return delegate.findByTokenHash(tokenHash);
    }

    @Override
    public void removerExpiradas(LocalDateTime agora) {
        delegate.removerExpiradas(agora);
    }

    @Override
    @Transactional
    public void encerrarDoUsuario(long ru, LocalDateTime agora) {
        delegate.encerrarDoUsuario(ru, agora);
    }
}
