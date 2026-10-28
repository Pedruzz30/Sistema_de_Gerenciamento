package service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.function.Function;

/**
 * Transaction-scoped, verified JWT -> PostgreSQL RLS identity bridge.
 *
 * IMPORTANT: the JDBC account MUST inherit only the stockos_runtime_reader
 * privileges. Never run through the database owner or postgres, which bypasses
 * RLS. PostgreSQL custom GUCs are NOT cryptographic authentication.
 *
 * This is intentionally not wired into existing write services: their runtime
 * privileges and tenant-scoped write policies are not implemented yet.
 */
@Service
public class SaasRlsReadExecutor {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public SaasRlsReadExecutor(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRED);
        this.transaction.setReadOnly(true);
        this.transaction.setTimeout(10);
    }

    public <T> T read(Function<JdbcTemplate, T> query) {
        if (query == null) throw new IllegalArgumentException("Consulta obrigatoria");

        // Refuse an ambient transaction rather than leaving actor_id set until
        // an outer, unrelated transaction commits. This method owns its scope.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Leitura RLS deve criar sua propria transacao, sem transacao externa");
        }

        UUID subject = verifiedSubject();
        return transaction.execute(status -> {
            // Parameter binding, never concatenate a token, header or UUID into SQL.
            // true = PostgreSQL SET LOCAL: reset at COMMIT and ROLLBACK.
            jdbc.queryForObject(
                    "select set_config('stockos.actor_id', ?, true)",
                    String.class, subject.toString());
            return query.apply(jdbc);
        });
    }

    static UUID verifiedSubject() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)
                || !token.isAuthenticated()
                || !token.getToken().getAudience().contains("authenticated")
                || !"authenticated".equals(token.getToken().getClaimAsString("role"))
                || Boolean.TRUE.equals(token.getToken().getClaimAsBoolean("is_anonymous"))) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "JWT Supabase autenticado obrigatorio");
        }
        try {
            return UUID.fromString(token.getToken().getSubject());
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "JWT sem identificador UUID valido");
        }
    }
}
