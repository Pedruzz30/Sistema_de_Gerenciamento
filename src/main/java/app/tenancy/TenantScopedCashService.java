package app.tenancy;

import app.tenancy.TenantMembershipGuard.Papel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * INTERNAL read-only financial projection.
 * No HTTP exposure: withdrawals, cash closing, settlement and refunds require
 * separate tenant-scoped transactional implementations and authorization.
 */
@Service
public class TenantScopedCashService {
    private final TenantMembershipGuard guard;
    private final JdbcTemplate jdbc;

    public TenantScopedCashService(TenantMembershipGuard guard, JdbcTemplate jdbc) {
        this.guard = Objects.requireNonNull(guard);
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    public record CaixaResumo(long id, int numeroCaixa, String status, BigDecimal saldoAtual) {}
    public record MovimentoResumo(long id, long caixaId, String tipo,
                                  BigDecimal valor, LocalDateTime dataHora) {}

    @Transactional(readOnly = true)
    public List<CaixaResumo> listarCaixas(UUID tenant, long verifiedRu) {
        guard.requireAnyRole(tenant, verifiedRu, Papel.PROPRIETARIO, Papel.GERENTE);
        return jdbc.query("""
                select id, numero_caixa, status, saldo_atual
                  from caixas
                 where estabelecimento_id = ?
                 order by id
                 limit 100
                """, (rs, row) -> new CaixaResumo(rs.getLong("id"),
                    rs.getInt("numero_caixa"), rs.getString("status"),
                    rs.getBigDecimal("saldo_atual")), tenant);
    }

    @Transactional(readOnly = true)
    public List<MovimentoResumo> listarMovimentos(UUID tenant, long verifiedRu) {
        guard.requireAnyRole(tenant, verifiedRu, Papel.PROPRIETARIO, Papel.GERENTE);
        return jdbc.query("""
                select m.id, m.caixa_id, m.tipo, m.valor, m.data_hora
                  from movimentacoes_caixa m
                  join caixas c on c.id = m.caixa_id
                     and c.estabelecimento_id = m.estabelecimento_id
                 where m.estabelecimento_id = ?
                 order by m.data_hora desc, m.id desc
                 limit 100
                """, (rs, row) -> new MovimentoResumo(rs.getLong("id"),
                    rs.getLong("caixa_id"), rs.getString("tipo"),
                    rs.getBigDecimal("valor"),
                    rs.getTimestamp("data_hora").toLocalDateTime()), tenant);
    }
}
