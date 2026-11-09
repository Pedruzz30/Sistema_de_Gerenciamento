package repository;

import model.SessaoUsuario;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class SessaoUsuarioRepositoryAdapter implements SessaoUsuarioRepository {

    private final JpaSessaoUsuarioRepository delegate;

    public SessaoUsuarioRepositoryAdapter(JpaSessaoUsuarioRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public SessaoUsuario salvar(SessaoUsuario sessao) {
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
    @org.springframework.transaction.annotation.Transactional
    public void encerrarDoUsuario(long ru, LocalDateTime agora) {
        delegate.encerrarDoUsuario(ru, agora);
    }
}
