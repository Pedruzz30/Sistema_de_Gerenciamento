package app.tenancy;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Production-only guard: no application access with the local Docker
 * superuser or the schema/migration owner. No remote DB is touched by this
 * code until the StockOS service is explicitly configured and started.
 */
@Component
@ConditionalOnProperty(name = "stockos.auth.mode", havingValue = "supabase")
public class SupabaseDatabasePrivilegeGuard implements ApplicationRunner {
    private static final String INSPECT_ROLE_SQL = """
            select r.rolsuper, r.rolcreatedb, r.rolcreaterole, r.rolbypassrls,
                   has_database_privilege(current_user, current_database(), 'CREATE'),
                   has_schema_privilege(current_user, current_schema(), 'CREATE'),
                   exists (
                       select 1 from pg_class c
                       join pg_namespace n on n.oid = c.relnamespace
                       where n.nspname = current_schema()
                         and c.relkind in ('r', 'p')
                         and c.relname in ('usuarios', 'estabelecimentos',
                                          'produtos', 'vendas', 'movimentos_estoque_decimal')
                         and pg_has_role(current_user, c.relowner, 'USAGE')
                   )
              from pg_roles r where r.rolname = current_user
            """;

    private final JdbcTemplate jdbc;

    public SupabaseDatabasePrivilegeGuard(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public void run(ApplicationArguments args) {
        Boolean insecure = jdbc.query(INSPECT_ROLE_SQL, rs -> {
            if (!rs.next()) {
                throw new IllegalStateException("Usuario JDBC da aplicacao nao encontrado.");
            }
            return rs.getBoolean(1) || rs.getBoolean(2) || rs.getBoolean(3)
                    || rs.getBoolean(4) || rs.getBoolean(5) || rs.getBoolean(6)
                    || rs.getBoolean(7);
        });
        if (!Boolean.FALSE.equals(insecure)) {
            throw new IllegalStateException(
                    "Modo Supabase exige usuario JDBC sem poderes administrativos, DDL ou ownership.");
        }
    }
}

/**
 * Flyway runs *before* ApplicationRunner. Refuse to migrate when the
 * migration identity is absent or equal to the runtime identity.
 * Spring Boot configures this role through SPRING_FLYWAY_USER and
 * SPRING_FLYWAY_PASSWORD. Do not use STOCKOS local superuser for runtime.
 */
@Configuration(proxyBeanMethods = false)
class SupabaseFlywaySeparation {
    @Bean
    @ConditionalOnProperty(name = "stockos.auth.mode", havingValue = "supabase")
    @ConditionalOnProperty(name = "stockos.migrations.mode", havingValue = "embedded", matchIfMissing = true)
    org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy
    requireSeparateMigrationIdentity(
            @Value("${spring.flyway.user:}") String migrator,
            @Value("${spring.flyway.password:}") String migratorPassword,
            @Value("${spring.datasource.username:}") String applicationUser) {
        if (migrator == null || migrator.isBlank() || migratorPassword == null
                || migratorPassword.isBlank() || applicationUser == null
                || applicationUser.isBlank() || migrator.trim().equalsIgnoreCase(applicationUser.trim())) {
            throw new IllegalStateException(
                    "Modo Supabase exige SPRING_FLYWAY_USER/PASSWORD distintos do DB_USERNAME da aplicacao.");
        }
        return flyway -> {
            // This check runs before the FIRST Flyway DDL, using Flyway's
            // configured migration datasource (never the runtime datasource).
            SupabaseSchemaCompatibilityGuard.requireFlywayCompatibleDatabase(
                    flyway.getConfiguration().getDataSource());
            flyway.migrate();
        };
    }
}
