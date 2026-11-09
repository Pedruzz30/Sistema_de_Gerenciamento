package repository;

import model.SessaoUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface JpaSessaoUsuarioRepository extends JpaRepository<SessaoUsuario, Long> {

    Optional<SessaoUsuario> findByTokenHash(String tokenHash);

    @Modifying
    @Query("delete from SessaoUsuario s where s.expiraEm <= :agora")
    void removerExpiradas(@Param("agora") LocalDateTime agora);

    @Modifying
    @Query("update SessaoUsuario s set s.expiraEm = :agora where s.usuario.ru = :ru and s.expiraEm > :agora")
    void encerrarDoUsuario(@Param("ru") long ru, @Param("agora") LocalDateTime agora);
}
