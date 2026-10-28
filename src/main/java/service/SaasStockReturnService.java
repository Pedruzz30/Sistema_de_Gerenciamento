package service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Records PHYSICAL merchandise received from CLOSED tabs.
 * Restock is explicit and only for resaleable returns; damaged goods are audited
 * but cannot increase available stock. No financial refund is triggered.
 */
@Service
public class SaasStockReturnService {
    private final JdbcTemplate jdbc;
    private final SaasTenantAccess access;

    public SaasStockReturnService(JdbcTemplate jdbc, SaasTenantAccess access) {
        this.jdbc=jdbc;
        this.access=access;
    }

    public List<Map<String,Object>> itensFechados(UUID empresa,UUID filial,UUID comanda) {
        access.requireMembership(empresa,"GERENTE","PROPRIETARIO");
        access.requireFilial(empresa,filial);
        requireClosedTab(empresa,filial,comanda,false);
        return jdbc.queryForList("""
            select i.id, i.produto_id, i.nome_snapshot, i.quantidade,
                   i.preco_unitario_snapshot,
                   coalesce((select sum(r.quantidade)
                             from stockos_private.devolucoes_estoque r
                             where r.empresa_id=i.empresa_id and r.filial_id=i.filial_id
                               and r.comanda_item_id=i.id),0) as quantidade_devolvida,
                   coalesce((select sum(r.quantidade)
                             from stockos_private.devolucoes_estoque r
                             where r.empresa_id=i.empresa_id and r.filial_id=i.filial_id
                               and r.comanda_item_id=i.id and r.destino='REESTOQUE'),0) as reestocada,
                   coalesce((select sum(r.quantidade)
                             from stockos_private.devolucoes_estoque r
                             where r.empresa_id=i.empresa_id and r.filial_id=i.filial_id
                               and r.comanda_item_id=i.id and r.destino='DESCARTE'),0) as descartada
            from stockos_private.comanda_itens i
            where i.empresa_id=? and i.filial_id=? and i.comanda_id=?
              and i.cancelado_em is null
            order by i.criado_em,i.id
            """,empresa,filial,comanda);
    }

    public List<Map<String,Object>> historico(UUID empresa,UUID filial,UUID comanda) {
        access.requireMembership(empresa,"GERENTE","PROPRIETARIO");
        access.requireFilial(empresa,filial);
        requireClosedTab(empresa,filial,comanda,false);
        return jdbc.queryForList("""
            select r.id,r.comanda_item_id,r.produto_id,r.quantidade,r.destino,
                   r.motivo,r.registrado_por,r.registrado_em
            from stockos_private.devolucoes_estoque r
            where r.empresa_id=? and r.filial_id=? and r.comanda_id=?
            order by r.registrado_em,r.id
            """,empresa,filial,comanda);
    }

    @Transactional
    public Map<String,Object> registrar(UUID empresa,UUID filial,UUID comanda,UUID item,
            int quantidade,String destino,String motivo,String chave) {
        access.requireMembership(empresa,"GERENTE","PROPRIETARIO");
        access.requireFilial(empresa,filial);
        if(quantidade<=0 || quantidade>10000)
            bad("Quantidade deve ser positiva e limitada");
        if(!List.of("REESTOQUE","DESCARTE").contains(destino))
            bad("Informe REESTOQUE ou DESCARTE");
        if(motivo==null || motivo.trim().length()<8 || motivo.length()>240)
            bad("Motivo obrigatorio de 8 a 240 caracteres");
        if(chave==null || chave.isBlank() || chave.length()>120)
            bad("Idempotency-Key obrigatoria");
        String why=motivo.trim();

        requireClosedTab(empresa,filial,comanda,true);
        // Lock item BEFORE checking previous totals: concurrent operators cannot
        // return more units than were delivered.
        List<Map<String,Object>> found=jdbc.queryForList("""
            select produto_id,quantidade,cancelado_em
            from stockos_private.comanda_itens
            where empresa_id=? and filial_id=? and comanda_id=? and id=?
            for update
            """,empresa,filial,comanda,item);
        if(found.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Item nao encontrado");
        Map<String,Object> original=found.get(0);
        if(original.get("cancelado_em")!=null)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Item ja cancelado");

        List<Map<String,Object>> replay=jdbc.queryForList("""
            select id,comanda_id,comanda_item_id,quantidade,destino,motivo
            from stockos_private.devolucoes_estoque
            where empresa_id=? and idempotency_key=?
            """,empresa,chave);
        if(!replay.isEmpty()){
            Map<String,Object> old=replay.get(0);
            if(!comanda.equals(old.get("comanda_id")) || !item.equals(old.get("comanda_item_id"))
                || quantidade!=((Number)old.get("quantidade")).intValue()
                || !destino.equals(old.get("destino")) || !why.equals(old.get("motivo")))
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Chave de operacao reutilizada com dados diferentes");
            return old;
        }

        int purchased=((Number)original.get("quantidade")).intValue();
        int already=jdbc.queryForObject("""
            select coalesce(sum(quantidade),0)
            from stockos_private.devolucoes_estoque
            where empresa_id=? and filial_id=? and comanda_item_id=?
            """,Integer.class,empresa,filial,item);
        if(quantidade>purchased-already)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Quantidade excede os produtos restantes desta compra");

        UUID produto=(UUID)original.get("produto_id");
        // Idempotency + quantity validation + stock mutation + audit occur together.
        Map<String,Object> record=jdbc.queryForMap("""
            insert into stockos_private.devolucoes_estoque
              (empresa_id,filial_id,comanda_id,comanda_item_id,produto_id,
               quantidade,destino,motivo,idempotency_key,registrado_por)
            values (?,?,?,?,?,?,?,?,?,?)
            returning id,comanda_item_id,quantidade,destino,motivo,registrado_em
            """,empresa,filial,comanda,item,produto,quantidade,destino,why,chave,
               access.authenticatedSubject());
        if("REESTOQUE".equals(destino)){
            int modified=jdbc.update("""
                update stockos_private.produtos set estoque_atual=estoque_atual+?
                where empresa_id=? and filial_id=? and id=?
                """,quantidade,empresa,filial,produto);
            if(modified!=1)throw new IllegalStateException("Produto nao encontrado para reintegracao");
        }
        return record;
    }

    private void requireClosedTab(UUID empresa,UUID filial,UUID comanda,boolean lock) {
        String sql="""
            select status from stockos_private.comandas
            where empresa_id=? and filial_id=? and id=?
            """+(lock?" for update":"");
        List<String> statuses=jdbc.query(sql,(rs,i)->rs.getString(1),empresa,filial,comanda);
        if(statuses.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Comanda nao encontrada");
        if(!"FECHADA".equals(statuses.get(0)))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Comanda ainda aberta");
    }
    private static void bad(String message){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}
