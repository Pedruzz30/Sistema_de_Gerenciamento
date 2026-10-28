package service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Manager-approved goods receipt, atomic with an immutable receiving journal. */
@Service
public class SaasGoodsReceiptService {
    private final JdbcTemplate jdbc;
    private final SaasTenantAccess tenant;

    public SaasGoodsReceiptService(JdbcTemplate jdbc, SaasTenantAccess tenant) {
        this.jdbc=jdbc;
        this.tenant=tenant;
    }

    public record ItemInput(UUID produtoId, int quantidade, BigDecimal custoUnitario) {}

    public List<Map<String,Object>> fornecedores(UUID empresa) {
        tenant.requireMembership(empresa,"GERENTE","PROPRIETARIO");
        return jdbc.queryForList("""
            select id,nome,documento,ativo,criado_em
            from stockos_private.fornecedores where empresa_id=? order by nome
            """,empresa);
    }

    @Transactional
    public Map<String,Object> cadastrarFornecedor(UUID empresa,String nome,String documento) {
        tenant.requireMembership(empresa,"GERENTE","PROPRIETARIO");
        if(nome==null||nome.trim().length()<2||nome.trim().length()>160)
            bad("Nome do fornecedor deve ter de 2 a 160 caracteres");
        String doc=(documento==null||documento.isBlank())?null:documento.trim();
        if(doc!=null && (doc.length()<5||doc.length()>32))
            bad("Documento do fornecedor invalido");
        try {
            return jdbc.queryForMap("""
                insert into stockos_private.fornecedores
                (empresa_id,nome,documento,criado_por)
                values (?,?,?,?)
                returning id,nome,documento,ativo,criado_em
                """,empresa,nome.trim(),doc,tenant.authenticatedSubject());
        } catch(DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                   "Documento de fornecedor ja cadastrado ou invalido",ex);
        }
    }

    public List<Map<String,Object>> entradas(UUID empresa,UUID filial,int limite) {
        tenant.requireMembership(empresa,"GERENTE","PROPRIETARIO");
        tenant.requireFilial(empresa,filial);
        if(limite<1||limite>100)bad("Limite entre 1 e 100");
        return jdbc.queryForList("""
            select e.id,e.fornecedor_id,f.nome as fornecedor_nome,
                   e.documento_referencia,e.custo_total,e.registrado_por,e.registrado_em,
                   (select count(*) from stockos_private.entrada_mercadoria_itens i
                    where i.entrada_id=e.id) as linhas
            from stockos_private.entradas_mercadoria e
            join stockos_private.fornecedores f
              on f.empresa_id=e.empresa_id and f.id=e.fornecedor_id
            where e.empresa_id=? and e.filial_id=?
            order by e.registrado_em desc,e.id desc limit ?
            """,empresa,filial,limite);
    }

    @Transactional
    public Map<String,Object> receber(UUID empresa,UUID filial,UUID fornecedor,
                    String documento,List<ItemInput> itens,String chave) {
        tenant.requireMembership(empresa,"GERENTE","PROPRIETARIO");
        tenant.requireFilial(empresa,filial);
        if(fornecedor==null)bad("Fornecedor obrigatorio");
        if(documento==null||documento.trim().length()<3||documento.trim().length()>80)
            bad("Referencia do documento de 3 a 80 caracteres");
        if(chave==null||chave.isBlank()||chave.length()>120)
            bad("Idempotency-Key obrigatoria");
        if(itens==null||itens.isEmpty()||itens.size()>100)
            bad("Informe de 1 a 100 produtos por recebimento");
        String doc=documento.trim().toUpperCase(Locale.ROOT);
        List<ItemInput> ordered=new ArrayList<>(itens);
        // Validate before sorting so malformed/null JSON entries fail with 400,
        // not an unhandled NullPointerException.
        if(ordered.stream().anyMatch(i->i==null || i.produtoId()==null))
            bad("Produto de entrada invalido");
        ordered.sort(Comparator.comparing(i->i.produtoId().toString()));
        Set<UUID> uniqueProducts=new HashSet<>();
        BigDecimal total=BigDecimal.ZERO;
        for(ItemInput i:ordered){
            if(i==null||i.produtoId()==null||!uniqueProducts.add(i.produtoId())
              ||i.quantidade()<=0||i.quantidade()>100000||i.custoUnitario()==null
              ||i.custoUnitario().signum()<0||i.custoUnitario().scale()>2
              ||i.custoUnitario().precision()>17)
                bad("Itens devem ser unicos, com quantidade e custo validos");
            total=total.add(i.custoUnitario().multiply(BigDecimal.valueOf(i.quantidade())));
        }
        if(total.precision()>19 || total.scale()>2)bad("Custo total excede os limites monetarios");
        String digest=digest(fornecedor,doc,ordered);

        // Lock the company before checking both unique keys. This serializes
        // goods receipts inside the company, preventing duplicate invoice
        // registration races without relying on a failed SQL transaction.
        List<UUID> company=jdbc.query("""
            select id from stockos_private.empresas where id=? and ativa=true for update
            """,(rs,n)->rs.getObject(1,UUID.class),empresa);
        if(company.isEmpty())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Empresa inativa");

        List<Map<String,Object>> replay=jdbc.queryForList("""
            select id,fornecedor_id,documento_referencia,payload_hash,custo_total
            from stockos_private.entradas_mercadoria
            where empresa_id=? and idempotency_key=?
            """,empresa,chave);
        if(!replay.isEmpty()){
            Map<String,Object> existing=replay.get(0);
            if(!fornecedor.equals(existing.get("fornecedor_id"))
              ||!doc.equals(existing.get("documento_referencia"))
              ||!digest.equals(existing.get("payload_hash")))
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Chave de recebimento reutilizada com dados diferentes");
            // Key is company-wide; make sure it cannot replay another filial.
            List<UUID> match=jdbc.query("""
                select id from stockos_private.entradas_mercadoria
                where id=? and empresa_id=? and filial_id=?
                """,(rs,n)->rs.getObject(1,UUID.class),existing.get("id"),empresa,filial);
            if(match.isEmpty())
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Chave usada em outra filial");
            return existing;
        }
        Integer docs=jdbc.queryForObject("""
            select count(*) from stockos_private.entradas_mercadoria
            where empresa_id=? and filial_id=? and fornecedor_id=?
              and documento_referencia=?
            """,Integer.class,empresa,filial,fornecedor,doc);
        if(docs!=null&&docs>0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Documento ja recebido nesta filial");

        List<UUID> supplier=jdbc.query("""
            select id from stockos_private.fornecedores
            where empresa_id=? and id=? and ativo=true
            """,(rs,n)->rs.getObject(1,UUID.class),empresa,fornecedor);
        if(supplier.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Fornecedor nao encontrado ou inativo nesta empresa");

        // Validate every row before writing the receipt or changing the stock.
        for(ItemInput item:ordered){
            List<Map<String,Object>> products=jdbc.queryForList("""
                select nome,estoque_atual from stockos_private.produtos
                where empresa_id=? and filial_id=? and id=? and ativo=true for update
                """,empresa,filial,item.produtoId());
            if(products.isEmpty())
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Produto nao encontrado ou inativo nesta filial");
            int existing=((Number)products.get(0).get("estoque_atual")).intValue();
            if(existing>Integer.MAX_VALUE-item.quantidade())
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Estoque ultrapassaria o limite");
        }

        Map<String,Object> receipt=jdbc.queryForMap("""
            insert into stockos_private.entradas_mercadoria
              (empresa_id,filial_id,fornecedor_id,documento_referencia,
               idempotency_key,payload_hash,custo_total,registrado_por)
            values (?,?,?,?,?,?,?,?)
            returning id,fornecedor_id,documento_referencia,custo_total,registrado_em
            """,empresa,filial,fornecedor,doc,chave,digest,total,tenant.authenticatedSubject());

        UUID entrada=(UUID)receipt.get("id");
        for(ItemInput item:ordered){
            String nome=jdbc.queryForObject("""
                select nome from stockos_private.produtos
                where empresa_id=? and filial_id=? and id=?
                """,String.class,empresa,filial,item.produtoId());
            jdbc.update("""
                insert into stockos_private.entrada_mercadoria_itens
                  (empresa_id,filial_id,entrada_id,produto_id,
                   quantidade,custo_unitario,nome_snapshot)
                values (?,?,?,?,?,?,?)
                """,empresa,filial,entrada,item.produtoId(),item.quantidade(),
                 item.custoUnitario(),nome);
            jdbc.update("""
                update stockos_private.produtos
                set estoque_atual=estoque_atual+?
                where empresa_id=? and filial_id=? and id=?
                """,item.quantidade(),empresa,filial,item.produtoId());
        }
        return receipt;
    }

    private static String digest(UUID supplier,String reference,List<ItemInput> items) {
        StringBuilder canonical=new StringBuilder(supplier.toString()).append('|').append(reference);
        for(ItemInput i:items)
            canonical.append('|').append(i.produtoId()).append(':').append(i.quantidade())
              .append(':').append(i.custoUnitario().setScale(2).toPlainString());
        try {
            byte[] value=MessageDigest.getInstance("SHA-256")
                            .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(value);
        } catch(NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable",ex);
        }
    }

    private static void bad(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);
    }
}
