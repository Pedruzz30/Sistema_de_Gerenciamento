package repository;

import model.SessaoUsuario;

import java.time.LocalDateTime;
import java.util.Optional;

public interface SessaoUsuarioRepository {
    SessaoUsuario salvar(SessaoUsuario sessao);
    Optional<SessaoUsuario> buscarPorTokenHash(String tokenHash);
    void removerExpiradas(LocalDateTime agora);

    /** Encerra todas as sessões abertas da pessoa (outro computador incluso). */
    void encerrarDoUsuario(long ru, LocalDateTime agora);
}
