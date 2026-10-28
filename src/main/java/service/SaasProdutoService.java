package service;

import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SaasProdutoService {
    private final JdbcTemplate jdbc;
    private final SaasTenantAccess tenant;

    public SaasProdutoService(JdbcTemplate jdbc, SaasTenantAccess tenant) {
        this.jdbc=jdbc; this.tenant=tenant;
    }

    public List<Map<String,Object>> listar(UUID empresa, UUID filial) {
        tenant.requireMembership(empresa);
        tenant.requireFilial(empresa,filial);
        return jdbc.queryForList("""
          select id,codigo,nome,preco_unitario,estoque_atual,ativo,versao_cadastro
          from stockos_private.produtos
          where empresa_id=? and filial_id=?
          order by nome,codigo
          """,empresa,filial);
    }


    /**
     * Updates ONLY catalog metadata; stock changes require goods receipt
     * or an audited physical count. Version rejects stale browser edits.
     */
    @Transactional
    public Map<String,Object> atualizar(UUID empresa,UUID filial,UUID produto,
            String codigo,String nome,BigDecimal preco,boolean ativo,long versaoEsperada) {
        tenant.requireMembership(empresa,"GERENTE","PROPRIETARIO");
        tenant.requireFilial(empresa,filial);
        if(codigo==null||codigo.trim().isEmpty()||codigo.trim().length()>60||
           nome==null||nome.trim().isEmpty()||nome.trim().length()>160||
           preco==null||preco.signum()<0||preco.scale()>2||preco.precision()>18||
           versaoEsperada<0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Dados de produto invalidos");
        List<Map<String,Object>> records=jdbc.queryForList("""
            select codigo,nome,preco_unitario,ativo,versao_cadastro
            from stockos_private.produtos
            where empresa_id=? and filial_id=? and id=? for update
            """,empresa,filial,produto);
        if(records.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Produto nao encontrado");
        Map<String,Object> old=records.get(0);
        long current=((Number)old.get("versao_cadastro")).longValue();
        if(current!=versaoEsperada)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Produto alterado em outra tela. Atualize antes de salvar");
        try {
            jdbc.update("""
                update stockos_private.produtos
                set codigo=?,nome=?,preco_unitario=?,ativo=?,
                    versao_cadastro=versao_cadastro+1
                where empresa_id=? and filial_id=? and id=?
                """,codigo.trim(),nome.trim(),preco,ativo,empresa,filial,produto);
        } catch(DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Codigo duplicado nesta filial ou valor invalido",ex);
        }
        jdbc.update("""
            insert into stockos_private.produtos_auditoria (
                empresa_id,filial_id,produto_id,codigo_anterior,codigo_novo,
                nome_anterior,nome_novo,preco_anterior,preco_novo,
                ativo_anterior,ativo_novo,versao_nova,registrado_por)
            values (?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,empresa,filial,produto,old.get("codigo"),codigo.trim(),
            old.get("nome"),nome.trim(),old.get("preco_unitario"),preco,
            old.get("ativo"),ativo,current+1,tenant.authenticatedSubject());
        return jdbc.queryForMap("""
            select id,codigo,nome,preco_unitario,estoque_atual,ativo,versao_cadastro
            from stockos_private.produtos
            where empresa_id=? and filial_id=? and id=?
            """,empresa,filial,produto);
    }

    public List<Map<String,Object>> historico(UUID empresa,UUID filial,UUID produto) {
        tenant.requireMembership(empresa,"GERENTE","PROPRIETARIO");
        tenant.requireFilial(empresa,filial);
        return jdbc.queryForList("""
            select codigo_anterior,codigo_novo,nome_anterior,nome_novo,
                   preco_anterior,preco_novo,ativo_anterior,ativo_novo,
                   versao_nova,registrado_por,registrado_em
            from stockos_private.produtos_auditoria
            where empresa_id=? and filial_id=? and produto_id=?
            order by registrado_em desc,id desc limit 50
            """,empresa,filial,produto);
    }

    @Transactional
    public Map<String,Object> cadastrar(UUID empresa,UUID filial,String codigo,String nome,
                                         BigDecimal preco,int quantidade) {
        tenant.requireMembership(empresa,"GERENTE","PROPRIETARIO");
        tenant.requireFilial(empresa,filial);
        if(codigo==null || codigo.isBlank() || codigo.length()>60 ||
           nome==null || nome.isBlank() || nome.length()>160 ||
           preco==null || preco.signum()<0 || preco.scale()>2 || quantidade<0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Dados de produto invalidos");
        try {
            return jdbc.queryForMap("""
              insert into stockos_private.produtos
                (empresa_id,filial_id,codigo,nome,preco_unitario,estoque_atual)
              values (?,?,?,?,?,?)
              returning id,codigo,nome,preco_unitario,estoque_atual
              """,empresa,filial,codigo.trim(),nome.trim(),preco,quantidade);
        } catch(org.springframework.dao.DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Codigo de produto ja cadastrado",e);
        }
    }
}
