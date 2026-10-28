package service;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SaasEmployeeService {
 private final JdbcTemplate jdbc;
 private final SaasTenantAccess tenant;
 public SaasEmployeeService(JdbcTemplate jdbc,SaasTenantAccess tenant){this.jdbc=jdbc;this.tenant=tenant;}

 public List<Map<String,Object>> listar(UUID empresa){
  tenant.requireMembership(empresa,"GERENTE","PROPRIETARIO");
  return jdbc.queryForList("select auth_user_id,papel,ativo from stockos_private.membros where empresa_id=? order by papel",empresa);
 }

 @Transactional
 public Map<String,Object> alterar(UUID empresa,UUID target,String papel,boolean ativo){
  tenant.requireMembership(empresa,"PROPRIETARIO");
  UUID actor=tenant.authenticatedSubject();
  if(target==null||!List.of("PROPRIETARIO","GERENTE","ATENDENTE").contains(papel))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Papel ou usuario invalido");
  jdbc.queryForObject("select id from stockos_private.empresas where id=? for update",UUID.class,empresa);
  Integer stillOwner=jdbc.queryForObject("""
    select count(*) from stockos_private.membros
    where empresa_id=? and auth_user_id=? and ativo=true and papel='PROPRIETARIO'
    """,Integer.class,empresa,actor);
  if(stillOwner==null||stillOwner!=1)
   throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Proprietario sem acesso");
  List<Map<String,Object>> rows=jdbc.queryForList("""
   select papel,ativo from stockos_private.membros
   where empresa_id=? and auth_user_id=? for update
   """,empresa,target);
  if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Funcionario nao vinculado");
  Map<String,Object> original=rows.get(0);
  if(actor.equals(target)&&(!ativo||!"PROPRIETARIO".equals(papel)))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Nao remova seu proprio acesso de proprietario");
  if(Boolean.TRUE.equals(original.get("ativo"))&&"PROPRIETARIO".equals(original.get("papel"))
    &&(!ativo||!"PROPRIETARIO".equals(papel))){
   Integer owners=jdbc.queryForObject("""
    select count(*) from stockos_private.membros
    where empresa_id=? and ativo=true and papel='PROPRIETARIO'
    """,Integer.class,empresa);
   if(owners==null||owners<2)
    throw new ResponseStatusException(HttpStatus.CONFLICT,"Ultimo proprietario nao pode ser removido");
  }
  if(ativo&&!Boolean.TRUE.equals(original.get("ativo"))){
   Integer count=jdbc.queryForObject("select count(*) from stockos_private.membros where empresa_id=? and ativo=true",Integer.class,empresa);
   Integer max=jdbc.queryForObject("""
    select p.max_usuarios from stockos_private.licencas l
    join stockos_private.planos p on p.id=l.plano_id
    where l.empresa_id=?
    """,Integer.class,empresa);
   Integer reserved=jdbc.queryForObject("""
    select count(*) from stockos_private.convites_funcionarios
    where empresa_id=? and status='PENDENTE' and expira_em>now()
    """,Integer.class,empresa);
   // Pending invitations reserve seats; reactivation must not take those
   // seats away or exceed the contracted maximum when invitees join.
   if(count==null||max==null||reserved==null
      ||((long)count+(long)reserved)>=max)
    throw new ResponseStatusException(HttpStatus.CONFLICT,
      "Limite de usuarios e convites pendentes do plano atingido");
  }
  jdbc.update("update stockos_private.membros set papel=?,ativo=? where empresa_id=? and auth_user_id=?",papel,ativo,empresa,target);
  jdbc.update("""
   insert into stockos_private.membros_auditoria
   (empresa_id,ator_auth_user_id,alvo_auth_user_id,papel_anterior,papel_novo,ativo_anterior,ativo_novo)
   values (?,?,?,?,?,?,?)
   """,empresa,actor,target,original.get("papel"),papel,original.get("ativo"),ativo);
  return Map.of("authUserId",target,"papel",papel,"ativo",ativo);
 }
 public List<Map<String,Object>> auditoria(UUID empresa){
  tenant.requireMembership(empresa,"PROPRIETARIO");
  return jdbc.queryForList("""
   select ator_auth_user_id,alvo_auth_user_id,papel_anterior,papel_novo,
          ativo_anterior,ativo_novo,registrado_em
   from stockos_private.membros_auditoria where empresa_id=?
   order by registrado_em desc limit 100
   """,empresa);
 }
}