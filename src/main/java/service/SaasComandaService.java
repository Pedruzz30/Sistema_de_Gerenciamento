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
 * Tenant-safe counter sales (comandas). Each mutation locks its tab and writes
 * stock or payment in the same transaction. All queries require company+branch scope.
 */
@Service
public class SaasComandaService {
    private final JdbcTemplate jdbc;
    private final SaasTenantAccess tenant;

    public SaasComandaService(JdbcTemplate jdbc, SaasTenantAccess tenant) {
        this.jdbc = jdbc;
        this.tenant = tenant;
    }

    public List<Map<String,Object>> listar(UUID empresa, UUID filial) {
        tenant.requireMembership(empresa);
        tenant.requireFilial(empresa, filial);
        return jdbc.queryForList("""
            select c.id, c.numero, c.status, c.aberta_em,
              coalesce((select sum(i.quantidade*i.preco_unitario_snapshot)
                        from stockos_private.comanda_itens i
                        where i.empresa_id=c.empresa_id and i.filial_id=c.filial_id
                          and i.comanda_id=c.id and i.cancelado_em is null),0) as total,
              coalesce((select sum(p.valor) from stockos_private.pagamentos p
                        where p.empresa_id=c.empresa_id and p.filial_id=c.filial_id
                          and p.comanda_id=c.id),0) as pago
            from stockos_private.comandas c
            where c.empresa_id=? and c.filial_id=? and c.status='ABERTA'
            order by c.numero
            """, empresa, filial);
    }

    @Transactional
    public Map<String,Object> abrir(UUID empresa, UUID filial, int numero) {
        tenant.requireMembership(empresa, "ATENDENTE", "GERENTE", "PROPRIETARIO");
        tenant.requireFilial(empresa, filial);
        if (numero < 1 || numero > 9999) bad("Numero deve estar entre 1 e 9999");
        UUID actor = tenant.authenticatedSubject();
        // Unique filtered index prevents concurrent opening of identical tab numbers.
        try {
            return jdbc.queryForMap("""
                insert into stockos_private.comandas (empresa_id,filial_id,numero,aberta_por)
                values (?,?,?,?) returning id,numero,status,aberta_em
                """, empresa,filial,numero,actor);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Comanda ja esta aberta", ex);
        }
    }

    public List<Map<String,Object>> itens(UUID empresa, UUID filial, UUID comanda) {
        tenant.requireMembership(empresa);
        tenant.requireFilial(empresa, filial);
        requireComanda(empresa,filial,comanda,false);
        return jdbc.queryForList("""
            select id, produto_id, nome_snapshot, quantidade, preco_unitario_snapshot,
                   criado_em, criado_por, cancelado_em
            from stockos_private.comanda_itens
            where empresa_id=? and filial_id=? and comanda_id=?
            order by criado_em, id
            """, empresa,filial,comanda);
    }

