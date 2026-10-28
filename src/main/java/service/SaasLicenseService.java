package service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-enforced commercial access. All business operations must pass a
 * membership check AND this check before touching a tenant's business data.
 * Renewals remain manual until a trusted billing integration exists.
 */
@Service
public class SaasLicenseService {
    private final JdbcTemplate jdbc;

    public SaasLicenseService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void requireActive(UUID companyId) {
        Boolean active = jdbc.queryForObject("""
            select exists(
                select 1 from stockos_private.licencas l
                join stockos_private.planos p on p.id=l.plano_id
                where l.empresa_id=? and l.status in ('TRIAL','ATIVA')
                  and l.vigencia_inicio<=now() and l.vigencia_fim>now()
                  and p.ativo=true and p.inclui_comandas=true
            )
            """, Boolean.class, companyId);
        if (!Boolean.TRUE.equals(active)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Licenca inativa, expirada ou sem acesso ao modulo. Consulte a administracao StockOS.");
        }
    }

    /**
     * This is intentionally read-only. Caller MUST first verify tenant membership
     * without license gating, so an expired customer can still view their own status.
     */
    public Map<String,Object> summary(UUID companyId) {
        List<Map<String,Object>> rows = jdbc.queryForList("""
            select l.status, p.codigo as plano, p.nome as nome_plano,
                   l.vigencia_inicio, l.vigencia_fim, l.valor_mensal_snapshot,
                   (l.status in ('TRIAL','ATIVA')
                     and l.vigencia_inicio<=now() and l.vigencia_fim>now()
                     and p.ativo=true and p.inclui_comandas=true) as acesso_liberado
              from stockos_private.licencas l
              join stockos_private.planos p on p.id=l.plano_id
             where l.empresa_id=?
            """, companyId);
        return rows.isEmpty()
                ? Map.of("status","SEM_LICENCA","acesso_liberado",false)
                : rows.get(0);
    }
}
