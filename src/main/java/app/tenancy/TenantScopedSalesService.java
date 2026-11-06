package app.tenancy;

import app.tenancy.TenantMembershipGuard.Papel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only scoped sales queries for the first secure financial boundaries.
 * No public HTTP routes until Supabase JWT, role and transactional tenant
 * coverage for refunds and payments have been integrated and E2E tested.
 */
@Service
public class TenantScopedSalesService {
    private final TenantMembershipGuard membership;
    private final JdbcTemplate jdbc;

    public TenantScopedSalesService(TenantMembershipGuard membership, JdbcTemplate jdbc) {
        this.membership = Objects.requireNonNull(membership);
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    public record VendaResumo(long id, String numero, String status,
                              BigDecimal valorTotal, LocalDateTime criadaEm) {}

    @Transactional(readOnly = true)
    public List<VendaResumo> listarVendas(UUID tenantId, long verifiedRu) {
        membership.requireAnyRole(tenantId, verifiedRu,
                Papel.PROPRIETARIO, Papel.GERENTE);
        return jdbc.query("""
                select v.id, v.numero, v.status, v.valor_total, v.criada_em
                  from vendas v
                 where v.estabelecimento_id = ?
                 order by v.criada_em desc, v.id desc
                 limit 100
                """,
                (rs, row) -> new VendaResumo(
                        rs.getLong("id"), rs.getString("numero"),
                        rs.getString("status"), rs.getBigDecimal("valor_total"),
                        rs.getTimestamp("criada_em").toLocalDateTime()),
                tenantId);
    }

    @Transactional(readOnly = true)
    public Optional<VendaResumo> buscarVenda(UUID tenantId, long verifiedRu, long vendaId) {
        membership.requireAnyRole(tenantId, verifiedRu,
                Papel.PROPRIETARIO, Papel.GERENTE);
        if (vendaId <= 0) return Optional.empty();
        return jdbc.query("""
                select v.id, v.numero, v.status, v.valor_total, v.criada_em
                  from vendas v
                 where v.id = ? and v.estabelecimento_id = ?
                """,
                (rs, row) -> new VendaResumo(
                        rs.getLong("id"), rs.getString("numero"),
                        rs.getString("status"), rs.getBigDecimal("valor_total"),
                        rs.getTimestamp("criada_em").toLocalDateTime()),
                vendaId, tenantId).stream().findFirst();
    }
}