    @Transactional
    public Map<String,Object> adicionar(UUID empresa,UUID filial,UUID comanda, UUID produto,
                                         int quantidade,String chave) {
        tenant.requireMembership(empresa, "ATENDENTE", "GERENTE", "PROPRIETARIO");
        tenant.requireFilial(empresa,filial);
        if (quantidade <= 0 || quantidade > 10000) bad("Quantidade invalida");
        validateKey(chave);
        requireComanda(empresa,filial,comanda,true);
        List<Map<String,Object>> previous=jdbc.queryForList("""
          select id, produto_id, quantidade, comanda_id from stockos_private.comanda_itens
          where empresa_id=? and filial_id=? and idempotency_key=?
          """,empresa,filial,chave);
        if(!previous.isEmpty()){
            Map<String,Object> existing=previous.get(0);
            if(!comanda.equals(existing.get("comanda_id")) ||
               !produto.equals(existing.get("produto_id")) ||
               quantidade != ((Number)existing.get("quantidade")).intValue()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Chave reutilizada com dados diferentes");
            }
            return existing;
        }
        List<Map<String,Object>> product=jdbc.queryForList("""
          select id,nome,preco_unitario,estoque_atual
          from stockos_private.produtos
          where empresa_id=? and filial_id=? and id=? and ativo=true
          for update
          """,empresa,filial,produto);
        if(product.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Produto nao encontrado");
        if(((Number)product.get(0).get("estoque_atual")).intValue()<quantidade)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Estoque insuficiente");
        jdbc.update("""
          update stockos_private.produtos set estoque_atual=estoque_atual-?
          where empresa_id=? and filial_id=? and id=?
          """,quantidade,empresa,filial,produto);
        Map<String,Object> item=jdbc.queryForMap("""
          insert into stockos_private.comanda_itens
          (empresa_id,filial_id,comanda_id,produto_id,nome_snapshot,quantidade,
           preco_unitario_snapshot,idempotency_key,criado_por)
          values (?,?,?,?,?,?,?,?,?)
          returning id,nome_snapshot,quantidade,preco_unitario_snapshot
          """,empresa,filial,comanda,produto,product.get(0).get("nome"),
          quantidade,product.get(0).get("preco_unitario"),chave,tenant.authenticatedSubject());
        jdbc.update("""
          insert into stockos_private.movimentos_estoque
          (empresa_id,filial_id,produto_id,comanda_item_id,tipo,quantidade,registrado_por)
          values (?,?,?,?, 'ENTREGA',?,?)
          """,empresa,filial,produto,item.get("id"),quantidade,tenant.authenticatedSubject());
        return item;
    }

    @Transactional
    public void cancelarItem(UUID empresa,UUID filial,UUID comanda,UUID itemId) {
        tenant.requireMembership(empresa, "GERENTE", "PROPRIETARIO");
        tenant.requireFilial(empresa,filial);
        requireComanda(empresa,filial,comanda,true);
        List<Map<String,Object>> rows=jdbc.queryForList("""
           select produto_id,quantidade,cancelado_em from stockos_private.comanda_itens
           where empresa_id=? and filial_id=? and comanda_id=? and id=? for update
           """,empresa,filial,comanda,itemId);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Item nao encontrado");
        if(rows.get(0).get("cancelado_em")!=null) throw new ResponseStatusException(HttpStatus.CONFLICT,"Item ja cancelado");
        int n=((Number)rows.get(0).get("quantidade")).intValue();
        BigDecimal saldoRestante = total(empresa,filial,comanda)
                .subtract(BigDecimal.valueOf(n).multiply(precoItem(empresa,filial,comanda,itemId)));
        if (pago(empresa,filial,comanda).compareTo(saldoRestante)>0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Nao e possivel cancelar item: estorne o pagamento antes");
        }
        UUID p=(UUID)rows.get(0).get("produto_id");
        jdbc.update("""
          update stockos_private.comanda_itens set cancelado_em=now(),cancelado_por=?
          where empresa_id=? and filial_id=? and id=?
          """,tenant.authenticatedSubject(),empresa,filial,itemId);
        jdbc.update("""
          update stockos_private.produtos set estoque_atual=estoque_atual+?
          where empresa_id=? and filial_id=? and id=?
          """,n,empresa,filial,p);
        jdbc.update("""
          insert into stockos_private.movimentos_estoque
          (empresa_id,filial_id,produto_id,comanda_item_id,tipo,quantidade,registrado_por)
          values (?,?,?,?, 'ESTORNO',?,?)
          """,empresa,filial,p,itemId,n,tenant.authenticatedSubject());
    }

    @Transactional
    public Map<String,Object> pagamento(UUID empresa,UUID filial,UUID comanda,
                        String modalidade,BigDecimal valor,String chave) {
        tenant.requireMembership(empresa, "ATENDENTE","GERENTE","PROPRIETARIO");
        tenant.requireFilial(empresa,filial);
        validateKey(chave);
        if(!List.of("DINHEIRO","PIX","DEBITO","CREDITO").contains(modalidade))
            bad("Modalidade de pagamento invalida");
        if(valor==null || valor.signum()<=0 || valor.scale()>2) bad("Valor monetario invalido");
        requireComanda(empresa,filial,comanda,true);
        List<Map<String,Object>> previous=jdbc.queryForList("""
          select id,comanda_id,modalidade,valor from stockos_private.pagamentos
          where empresa_id=? and filial_id=? and idempotency_key=?
          """,empresa,filial,chave);
        if(!previous.isEmpty()){
            Map<String,Object> entry=previous.get(0);
            if(!comanda.equals(entry.get("comanda_id")) ||
              !modalidade.equals(entry.get("modalidade")) ||
              valor.compareTo((BigDecimal)entry.get("valor"))!=0)
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Chave reutilizada com pagamento diferente");
            return entry;
        }
        // Serialize receipts with cash-close operations. Pix and card transactions
        // also belong to the active shift for truthful end-of-shift reconciliation.
        List<UUID> openShift=jdbc.query("""
          select id from stockos_private.caixa_turnos
          where empresa_id=? and filial_id=? and status='ABERTO'
          for update
          """,(rs,i)->rs.getObject(1,UUID.class),empresa,filial);
        if(openShift.isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Abra o caixa antes de receber");
        UUID turno=openShift.get(0);
        BigDecimal total=total(empresa,filial,comanda);
        BigDecimal pago=pago(empresa,filial,comanda);
        if(pago.add(valor).compareTo(total)>0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Pagamento excede valor devido");
        return jdbc.queryForMap("""
           insert into stockos_private.pagamentos
             (empresa_id,filial_id,comanda_id,modalidade,valor,idempotency_key,registrado_por,caixa_turno_id)
           values (?,?,?,?,?,?,?,?) returning id,modalidade,valor,registrado_em
           """,empresa,filial,comanda,modalidade,valor,chave,tenant.authenticatedSubject(),turno);
    }

    @Transactional
    public Map<String,Object> fechar(UUID empresa,UUID filial,UUID comanda) {
        tenant.requireMembership(empresa, "ATENDENTE","GERENTE","PROPRIETARIO");
        tenant.requireFilial(empresa,filial);
        requireComanda(empresa,filial,comanda,true);
        BigDecimal total=total(empresa,filial,comanda), pago=pago(empresa,filial,comanda);
        if(total.signum()<=0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Comanda sem itens");
        if(total.compareTo(pago)!=0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Pagamento incompleto");
        return jdbc.queryForMap("""
           update stockos_private.comandas
           set status='FECHADA',fechada_em=now()
           where empresa_id=? and filial_id=? and id=?
           returning id,numero,status,fechada_em
           """,empresa,filial,comanda);
    }

    private BigDecimal total(UUID empresa,UUID filial,UUID comanda){
        return jdbc.queryForObject("""
          select coalesce(sum(quantidade*preco_unitario_snapshot),0)
          from stockos_private.comanda_itens
          where empresa_id=? and filial_id=? and comanda_id=? and cancelado_em is null
          """, BigDecimal.class,empresa,filial,comanda);
    }
    private BigDecimal precoItem(UUID empresa,UUID filial,UUID comanda,UUID itemId) {
        return jdbc.queryForObject("""
          select preco_unitario_snapshot from stockos_private.comanda_itens
          where empresa_id=? and filial_id=? and comanda_id=? and id=?
          """,BigDecimal.class,empresa,filial,comanda,itemId);
    }
    private BigDecimal pago(UUID empresa,UUID filial,UUID comanda){
        return jdbc.queryForObject("""
          select coalesce(sum(valor),0) from stockos_private.pagamentos
          where empresa_id=? and filial_id=? and comanda_id=?
          """,BigDecimal.class,empresa,filial,comanda);
    }
    private void requireComanda(UUID empresa,UUID filial,UUID comanda,boolean opened){
        String statement="""
          select status from stockos_private.comandas
          where id=? and empresa_id=? and filial_id=?
          """ + (opened ? " for update" : "");
        List<String> statuses=jdbc.query(statement,(r,i)->r.getString(1),comanda,empresa,filial);
        if(statuses.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Comanda nao encontrada");
        if(opened && !"ABERTA".equals(statuses.get(0)))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Comanda nao esta aberta");
    }
    private static void validateKey(String key){
        if(key==null || key.isBlank() || key.length()>120)
            bad("Idempotency-Key obrigatoria (maximo 120 caracteres)");
    }
    private static void bad(String message){
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);
    }
}
