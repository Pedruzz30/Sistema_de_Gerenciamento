package app.tenancy;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Fail-closed boundary between the original StockOS Supabase schema and
 * the PostgreSQL/Flyway model used by the Java backend.
 *
 * The existing remote StockOS Staging project already contains a distinct
 * `stockos_private` model (empresas, filiais, membros, produtos). Running
 * Java Flyway migrations there would create a parallel and incompatible
 * set of tables, not migrate the existing SaaS model.
 *
 * This preflight performs read-only catalog inspection BEFORE flyway.migrate().
 * Only a deliberately reviewed schema reconciliation may remove this guard.
 */
final class SupabaseSchemaCompatibilityGuard {
    private static final String COLLISION_SQL = """
            select exists (
              select 1 from pg_catalog.pg_class c
              join pg_catalog.pg_namespace n on n.oid = c.relnamespace
              where n.nspname = 'stockos_private'
                and c.relkind in ('r', 'p')
                and c.relname in ('empresas', 'filiais', 'membros', 'produtos')
            )
            """;

    private SupabaseSchemaCompatibilityGuard() {}

    static void requireFlywayCompatibleDatabase(DataSource migrationDataSource) {
        if (migrationDataSource == null) {
            throw new IllegalStateException(
                    "Banco de migracao Flyway nao configurado; inicializacao bloqueada.");
        }
        final boolean schemaCollision;
        try (Connection connection = migrationDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(COLLISION_SQL);
             ResultSet results = statement.executeQuery()) {
            if (!results.next()) {
                throw new IllegalStateException(
                        "Nao foi possivel verificar compatibilidade do schema StockOS.");
            }
            schemaCollision = results.getBoolean(1);
        } catch (SQLException problem) {
            throw new IllegalStateException(
                    "Falha ao auditar o schema antes das migrations; inicializacao bloqueada.",
                    problem);
        }
        if (schemaCollision) {
            throw new IllegalStateException(
                    "Incompatibilidade de schema: stockos_private possui tabelas "
                    + "do SaaS original; nao executar migrations Flyway Java nesse banco. "
                    + "Realize reconciliacao e homologacao em banco isolado.");
        }
    }
}
