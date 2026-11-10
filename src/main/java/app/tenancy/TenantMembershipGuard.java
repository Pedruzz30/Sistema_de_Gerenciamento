package app.tenancy;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * First server-side tenant membership boundary. Use ONLY with a user RU derived
 * from verified authentication, never one taken from an HTTP header or body.
 *
 * A membership check alone is not row-level isolation: every subsequent
 * data query/mutation must filter by the SAME tenant within the transaction.
 */
@Service
public class TenantMembershipGuard {
    private static final String MEMBERSHIP_SQL = """
            select ue.papel
              from usuarios_estabelecimentos ue
              join estabelecimentos e on e.id = ue.estabelecimento_id
              join usuarios u on u.ru = ue.usuario_ru
             where ue.estabelecimento_id = ?
               and ue.usuario_ru = ?
               and ue.ativo = true
               and u.ativo = true
               and e.situacao = 'ATIVO'
            """;

    private final JdbcTemplate jdbc;

    public TenantMembershipGuard(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    public enum Papel { PROPRIETARIO, GERENTE, OPERADOR }

    /**
     * Only active, tenant-linked stores. Returns no global tenant list and
     * never derives RU from request JSON, query parameters or headers.
     */
    public record EstabelecimentoDisponivel(UUID id, String nome, Papel papel, int ruLocal) {}

    public List<EstabelecimentoDisponivel> listarEstabelecimentosAutorizados(long verifiedUsuarioRu) {
        if (verifiedUsuarioRu <= 0) throw denied();
        return jdbc.query("""
                select e.id, e.nome, ue.papel, ue.ru_local
                  from usuarios_estabelecimentos ue
                  join estabelecimentos e on e.id = ue.estabelecimento_id
                  join usuarios u on u.ru = ue.usuario_ru
                 where ue.usuario_ru = ?
                   and ue.ativo = true
                   and u.ativo = true
                   and e.situacao = 'ATIVO'
                 order by e.nome, e.id
                 limit 100
                """, (rs, row) -> new EstabelecimentoDisponivel(
                rs.getObject("id", UUID.class), rs.getString("nome"),
                Papel.valueOf(rs.getString("papel")), rs.getInt("ru_local")), verifiedUsuarioRu);
    }

    /**
     * Fail-closed for missing, revoked, inactive or cross-tenant membership.
     * No tenant name or existence is revealed to callers.
     */
    public Papel requireActiveMembership(UUID estabelecimentoId, long verifiedUsuarioRu) {
        if (estabelecimentoId == null || verifiedUsuarioRu <= 0) {
            throw denied();
        }
        final String papel;
        try {
            papel = jdbc.queryForObject(
                    MEMBERSHIP_SQL, String.class, estabelecimentoId, verifiedUsuarioRu);
        } catch (EmptyResultDataAccessException exception) {
            throw denied();
        }
        if (papel == null) {
            throw denied();
        }
        try {
            return Papel.valueOf(papel);
        } catch (IllegalArgumentException exception) {
            throw denied();
        }
    }

    /**
     * Tenant identifiers in client requests are mere selectors, never proof
     * of access. The verified user identity must be supplied by the server.
     * Re-run this check at the point of each privileged transaction; a cached
     * browser role is not authoritative.
     * Explicitly grant only the listed tenant roles; no implicit role elevation.
     */
    public void requireAnyRole(UUID estabelecimentoId, long verifiedUsuarioRu, Papel... allowed) {
        Papel current = requireActiveMembership(estabelecimentoId, verifiedUsuarioRu);
        if (allowed == null || allowed.length == 0
                || Arrays.stream(allowed).noneMatch(role -> role != null && role == current)) {
            throw denied();
        }
    }

    private static SecurityException denied() {
        return new SecurityException("Acesso negado ao estabelecimento.");
    }
}
