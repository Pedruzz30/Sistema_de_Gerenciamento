package service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Operational posted money movements by their actual posting date.
 * Not fiscal revenue, bank settlement, NF-e or accrual-based accounting.
 */
@Service
public class SaasReportsService {
    private final JdbcTemplate jdbc;
    private final SaasTenantAccess tenant;
    public SaasReportsService(JdbcTemplate jdbc,SaasTenantAccess tenant){
        this.jdbc=jdbc;this.tenant=tenant;
    }

    public Map<String,Object> financeiro(UUID empresa,UUID filial,
             OffsetDateTime inicio,OffsetDateTime fim) {
        tenant.requireMembership(empresa,"GERENTE","PROPRIETARIO");
        tenant.requireFilial(empresa,filial);
        if(inicio==null||fim==null||!inicio.isBefore(fim)
             ||ChronoUnit.DAYS.between(inicio,fim)>366)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Informe periodo valido de ate 366 dias");
        // Payments already recorded, including open tabs. Reversals and refunds
        // are reported in the period when posted, even if receipt was earlier.
        List<Map<String,Object>> receipts=jdbc.queryForList("""
            select modalidade,coalesce(sum(valor),0) as valor,count(*) as operacoes
            from stockos_private.pagamentos
            where empresa_id=? and filial_id=? and registrado_em>=? and registrado_em<?
            group by modalidade order by modalidade
            """,empresa,filial,inicio,fim);
        List<Map<String,Object>> reversals=jdbc.queryForList("""
            select modalidade,coalesce(sum(valor),0) as valor,count(*) as operacoes
            from stockos_private.pagamento_estornos
            where empresa_id=? and filial_id=? and registrado_em>=? and registrado_em<?
            group by modalidade order by modalidade
            """,empresa,filial,inicio,fim);
        List<Map<String,Object>> refunds=jdbc.queryForList("""
            select modalidade,coalesce(sum(valor),0) as valor,count(*) as operacoes
            from stockos_private.devolucoes_pos_fechamento
            where empresa_id=? and filial_id=? and registrado_em>=? and registrado_em<?
            group by modalidade order by modalidade
            """,empresa,filial,inicio,fim);
        BigDecimal entered=sum(receipts),reversed=sum(reversals),refunded=sum(refunds);
        return Map.of("inicio",inicio,"fim_exclusivo",fim,
            "recebimentos",receipts,"estornos",reversals,"devolucoes",refunds,
            "total_recebido",entered,"total_estornado",reversed,
            "total_devolvido",refunded,
            "fluxo_liquido_registrado",entered.subtract(reversed).subtract(refunded));
    }

    public List<Map<String,Object>> estoqueBaixo(UUID empresa,UUID filial,int limite,int max) {
        tenant.requireMembership(empresa,"GERENTE","PROPRIETARIO");
        tenant.requireFilial(empresa,filial);
        if(limite<0||limite>1000000||max<1||max>200)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Filtro de estoque invalido");
        return jdbc.queryForList("""
            select id,codigo,nome,estoque_atual,preco_unitario
            from stockos_private.produtos
            where empresa_id=? and filial_id=? and ativo=true and estoque_atual<=?
            order by estoque_atual asc,nome asc limit ?
            """,empresa,filial,limite,max);
    }

    public List<Map<String,Object>> produtosMaisEntregues(UUID empresa,UUID filial,
            OffsetDateTime inicio,OffsetDateTime fim,int max) {
        tenant.requireMembership(empresa,"GERENTE","PROPRIETARIO");
        tenant.requireFilial(empresa,filial);
        if(inicio==null||fim==null||!inicio.isBefore(fim)
          ||ChronoUnit.DAYS.between(inicio,fim)>366||max<1||max>100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Periodo ou limite invalido");
        return jdbc.queryForList("""
            select produto_id,nome_snapshot as nome,
                 sum(quantidade) as unidades,
                 sum(quantidade*preco_unitario_snapshot) as valor_bruto_itens
            from stockos_private.comanda_itens
            where empresa_id=? and filial_id=? and cancelado_em is null
              and criado_em>=? and criado_em<?
            group by produto_id,nome_snapshot
            order by unidades desc,nome_snapshot asc limit ?
            """,empresa,filial,inicio,fim,max);
    }
    private static BigDecimal sum(List<Map<String,Object>> values){
        BigDecimal total=BigDecimal.ZERO;
        for(Map<String,Object> row:values)total=total.add((BigDecimal)row.get("valor"));
        return total;
    }
}
