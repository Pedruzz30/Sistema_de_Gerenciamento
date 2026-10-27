package service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Physical cash reconciliation only includes cash transactions.
 * Pix, credit and debit remain in their own financial totals and NEVER inflate
 * the expected money in the drawer. Shift close and receipt posting serialize
 * via a row-level lock on caixa_turnos.
 */
@Service
public class SaasCashService {
    private final JdbcTemplate jdbc;
    private final SaasTenantAccess tenant;

    public SaasCashService(JdbcTemplate jdbc, SaasTenantAccess tenant) {
        this.jdbc = jdbc;
        this.tenant = tenant;
    }

    public Map<String,Object> atual(UUID empresa,UUID filial) {
        tenant.requireMembership(empresa);
        tenant.requireFilial(empresa,filial);
        List<Map<String,Object>> open=jdbc.queryForList("""
            select id,status,valor_abertura,aberto_por,aberto_em
            from stockos_private.caixa_turnos
            where empresa_id=? and filial_id=? and status='ABERTO'
            """,empresa,filial);
        if(open.isEmpty()) return Map.of("status","SEM_CAIXA_ABERTO");
        Map<String,Object> result=new java.util.LinkedHashMap<>(open.get(0));
        result.putAll(resumo(empresa,filial,(UUID)result.get("id")));
        return result;
    }

    @Transactional
    public Map<String,Object> abrir(UUID empresa,UUID filial,BigDecimal valor) {
        tenant.requireMembership(empresa,"ATENDENTE","GERENTE","PROPRIETARIO");
        tenant.requireFilial(empresa,filial);
        validateAmount(valor,false);
        try {
            return jdbc.queryForMap("""
                insert into stockos_private.caixa_turnos
                  (empresa_id,filial_id,valor_abertura,aberto_por)
                values (?,?,?,?)
                returning id,status,valor_abertura,aberto_por,aberto_em
                """,empresa,filial,valor,tenant.authenticatedSubject());
        } catch(DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ja existe um caixa aberto nesta filial",ex);
        }
    }

