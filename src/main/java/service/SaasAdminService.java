package service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Staging-only management, with authorization and audit on every write. */
@Service
public class SaasAdminService {
    private final JdbcTemplate jdbc;
    private final SaasAdminAccess access;
    public SaasAdminService(JdbcTemplate jdbc, SaasAdminAccess access) {
        this.jdbc=jdbc; this.access=access;
    }
    public List<Map<String,Object>> planos() {
        access.requireAdmin();
        return jdbc.queryForList("""
          select id,codigo,nome,preco_mensal,ativo,inclui_comandas,max_filiais,max_usuarios
          from stockos_private.planos order by codigo
          """);
    }
    public List<Map<String,Object>> empresas() {
        access.requireAdmin();
        return jdbc.queryForList("""
          select e.id,e.nome,e.ativa,e.criada_em,l.status,p.codigo as plano,l.vigencia_fim
          from stockos_private.empresas e
          left join stockos_private.licencas l on l.empresa_id=e.id
          left join stockos_private.planos p on p.id=l.plano_id
          order by e.criada_em desc
          """);
    }
    public List<Map<String,Object>> auditoria(int limite) {
        access.requireAdmin();
        if(limite<1 || limite>200) bad("Limite entre 1 e 200");
        return jdbc.queryForList("""
          select id,ator_auth_user_id,acao,empresa_id,alvo_id,detalhes,criada_em
          from stockos_private.admin_auditoria
          order by criada_em desc,id desc limit ?
          """,limite);
    }

    @Transactional
    public Map<String,Object> cadastrarPlano(String codigo,String nome,BigDecimal preco,
                                            int maxFiliais,int maxUsuarios,boolean comandas) {
        UUID actor=access.requireAdmin();
        validate(codigo,32,"Codigo"); validate(nome,100,"Nome");
        if(!codigo.matches("[a-z0-9_-]+")) bad("Codigo invalido");
        if(preco==null || preco.signum()<0 || preco.scale()>2 || preco.precision()>18)
            bad("Preco invalido");
        if(maxFiliais<1 || maxFiliais>1000 || maxUsuarios<1 || maxUsuarios>100000)
            bad("Limites invalidos");
        UUID id=UUID.randomUUID();
        try {
            jdbc.update("""
              insert into stockos_private.planos
                (id,codigo,nome,preco_mensal,ativo,inclui_comandas,max_filiais,max_usuarios)
              values (?,?,?,?,true,?,?,?)
              """,id,codigo.trim(),nome.trim(),preco,comandas,maxFiliais,maxUsuarios);
        } catch(DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Plano duplicado ou invalido",ex);
        }
        audit(actor,"PLANO_CRIADO",null,id,"codigo="+codigo.trim());
        return Map.of("id",id,"codigo",codigo.trim(),"nome",nome.trim());
    }

    @Transactional
    public Map<String,Object> cadastrarEmpresa(String nome,String filial,UUID ownerAuthId) {
        UUID actor=access.requireAdmin();
        validate(nome,160,"Empresa");validate(filial,160,"Filial");
        if(ownerAuthId==null)bad("UUID do proprietario obrigatorio");
        UUID empresaId=UUID.randomUUID(), filialId=UUID.randomUUID();
        jdbc.update("insert into stockos_private.empresas (id,nome) values (?,?)",
                    empresaId,nome.trim());
        jdbc.update("""
          insert into stockos_private.filiais(id,empresa_id,nome) values (?,?,?)
          """,filialId,empresaId,filial.trim());
        jdbc.update("""
          insert into stockos_private.membros(empresa_id,auth_user_id,papel)
          values (?,?,'PROPRIETARIO')
          """,empresaId,ownerAuthId);
        audit(actor,"EMPRESA_CRIADA",empresaId,empresaId,
              "filial="+filialId+"; proprietario="+ownerAuthId);
        return Map.of("empresaId",empresaId,"filialId",filialId,
                      "proprietarioAuthUserId",ownerAuthId,"nome",nome.trim());
    }

    @Transactional
    public Map<String,Object> licenciar(UUID empresaId,UUID planoId,String status,
                                     OffsetDateTime inicio,OffsetDateTime fim) {
        UUID actor=access.requireAdmin();
        if(empresaId==null || planoId==null)bad("Empresa e plano obrigatorios");
        if(!List.of("TRIAL","ATIVA","SUSPENSA","CANCELADA").contains(status))
            bad("Status invalido");
        if(inicio==null || fim==null || !fim.isAfter(inicio))bad("Vigencia invalida");
        List<BigDecimal> preco=jdbc.query("""
          select preco_mensal from stockos_private.planos
          where id=? and ativo=true
          """,(rs,i)->rs.getBigDecimal(1),planoId);
        if(preco.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Plano inexistente");
        List<UUID> empresa=jdbc.query("""
          select id from stockos_private.empresas where id=? and ativa=true
          """,(rs,i)->rs.getObject(1,UUID.class),empresaId);
        if(empresa.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Empresa inexistente");
        jdbc.update("""
          insert into stockos_private.licencas
            (empresa_id,plano_id,status,vigencia_inicio,vigencia_fim,valor_mensal_snapshot)
          values (?,?,?,?,?,?)
          on conflict (empresa_id) do update set
            plano_id=excluded.plano_id,status=excluded.status,
            vigencia_inicio=excluded.vigencia_inicio,vigencia_fim=excluded.vigencia_fim,
            valor_mensal_snapshot=excluded.valor_mensal_snapshot,atualizada_em=now()
          """,empresaId,planoId,status,inicio,fim,preco.get(0));
        audit(actor,"LICENCA_DEFINIDA",empresaId,planoId,
              "status="+status+"; inicio="+inicio+"; fim="+fim+"; preco="+preco.get(0));
        return Map.of("empresaId",empresaId,"planoId",planoId,"status",status,
                      "vigenciaInicio",inicio,"vigenciaFim",fim,"preco",preco.get(0));
    }
    private void audit(UUID actor,String acao,UUID empresa,UUID alvo,String detalhes) {
        jdbc.update("""
          insert into stockos_private.admin_auditoria
            (ator_auth_user_id,acao,empresa_id,alvo_id,detalhes)
          values (?,?,?,?,?)
          """,actor,acao,empresa,alvo,detalhes);
    }
    private static void validate(String s,int max,String field) {
        if(s==null || s.isBlank() || s.trim().length()>max)bad(field+" invalido");
    }
    private static void bad(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);
    }
}
