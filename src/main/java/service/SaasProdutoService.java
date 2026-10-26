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
          select id,codigo,nome,preco_unitario,estoque_atual,ativo
          from stockos_private.produtos
          where empresa_id=? and filial_id=?
          order by nome,codigo
          """,empresa,filial);
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
