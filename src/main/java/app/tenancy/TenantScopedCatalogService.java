package app.tenancy;

import app.tenancy.TenantMembershipGuard.Papel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Security boundary for NEW tenant-aware catalog operations.
 *
 * This is deliberately not wired to legacy /api/produtos controllers.
 * A verified user RU must come from the server's authenticated identity.
 * The requested tenant UUID is a selector, not an authorization claim.
 *
 * Every SQL statement here filters by estabelecimento_id; checking membership
 * alone followed by a global findById would create an IDOR vulnerability.
 */
@Service
public class TenantScopedCatalogService {
    private final TenantMembershipGuard membership;
    private final JdbcTemplate jdbc;

    public TenantScopedCatalogService(TenantMembershipGuard membership, JdbcTemplate jdbc) {
        this.membership = Objects.requireNonNull(membership);
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    public record ProdutoResumo(int id, String nome) {}
    public record FornecedorResumo(long id, String nome) {}

    @Transactional(readOnly = true)
    public List<ProdutoResumo> listarProdutos(UUID tenantId, long verifiedRu) {
        membership.requireActiveMembership(tenantId, verifiedRu);
        return jdbc.query("""
                select id, nome from produtos
                where estabelecimento_id = ?
                order by id
                """,
                (rs, index) -> new ProdutoResumo(rs.getInt("id"), rs.getString("nome")),
                tenantId);
    }

    @Transactional(readOnly = true)
    public Optional<ProdutoResumo> buscarProduto(UUID tenantId, long verifiedRu, int produtoId) {
        membership.requireActiveMembership(tenantId, verifiedRu);
        if (produtoId <= 0) return Optional.empty();
        return jdbc.query("""
                select id, nome from produtos
                where id = ? and estabelecimento_id = ?
                """,
                (rs, index) -> new ProdutoResumo(rs.getInt("id"), rs.getString("nome")),
                produtoId, tenantId).stream().findFirst();
    }

    @Transactional(readOnly = true)
    public List<FornecedorResumo> listarFornecedores(UUID tenantId, long verifiedRu) {
        membership.requireActiveMembership(tenantId, verifiedRu);
        return jdbc.query("""
                select id, nome from fornecedores
                where estabelecimento_id = ?
                order by id
                """,
                (rs, index) -> new FornecedorResumo(rs.getLong("id"), rs.getString("nome")),
                tenantId);
    }

    @Transactional
    public int cadastrarProduto(UUID tenantId, long verifiedRu,
                               String nome, int quantidadeInicial,
                               int quantidadeMinima, BigDecimal precoUnitario) {
        membership.requireAnyRole(tenantId, verifiedRu, Papel.PROPRIETARIO, Papel.GERENTE);
        if (nome == null || nome.isBlank() || nome.trim().length() > 255
                || quantidadeInicial < 0 || quantidadeMinima < 0
                || precoUnitario == null || precoUnitario.signum() < 0
                || precoUnitario.scale() > 2) {
            throw new IllegalArgumentException("Dados de produto invalidos.");
        }
        return jdbc.queryForObject("""
                insert into produtos
                    (nome, quantidade_atual, quantidade_minima, preco_unitario,
                     estabelecimento_id, ativo, politica_falta)
                values (?, ?, ?, ?, ?, true, 'BLOQUEAR')
                returning id
                """, Integer.class,
                nome.trim(), quantidadeInicial, quantidadeMinima, precoUnitario, tenantId);
    }

    @Transactional
    public void renomearProduto(UUID tenantId, long verifiedRu,
                                int produtoId, String novoNome) {
        membership.requireAnyRole(tenantId, verifiedRu, Papel.PROPRIETARIO, Papel.GERENTE);
        if (produtoId <= 0 || novoNome == null || novoNome.isBlank()
                || novoNome.trim().length() > 255) {
            throw new IllegalArgumentException("Dados de produto invalidos.");
        }
        int changed = jdbc.update("""
                update produtos set nome = ?
                where id = ? and estabelecimento_id = ?
                """, novoNome.trim(), produtoId, tenantId);
        if (changed != 1) {
            // No distinction between an absent product and one from another tenant.
            throw new SecurityException("Produto nao encontrado ou acesso negado.");
        }
    }
}