    @Transactional
    public Map<String,Object> movimentar(UUID empresa,UUID filial,
                            String tipo,BigDecimal valor,String motivo,String chave) {
        tenant.requireMembership(empresa,"GERENTE","PROPRIETARIO");
        tenant.requireFilial(empresa,filial);
        if (!List.of("SANGRIA","SUPRIMENTO").contains(tipo))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tipo de movimento invalido");
        validateAmount(valor,true);
        if (motivo==null || motivo.trim().length()<5 || motivo.length()>240)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Informe um motivo (5-240 caracteres)");
        validateKey(chave);

        UUID turno=requireTurno(empresa,filial);
        List<Map<String,Object>> existing=jdbc.queryForList("""
            select id,tipo,valor,motivo,caixa_turno_id
            from stockos_private.caixa_movimentos
            where empresa_id=? and idempotency_key=?
            """,empresa,chave);
        if(!existing.isEmpty()){
            Map<String,Object> row=existing.get(0);
            if(!turno.equals(row.get("caixa_turno_id")) || !tipo.equals(row.get("tipo"))
                    || valor.compareTo((BigDecimal)row.get("valor"))!=0
                    || !motivo.trim().equals(row.get("motivo")))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Chave repetida com valores diferentes");
            return row;
        }
        Map<String,Object> before=resumo(empresa,filial,turno);
        BigDecimal expected=(BigDecimal)before.get("dinheiro_esperado");
        if("SANGRIA".equals(tipo) && expected.compareTo(valor)<0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Sangria maior que o dinheiro esperado no caixa");
        return jdbc.queryForMap("""
            insert into stockos_private.caixa_movimentos
              (empresa_id,filial_id,caixa_turno_id,tipo,valor,motivo,idempotency_key,registrado_por)
            values (?,?,?,?,?,?,?,?)
            returning id,tipo,valor,motivo,registrado_em
            """,empresa,filial,turno,tipo,valor,motivo.trim(),chave,tenant.authenticatedSubject());
    }

    @Transactional
    public Map<String,Object> fechar(UUID empresa,UUID filial,BigDecimal contado) {
        tenant.requireMembership(empresa,"GERENTE","PROPRIETARIO");
        tenant.requireFilial(empresa,filial);
        validateAmount(contado,false);
        UUID turno=requireTurno(empresa,filial);
        Map<String,Object> amounts=resumo(empresa,filial,turno);
        BigDecimal esperado=(BigDecimal)amounts.get("dinheiro_esperado");
        BigDecimal diferenca=contado.subtract(esperado);
        Map<String,Object> closed=jdbc.queryForMap("""
            update stockos_private.caixa_turnos
            set status='FECHADO',fechado_por=?,fechado_em=now(),
                valor_esperado=?,valor_contado=?,diferenca=?
            where id=? and empresa_id=? and filial_id=? and status='ABERTO'
            returning id,status,valor_abertura,valor_esperado,valor_contado,diferenca,fechado_em
            """,tenant.authenticatedSubject(),esperado,contado,diferenca,turno,empresa,filial);
        Map<String,Object> result=new java.util.LinkedHashMap<>(closed);
        result.putAll(amounts);
        return result;
    }

    public List<Map<String,Object>> historico(UUID empresa,UUID filial) {
        tenant.requireMembership(empresa,"GERENTE","PROPRIETARIO");
        tenant.requireFilial(empresa,filial);
        return jdbc.queryForList("""
            select id,status,valor_abertura,valor_esperado,valor_contado,diferenca,
                   aberto_em,fechado_em,aberto_por,fechado_por
            from stockos_private.caixa_turnos
            where empresa_id=? and filial_id=?
            order by aberto_em desc limit 100
            """,empresa,filial);
    }

    /**
     * Requires the only open cash shift under FOR UPDATE. Payment writes must
     * obtain the same lock; this protects closing balances during concurrency.
     */
    private UUID requireTurno(UUID empresa,UUID filial) {
        List<UUID> ids=jdbc.query("""
            select id from stockos_private.caixa_turnos
            where empresa_id=? and filial_id=? and status='ABERTO' for update
            """,(rs,i)->rs.getObject(1,UUID.class),empresa,filial);
        if(ids.isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Abra o caixa antes desta operacao");
        return ids.get(0);
    }

    private Map<String,Object> resumo(UUID empresa,UUID filial,UUID turno) {
        Map<String,Object> paid=jdbc.queryForMap("""
            select coalesce(sum(valor) filter (where modalidade='DINHEIRO'),0) as dinheiro,
                   coalesce(sum(valor) filter (where modalidade='PIX'),0) as pix,
                   coalesce(sum(valor) filter (where modalidade='DEBITO'),0) as debito,
                   coalesce(sum(valor) filter (where modalidade='CREDITO'),0) as credito
            from stockos_private.pagamentos p
            where p.empresa_id=? and p.filial_id=? and p.caixa_turno_id=?
              and not exists (
                select 1 from stockos_private.pagamento_estornos e
                where e.pagamento_id=p.id
              )
            """,empresa,filial,turno);
        Map<String,Object> adjustments=jdbc.queryForMap("""
            select coalesce(sum(valor) filter (where tipo='SUPRIMENTO'),0) as suprimentos,
                   coalesce(sum(valor) filter (where tipo='SANGRIA'),0) as sangrias
            from stockos_private.caixa_movimentos
            where empresa_id=? and filial_id=? and caixa_turno_id=?
            """,empresa,filial,turno);
        BigDecimal opening=jdbc.queryForObject("""
            select valor_abertura from stockos_private.caixa_turnos
            where id=? and empresa_id=? and filial_id=?
            """,BigDecimal.class,turno,empresa,filial);
        BigDecimal expected=opening
                .add((BigDecimal)paid.get("dinheiro"))
                .add((BigDecimal)adjustments.get("suprimentos"))
                .subtract((BigDecimal)adjustments.get("sangrias"));
        Map<String,Object> summary=new java.util.LinkedHashMap<>(paid);
        summary.putAll(adjustments);
        summary.put("dinheiro_esperado",expected);
        return summary;
    }

    private static void validateAmount(BigDecimal amount,boolean strictlyPositive) {
        if(amount==null || amount.scale()>2 || amount.precision()>18
                || (strictlyPositive ? amount.signum()<=0 : amount.signum()<0))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Valor monetario invalido");
    }

    private static void validateKey(String key) {
        if(key==null || key.isBlank() || key.length()>120)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Idempotency-Key obrigatoria");
    }
}
