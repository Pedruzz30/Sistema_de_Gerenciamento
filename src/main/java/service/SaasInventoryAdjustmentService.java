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
 * Audited physical stocktaking. Never modify stock based on a stale
 * browser snapshot. Row lock serializes this correction with sales/returns.
 */
@Service
public class SaasInventoryAdjustmentService {
    private final JdbcTemplate jdbc;
    private final SaasTenantAccess access;

    public SaasInventoryAdjustmentService(JdbcTemplate jdbc, SaasTenantAccess access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    public List<Map<String,Object>> historico(UUID empresa,UUID filial,int limite) {
        access.requireMembership(empresa,"GERENTE","PROPRIETARIO");
        access.requireFilial(empresa,filial);
        if(limite<1||limite>100)
            bad("Limite de historico entre 1 e 100");
        return jdbc.queryForList("""
            select a.id,a.produto_id,p.nome as produto_nome,p.codigo,
                   a.estoque_anterior,a.estoque_contado,a.diferenca,
                   a.motivo,a.registrado_por,a.registrado_em
            from stockos_private.ajustes_inventario a
            join stockos_private.produtos p
              on p.empresa_id=a.empresa_id and p.filial_id=a.filial_id
             and p.id=a.produto_id
            where a.empresa_id=? and a.filial_id=?
            order by a.registrado_em desc,a.id desc
            limit ?
            """,empresa,filial,limite);
    }

    @Transactional
    public Map<String,Object> conferir(UUID empresa,UUID filial,UUID produto,
                   int estoqueEsperado,int estoqueContado,String motivo,String chave) {
        access.requireMembership(empresa,"GERENTE","PROPRIETARIO");
        access.requireFilial(empresa,filial);
        if(estoqueEsperado<0||estoqueContado<0)
            bad("Quantidades de estoque nao podem ser negativas");
        if(motivo==null||motivo.trim().length()<8||motivo.length()>240)
            bad("Informe um motivo com 8 a 240 caracteres");
        if(chave==null||chave.isBlank()||chave.length()>120)
            bad("Idempotency-Key obrigatoria");
        String why=motivo.trim();

        // Lock product before inspecting the count and replay. Sales obtain this
        // same lock, so a count cannot silently erase an intervening sale.
        List<Map<String,Object>> products=jdbc.queryForList("""
            select estoque_atual,ativo from stockos_private.produtos
            where empresa_id=? and filial_id=? and id=? for update
            """,empresa,filial,produto);
        if(products.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Produto nao encontrado");

        List<Map<String,Object>> previous=jdbc.queryForList("""
            select id,produto_id,estoque_anterior,estoque_contado,diferenca,
                   motivo,registrado_por,registrado_em
            from stockos_private.ajustes_inventario
            where empresa_id=? and idempotency_key=?
            """,empresa,chave);
        if(!previous.isEmpty()){
            Map<String,Object> saved=previous.get(0);
            if(!produto.equals(saved.get("produto_id"))
              || estoqueEsperado!=((Number)saved.get("estoque_anterior")).intValue()
              || estoqueContado!=((Number)saved.get("estoque_contado")).intValue()
              || !why.equals(saved.get("motivo")))
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Chave reutilizada com outros dados de conferencia");
            return saved;
        }
        Map<String,Object> current=products.get(0);
        if(!Boolean.TRUE.equals(current.get("ativo")))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Produto inativo");
        int actual=((Number)current.get("estoque_atual")).intValue();
        if(actual!=estoqueEsperado)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                 "Estoque alterado por outra operacao. Atualize a tela e confira novamente");

        // Zero-difference counts are still valuable proof that the shelf was checked.
        Map<String,Object> recorded=jdbc.queryForMap("""
            insert into stockos_private.ajustes_inventario
              (empresa_id,filial_id,produto_id,estoque_anterior,estoque_contado,
               motivo,idempotency_key,registrado_por)
            values (?,?,?,?,?,?,?,?)
            returning id,produto_id,estoque_anterior,estoque_contado,diferenca,
                      motivo,registrado_por,registrado_em
            """,empresa,filial,produto,estoqueEsperado,estoqueContado,why,chave,
               access.authenticatedSubject());
        if(actual!=estoqueContado){
            int updated=jdbc.update("""
                update stockos_private.produtos set estoque_atual=?
                where empresa_id=? and filial_id=? and id=?
                """,estoqueContado,empresa,filial,produto);
            if(updated!=1)throw new IllegalStateException("Falha na atualizacao do estoque");
        }
        return recorded;
    }

    private static void bad(String message){
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);
    }
}
