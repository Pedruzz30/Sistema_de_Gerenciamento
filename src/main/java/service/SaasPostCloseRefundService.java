package service;

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
 * Manual full refunds of payments belonging to CLOSED tabs.
 * The original receipt, tab and closed cash-shift records are immutable.
 * This is NOT an integration with Pix, card acquirers or banking rails.
 */
@Service
public class SaasPostCloseRefundService {
    private final JdbcTemplate jdbc;
    private final SaasTenantAccess access;
    private final SaasCashService cash;

    public SaasPostCloseRefundService(JdbcTemplate jdbc, SaasTenantAccess access,
                                      SaasCashService cash) {
        this.jdbc=jdbc;this.access=access;this.cash=cash;
    }

    public List<Map<String,Object>> comandasFechadas(UUID empresa,UUID filial) {
        access.requireMembership(empresa,"GERENTE","PROPRIETARIO");
        access.requireFilial(empresa,filial);
        return jdbc.queryForList("""
            select c.id,c.numero,c.fechada_em,
                   coalesce((select sum(i.quantidade*i.preco_unitario_snapshot)
                       from stockos_private.comanda_itens i
                       where i.empresa_id=c.empresa_id and i.filial_id=c.filial_id
                         and i.comanda_id=c.id and i.cancelado_em is null),0) as total
            from stockos_private.comandas c
            where c.empresa_id=? and c.filial_id=? and c.status='FECHADA'
            order by c.fechada_em desc,c.id desc limit 50
            """,empresa,filial);
    }

    public List<Map<String,Object>> pagamentosFechados(UUID empresa,UUID filial,UUID comanda) {
        access.requireMembership(empresa,"GERENTE","PROPRIETARIO");
        access.requireFilial(empresa,filial);
        requireClosedComanda(empresa,filial,comanda,false);
        return jdbc.queryForList("""
            select p.id,p.modalidade,p.valor,p.registrado_em,
                   d.id as devolucao_id,d.registrado_em as devolvido_em,
                   d.comprovante_referencia,d.motivo as devolucao_motivo
            from stockos_private.pagamentos p
            left join stockos_private.devolucoes_pos_fechamento d
              on d.pagamento_id=p.id
            where p.empresa_id=? and p.filial_id=? and p.comanda_id=?
            order by p.registrado_em,p.id
            """,empresa,filial,comanda);
    }

    @Transactional
    public Map<String,Object> registrar(UUID empresa,UUID filial,UUID comanda,
           UUID pagamento,String motivo,String referencia,boolean confirmada,String chave) {
        access.requireMembership(empresa,"GERENTE","PROPRIETARIO");
        access.requireFilial(empresa,filial);
        if(chave==null || chave.isBlank() || chave.length()>120)bad("Idempotency-Key obrigatoria");
        if(motivo==null || motivo.trim().length()<8 || motivo.length()>240)bad("Motivo de 8 a 240 caracteres");
        if(referencia==null || referencia.trim().length()<6 || referencia.length()>160)
            bad("Referencia de comprovante de 6 a 160 caracteres");
        if(!confirmada)bad("Confirme que a devolucao externa ja foi realizada");
        String reason=motivo.trim(), ref=referencia.trim();

        // Same lock order: tab, open cash shift, payment.
        requireClosedComanda(empresa,filial,comanda,true);
        List<Map<String,Object>> replay=jdbc.queryForList("""
            select id,pagamento_id,comanda_id,motivo,comprovante_referencia,valor
            from stockos_private.devolucoes_pos_fechamento
            where empresa_id=? and idempotency_key=?
            """,empresa,chave);
        if(!replay.isEmpty()){
            Map<String,Object> old=replay.get(0);
            if(!pagamento.equals(old.get("pagamento_id")) || !comanda.equals(old.get("comanda_id"))
               || !reason.equals(old.get("motivo")) || !ref.equals(old.get("comprovante_referencia")))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Chave usada com dados diferentes");
            return old;
        }

        List<UUID> turns=jdbc.query("""
            select id from stockos_private.caixa_turnos
            where empresa_id=? and filial_id=? and status='ABERTO' for update
            """,(rs,i)->rs.getObject(1,UUID.class),empresa,filial);
        if(turns.isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Abra um novo turno para registrar a devolucao");
        UUID currentTurn=turns.get(0);

        List<Map<String,Object>> receipts=jdbc.queryForList("""
            select p.id,p.modalidade,p.valor,p.caixa_turno_id
            from stockos_private.pagamentos p
            where p.empresa_id=? and p.filial_id=? and p.comanda_id=? and p.id=?
            for update
            """,empresa,filial,comanda,pagamento);
        if(receipts.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Pagamento nao encontrado nesta empresa e filial");
        Map<String,Object> original=receipts.get(0);
        Integer reversed=jdbc.queryForObject("""
            select count(*) from stockos_private.pagamento_estornos
            where pagamento_id=?
            """,Integer.class,pagamento);
        if(reversed!=null && reversed>0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Pagamento ja estornado");

        Integer already=jdbc.queryForObject("""
            select count(*) from stockos_private.devolucoes_pos_fechamento
            where pagamento_id=?
            """,Integer.class,pagamento);
        if(already!=null && already>0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Devolucao ja registrada");

        BigDecimal amount=(BigDecimal)original.get("valor");
        if("DINHEIRO".equals(original.get("modalidade"))){
            BigDecimal expected=(BigDecimal)cash.atual(empresa,filial).get("dinheiro_esperado");
            if(expected.compareTo(amount)<0)
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Caixa atual nao cobre devolucao em dinheiro; concilie antes");
        }

        return jdbc.queryForMap("""
            insert into stockos_private.devolucoes_pos_fechamento
              (empresa_id,filial_id,comanda_id,pagamento_id,turno_processamento_id,
               modalidade,valor,motivo,comprovante_referencia,declarado_confirmado,
               idempotency_key,registrado_por)
            values (?,?,?,?,?,?,?,?,?,true,?,?)
            returning id,pagamento_id,comanda_id,valor,motivo,comprovante_referencia,registrado_em
            """,empresa,filial,comanda,pagamento,currentTurn,original.get("modalidade"),
                amount,reason,ref,chave,access.authenticatedSubject());
    }

    private void requireClosedComanda(UUID empresa,UUID filial,UUID comanda,boolean lock){
        String sql="""
            select status from stockos_private.comandas
            where empresa_id=? and filial_id=? and id=?
            """+(lock?" for update":"");
        List<String> statuses=jdbc.query(sql,(rs,i)->rs.getString(1),empresa,filial,comanda);
        if(statuses.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Comanda nao encontrada");
        if(!"FECHADA".equals(statuses.get(0)))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Comanda ainda nao foi fechada");
    }
    private static void bad(String msg){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,msg);}
}
