package app.tenancy;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Internal, tenant-scoped read model for stock balances and movements.
 *
 * No HTTP endpoint is exposed here. Caller RU must come from a verified
 * server-side identity. All joins and predicates are scoped to the same tenant,
 * not merely a global product identifier or legacy filial_id.
 */
@Service
public class TenantScopedStockService {
    private final TenantMembershipGuard guard;
    private final JdbcTemplate jdbc;

    public TenantScopedStockService(TenantMembershipGuard guard, JdbcTemplate jdbc) {
        this.guard = Objects.requireNonNull(guard);
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    public record SaldoResumo(long id, int produtoId, String nomeProduto,
                             BigDecimal quantidade, BigDecimal reservada) {}
    public record MovimentoResumo(long id, int produtoId, String tipo, BigDecimal quantidade) {}

    @Transactional(readOnly = true)
    public List<SaldoResumo> listarSaldos(UUID tenantId, long verifiedRu) {
        guard.requireActiveMembership(tenantId, verifiedRu);
        return jdbc.query("""
                select e.id, e.produto_id, p.nome, e.quantidade_atual,
                       e.quantidade_reservada
                  from estoques_produto e
                  join produtos p
                    on p.id = e.produto_id
                   and p.estabelecimento_id = e.estabelecimento_id
                 where e.estabelecimento_id = ?
                 order by e.id
                 limit 200
                """,
                (rs, row) -> new SaldoResumo(
                        rs.getLong("id"), rs.getInt("produto_id"),
                        rs.getString("nome"), rs.getBigDecimal("quantidade_atual"),
                        rs.getBigDecimal("quantidade_reservada")),
                tenantId);
    }

    @Transactional(readOnly = true)
    public List<MovimentoResumo> listarMovimentos(UUID tenantId, long verifiedRu) {
        guard.requireActiveMembership(tenantId, verifiedRu);
        return jdbc.query("""
                select m.id, m.produto_id, m.tipo, m.quantidade_base
                  from movimentos_estoque_decimal m
                  join produtos p
                    on p.id = m.produto_id
                   and p.estabelecimento_id = m.estabelecimento_id
                 where m.estabelecimento_id = ?
                 order by m.criado_em desc, m.id desc
                 limit 100
                """,
                (rs, row) -> new MovimentoResumo(
                        rs.getLong("id"), rs.getInt("produto_id"),
                        rs.getString("tipo"), rs.getBigDecimal("quantidade_base")),
                tenantId);
    }
}
