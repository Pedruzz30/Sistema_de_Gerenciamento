package service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class SaasEmployeeInvitations {
    private static final SecureRandom RANDOM=new SecureRandom();
    private final JdbcTemplate jdbc;
    private final SaasTenantAccess tenant;
    private final SaasLicenseService licenses;
    private final SaasVerifiedAuthIdentity identity;

    public SaasEmployeeInvitations(JdbcTemplate jdbc,SaasTenantAccess tenant,
          SaasLicenseService licenses,SaasVerifiedAuthIdentity identity){
        this.jdbc=jdbc;this.tenant=tenant;this.licenses=licenses;this.identity=identity;
    }

    private static String email(String original){
        if(original==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Email obrigatorio");
        String value=original.trim().toLowerCase(Locale.ROOT);
        if(value.length()>254 || !value.matches("[a-z0-9._%+\\-]+@[a-z0-9.\\-]+\\.[a-z]{2,}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Email invalido");
        return value;
    }
    private static String hash(String token){
        try {
            byte[] digest=MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        }catch(Exception ex){throw new IllegalStateException("SHA-256 indisponivel",ex);}
    }
    private void lockCompany(UUID company){
        List<UUID> rows=jdbc.query("""
           select id from stockos_private.empresas where id=? and ativa=true for update
           """,(rs,i)->rs.getObject(1,UUID.class),company);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Empresa invalida");
    }
    private int seatsAvailable(UUID company){
        Integer limit=jdbc.queryForObject("""
            select p.max_usuarios from stockos_private.licencas l
            join stockos_private.planos p on p.id=l.plano_id where l.empresa_id=?
            """,Integer.class,company);
        Integer used=jdbc.queryForObject("""
            select count(*) from stockos_private.membros where empresa_id=? and ativo=true
            """,Integer.class,company);
        return Math.max(0,(limit==null?0:limit)-(used==null?0:used));
    }

    @Transactional
    public Map<String,Object> create(UUID company,String invitedEmail,String role){
        tenant.requireMembership(company,"PROPRIETARIO");
        if(!List.of("GERENTE","ATENDENTE").contains(role))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Papel invalido");
        String normalized=email(invitedEmail);
        UUID actor=tenant.authenticatedSubject();
        lockCompany(company);
        jdbc.update("""
            update stockos_private.convites_funcionarios
            set status='REVOGADO',revogado_por=?,revogado_em=now()
            where empresa_id=? and email_normalizado=? and status='PENDENTE'
              and expira_em<=now()
            """,actor,company,normalized);
        Integer pending=jdbc.queryForObject("""
            select count(*) from stockos_private.convites_funcionarios
            where empresa_id=? and email_normalizado=? and status='PENDENTE'
            """,Integer.class,company,normalized);
        if(pending!=null&&pending>0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Convite ja pendente");
        Integer reserved=jdbc.queryForObject("""
            select count(*) from stockos_private.convites_funcionarios
            where empresa_id=? and status='PENDENTE' and expira_em>now()
            """,Integer.class,company);
        if(seatsAvailable(company)<=(reserved==null?0:reserved))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Limite de usuarios e convites atingido");
        byte[] secret=new byte[32];RANDOM.nextBytes(secret);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        UUID id=UUID.randomUUID();
        jdbc.update("""
            insert into stockos_private.convites_funcionarios
              (id,empresa_id,email_normalizado,papel,token_sha256,criado_por,expira_em)
            values (?,?,?,?,?,?,now()+interval '72 hours')
            """,id,company,normalized,role,hash(token),actor);
        return Map.of("id",id,"email",normalized,"papel",role,
                      "codigoConvite",token,"validadeHoras",72);
    }

    public List<Map<String,Object>> list(UUID company){
        tenant.requireMembership(company,"PROPRIETARIO");
        return jdbc.queryForList("""
            select id,email_normalizado,papel,status,criado_em,expira_em,aceito_em
            from stockos_private.convites_funcionarios
            where empresa_id=? order by criado_em desc limit 100
            """,company);
    }

    @Transactional
    public void revoke(UUID company,UUID invitation){
        tenant.requireMembership(company,"PROPRIETARIO");
        lockCompany(company);
        int changed=jdbc.update("""
            update stockos_private.convites_funcionarios
            set status='REVOGADO',revogado_por=?,revogado_em=now()
            where empresa_id=? and id=? and status='PENDENTE'
            """,tenant.authenticatedSubject(),company,invitation);
        if(changed!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Convite nao disponivel");
    }

    @Transactional
    public Map<String,Object> accept(String token){
        UUID actor=tenant.authenticatedSubject();
        if(token==null || !token.matches("[A-Za-z0-9_-]{43}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Codigo invalido");
        // The user's email must be confirmed by Auth, not merely sent by browser.
        String verifiedEmail=identity.confirmedEmail(actor);
        String fingerprint=hash(token);
        List<UUID> companies=jdbc.query("""
            select empresa_id from stockos_private.convites_funcionarios
            where token_sha256=?
            """,(rs,i)->rs.getObject(1,UUID.class),fingerprint);
        if(companies.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Convite invalido");
        UUID company=companies.get(0);
        lockCompany(company);
        List<Map<String,Object>> found=jdbc.queryForList("""
            select id,email_normalizado,papel,status,expira_em,criado_por
            from stockos_private.convites_funcionarios
            where empresa_id=? and token_sha256=? for update
            """,company,fingerprint);
        if(found.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Convite invalido");
        Map<String,Object> invite=found.get(0);
        if(!"PENDENTE".equals(invite.get("status"))
             || !((java.sql.Timestamp)invite.get("expira_em")).toInstant().isAfter(java.time.Instant.now()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Convite vencido ou utilizado");
        if(!verifiedEmail.equals(invite.get("email_normalizado")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Email nao corresponde ao convite");
        licenses.requireActive(company);
        Integer issuerActive=jdbc.queryForObject("""
           select count(*) from stockos_private.membros
           where empresa_id=? and auth_user_id=? and ativo=true and papel='PROPRIETARIO'
           """,Integer.class,company,invite.get("criado_por"));
        if(issuerActive==null||issuerActive==0)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Emissor nao autorizado");
        Integer existed=jdbc.queryForObject("""
            select count(*) from stockos_private.membros
            where empresa_id=? and auth_user_id=?
            """,Integer.class,company,actor);
        if(existed!=null&&existed>0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Usuario ja vinculado");
        if(seatsAvailable(company)<=0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Sem vaga no plano");
        jdbc.update("""
            insert into stockos_private.membros(empresa_id,auth_user_id,papel,ativo)
            values (?,?,?,true)
            """,company,actor,invite.get("papel"));
        jdbc.update("""
            update stockos_private.convites_funcionarios
            set status='ACEITO',aceito_por=?,aceito_em=now()
            where empresa_id=? and id=? and status='PENDENTE'
            """,actor,company,invite.get("id"));
        return Map.of("empresaId",company,"authUserId",actor,
                      "papel",invite.get("papel"),"status","ACEITO");
    }
}
