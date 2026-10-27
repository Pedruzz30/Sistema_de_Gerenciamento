package integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import service.SaasComandaService;
import service.SaasCashService;
import service.SaasPostCloseRefundService;
import service.SaasStockReturnService;
import service.SaasInventoryAdjustmentService;
import service.SaasLicenseService;
import service.SaasTenantAccess;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PostgreSQL-only SaaS tests. Run against a disposable local CI database,
 * NEVER a Supabase project or a database with business records.
 */
@EnabledIfEnvironmentVariable(named = "STOCKOS_TEST_DB_URL",
        matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1):5432/stockos_ci_test")
class SaasComandaPostgresIntegrationTest {
    private static JdbcTemplate jdbc;
    private static TransactionTemplate tx;
    private static SaasComandaService comandas;
    private static SaasCashService caixa;
    private static SaasPostCloseRefundService refunds;
    private static SaasStockReturnService returns;
    private static SaasInventoryAdjustmentService stocktake;
    private static SaasTenantAccess tenant;
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID OUTSIDER = UUID.randomUUID();

    @BeforeAll
    static void initialize() {
        String url=System.getenv("STOCKOS_TEST_DB_URL");
        if(url==null || !url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):5432/stockos_ci_test"))
            throw new IllegalStateException("Somente PostgreSQL local descartavel e permitido");
        DriverManagerDataSource ds=new DriverManagerDataSource(
                url,System.getenv("DB_USERNAME"),System.getenv("DB_PASSWORD"));
        Flyway flyway=Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .load();
        flyway.migrate();
        jdbc=new JdbcTemplate(ds);
        tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        tenant=new SaasTenantAccess(jdbc,new SaasLicenseService(jdbc));
        comandas=new SaasComandaService(jdbc,tenant);
        caixa=new SaasCashService(jdbc,tenant);
        refunds=new SaasPostCloseRefundService(jdbc,tenant,caixa);
        returns=new SaasStockReturnService(jdbc,tenant);
        stocktake=new SaasInventoryAdjustmentService(jdbc,tenant);
    }

    private static void login(UUID id){
        Jwt jwt=Jwt.withTokenValue("integration-jwt").header("alg","RS256")
                .subject(id.toString()).claim("role","authenticated").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_AUTHENTICATED"))));
    }

    private static Fixture fixture(int stock,int price){
        UUID company=UUID.randomUUID(),branch=UUID.randomUUID(),product=UUID.randomUUID();
        UUID plan=UUID.randomUUID();
        jdbc.update("insert into stockos_private.empresas(id,nome) values (?,?)",company,"Loja "+company);
        jdbc.update("insert into stockos_private.filiais(id,empresa_id,nome) values (?,?,'Principal')",branch,company);
        jdbc.update("insert into stockos_private.membros(empresa_id,auth_user_id,papel) values (?,?,'PROPRIETARIO')",company,OWNER);
        jdbc.update("""
            insert into stockos_private.planos
            (id,codigo,nome,preco_mensal,max_filiais,max_usuarios)
            values (?,?,?,?,1,10)
            """,plan,"t_"+company.toString().replace("-","").substring(0,28),"Teste",BigDecimal.ZERO);
        jdbc.update("""
            insert into stockos_private.licencas
            (empresa_id,plano_id,status,vigencia_inicio,vigencia_fim,valor_mensal_snapshot)
            values (?,?,'TRIAL',now()-interval '1 day',now()+interval '1 day',0)
            """,company,plan);
        jdbc.update("""
            insert into stockos_private.produtos
            (id,empresa_id,filial_id,codigo,nome,preco_unitario,estoque_atual)
            values (?,?,?,'A001','Agua',?,?)
            """,product,company,branch,BigDecimal.valueOf(price),stock);
        return new Fixture(company,branch,product);
    }

    private record Fixture(UUID company,UUID branch,UUID product) {}

    private static UUID id(Map<String,Object> row){return (UUID)row.get("id");}

    @Test
    void migrationCreatesPrivateSaasTablesAndRejectsWrongTenantLinks(){
        login(OWNER);
        Fixture a=fixture(10,5), b=fixture(10,5);
        assertEquals(1,jdbc.queryForObject("""
            select count(*) from information_schema.tables
            where table_schema='stockos_private' and table_name='comandas'
            """,Integer.class));
        // Composite FK forbids attaching an item to a product in a different store.
        UUID tab=id(tx.execute(t->comandas.abrir(a.company,a.branch,1)));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("""
            insert into stockos_private.comanda_itens
            (empresa_id,filial_id,comanda_id,produto_id,nome_snapshot,
             quantidade,preco_unitario_snapshot,idempotency_key,criado_por)
            values (?,?,?,?,'Bloqueado',1,5,?,?)
            """,a.company,a.branch,tab,b.product,UUID.randomUUID().toString(),OWNER));
    }

    @Test
    void rejectsCrossCompanyAccessAndInactiveSubscription() {
        login(OWNER);
        Fixture a=fixture(3,8);
        login(OUTSIDER);
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(ResponseStatusException.class,
                ()->comandas.listar(a.company,a.branch)).getStatusCode());
        login(OWNER);
        jdbc.update("update stockos_private.licencas set status='SUSPENSA' where empresa_id=?",a.company);
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(ResponseStatusException.class,
                ()->comandas.listar(a.company,a.branch)).getStatusCode());
    }

    @Test
    void repeatedItemAndPaymentKeysNeverDoubleBook() {
        login(OWNER);
        Fixture a=fixture(5,12);
        UUID tab=id(tx.execute(t->comandas.abrir(a.company,a.branch,1)));
        tx.execute(t->caixa.abrir(a.company,a.branch,new BigDecimal("50.00")));
        UUID key=UUID.randomUUID();
        Map<String,Object> first=tx.execute(t->comandas.adicionar(a.company,a.branch,tab,a.product,2,key.toString()));
        Map<String,Object> second=tx.execute(t->comandas.adicionar(a.company,a.branch,tab,a.product,2,key.toString()));
        assertEquals(first.get("id"),second.get("id"));
        assertEquals(3,stock(a));
        assertEquals(1,movements(a,"ENTREGA"));
        assertEquals(HttpStatus.CONFLICT,assertThrows(ResponseStatusException.class,
                ()->tx.execute(t->comandas.adicionar(a.company,a.branch,tab,a.product,3,key.toString())))
                .getStatusCode());
        String paymentKey=UUID.randomUUID().toString();
        tx.execute(t->comandas.pagamento(a.company,a.branch,tab,"PIX",new BigDecimal("10.00"),paymentKey));
        tx.execute(t->comandas.pagamento(a.company,a.branch,tab,"PIX",new BigDecimal("10.00"),paymentKey));
        assertEquals(1,jdbc.queryForObject("""
            select count(*) from stockos_private.pagamentos
            where empresa_id=? and idempotency_key=?
            """,Integer.class,a.company,paymentKey));
        assertEquals(HttpStatus.CONFLICT,assertThrows(ResponseStatusException.class,
                ()->tx.execute(t->comandas.fechar(a.company,a.branch,tab))).getStatusCode());
        tx.execute(t->comandas.pagamento(a.company,a.branch,tab,"DINHEIRO",new BigDecimal("14.00"),UUID.randomUUID().toString()));
        assertEquals("FECHADA",tx.execute(t->comandas.fechar(a.company,a.branch,tab)).get("status"));
        assertEquals(3,stock(a)); // Closing must not decrement delivered stock again.
    }

    @Test
    void cashShiftSeparatesPhysicalMoneyFromElectronicReceipts() {
        login(OWNER);
        Fixture f=fixture(12,10);
        UUID tab=id(tx.execute(t->comandas.abrir(f.company,f.branch,1)));
        assertEquals(HttpStatus.CONFLICT,assertThrows(ResponseStatusException.class,()->
                tx.execute(t->{
                    comandas.adicionar(f.company,f.branch,tab,f.product,2,UUID.randomUUID().toString());
                    return comandas.pagamento(f.company,f.branch,tab,"PIX",
                            new BigDecimal("10.00"),UUID.randomUUID().toString());
                })).getStatusCode());
        assertEquals(12,stock(f),"Rollback must restore stock when payment fails without shift");
        tx.execute(t->caixa.abrir(f.company,f.branch,new BigDecimal("50.00")));
        tx.execute(t->comandas.adicionar(f.company,f.branch,tab,f.product,2,UUID.randomUUID().toString()));
        tx.execute(t->comandas.pagamento(f.company,f.branch,tab,"PIX",
                new BigDecimal("10.00"),UUID.randomUUID().toString()));
        tx.execute(t->comandas.pagamento(f.company,f.branch,tab,"DINHEIRO",
                new BigDecimal("10.00"),UUID.randomUUID().toString()));
        Map<String,Object> status=caixa.atual(f.company,f.branch);
        assertEquals(0,new BigDecimal("60.00").compareTo((BigDecimal)status.get("dinheiro_esperado")));
        assertEquals(0,new BigDecimal("10.00").compareTo((BigDecimal)status.get("pix")));
        tx.execute(t->caixa.movimentar(f.company,f.branch,"SANGRIA",
                new BigDecimal("5.00"),"Deposito bancario",UUID.randomUUID().toString()));
        Map<String,Object> closed=tx.execute(t->caixa.fechar(f.company,f.branch,new BigDecimal("53.00")));
        assertEquals(0,new BigDecimal("55.00").compareTo((BigDecimal)closed.get("valor_esperado")));
        assertEquals(0,new BigDecimal("-2.00").compareTo((BigDecimal)closed.get("diferenca")));
        assertEquals("FECHADO",closed.get("status"));
        assertEquals(HttpStatus.CONFLICT,assertThrows(ResponseStatusException.class,()->
                tx.execute(t->caixa.fechar(f.company,f.branch,BigDecimal.ZERO))).getStatusCode());
    }

    @Test
    void fullPaymentReversalKeepsOriginalAndUpdatesCash() {
        login(OWNER);
        Fixture f=fixture(10,20);
        UUID tab=id(tx.execute(t->comandas.abrir(f.company,f.branch,1)));
        tx.execute(t->caixa.abrir(f.company,f.branch,new BigDecimal("30.00")));
        tx.execute(t->comandas.adicionar(f.company,f.branch,tab,f.product,1,UUID.randomUUID().toString()));
        UUID payment=id(tx.execute(t->comandas.pagamento(f.company,f.branch,tab,"DINHEIRO",new BigDecimal("20.00"),UUID.randomUUID().toString())));
        String key=UUID.randomUUID().toString();
        Map<String,Object> first=tx.execute(t->comandas.estornarPagamento(f.company,f.branch,tab,payment,"Devolucao integral ao cliente",key));
        Map<String,Object> second=tx.execute(t->comandas.estornarPagamento(f.company,f.branch,tab,payment,"Devolucao integral ao cliente",key));
        assertEquals(first.get("id"),second.get("id"));
        assertEquals(1,jdbc.queryForObject("select count(*) from stockos_private.pagamentos where id=?",Integer.class,payment));
        assertEquals(1,jdbc.queryForObject("select count(*) from stockos_private.pagamento_estornos where pagamento_id=?",Integer.class,payment));
        assertEquals(0,new BigDecimal("30.00").compareTo((BigDecimal)caixa.atual(f.company,f.branch).get("dinheiro_esperado")));
        assertEquals(HttpStatus.CONFLICT,assertThrows(ResponseStatusException.class,
            ()->tx.execute(t->comandas.fechar(f.company,f.branch,tab))).getStatusCode());
    }

    @Test
    void closedSaleRefundUsesNewShiftAndKeepsOldDrawerUnchanged() {
        login(OWNER);
        Fixture f=fixture(5,20);
        UUID tab=id(tx.execute(t->comandas.abrir(f.company,f.branch,1)));
        tx.execute(t->caixa.abrir(f.company,f.branch,new BigDecimal("50.00")));
        tx.execute(t->comandas.adicionar(f.company,f.branch,tab,f.product,1,UUID.randomUUID().toString()));
        UUID payment=id(tx.execute(t->comandas.pagamento(f.company,f.branch,tab,"DINHEIRO",
                new BigDecimal("20.00"),UUID.randomUUID().toString())));
        tx.execute(t->comandas.fechar(f.company,f.branch,tab));
        Map<String,Object> old=tx.execute(t->caixa.fechar(f.company,f.branch,new BigDecimal("70.00")));
        assertEquals(0,new BigDecimal("70.00").compareTo((BigDecimal)old.get("valor_esperado")));
        tx.execute(t->caixa.abrir(f.company,f.branch,new BigDecimal("30.00")));
        String key=UUID.randomUUID().toString();
        Map<String,Object> first=tx.execute(t->refunds.registrar(f.company,f.branch,tab,payment,
            "Cliente solicitou devolucao","COMPROVANTE-TESTE-001",true,key));
        Map<String,Object> replay=tx.execute(t->refunds.registrar(f.company,f.branch,tab,payment,
            "Cliente solicitou devolucao","COMPROVANTE-TESTE-001",true,key));
        assertEquals(first.get("id"),replay.get("id"));
        assertEquals(0,new BigDecimal("10.00").compareTo(
                (BigDecimal)caixa.atual(f.company,f.branch).get("dinheiro_esperado")));
        assertEquals(0,new BigDecimal("70.00").compareTo(jdbc.queryForObject("""
            select valor_esperado from stockos_private.caixa_turnos where id=?
            """,BigDecimal.class,old.get("id"))));
        assertEquals(HttpStatus.CONFLICT,assertThrows(ResponseStatusException.class,
            ()->tx.execute(t->refunds.registrar(f.company,f.branch,tab,payment,
                "Cliente solicitou devolucao","COMPROVANTE-TESTE-002",true,UUID.randomUUID().toString()))).getStatusCode());
    }

    @Test
    void physicalReturnsSeparateRestockFromDiscardAndRespectSoldQuantity(){
        login(OWNER);
        Fixture f=fixture(8,12);
        UUID tab=id(tx.execute(t->comandas.abrir(f.company,f.branch,1)));
        tx.execute(t->caixa.abrir(f.company,f.branch,BigDecimal.ZERO));
        UUID item=id(tx.execute(t->comandas.adicionar(f.company,f.branch,tab,f.product,3,UUID.randomUUID().toString())));
        tx.execute(t->comandas.pagamento(f.company,f.branch,tab,"PIX",new BigDecimal("36.00"),UUID.randomUUID().toString()));
        tx.execute(t->comandas.fechar(f.company,f.branch,tab));
        assertEquals(5,stock(f));
        String key=UUID.randomUUID().toString();
        Map<String,Object> restocked=tx.execute(t->returns.registrar(f.company,f.branch,tab,item,1,
                "REESTOQUE","Produto lacrado devolvido",key));
        assertEquals(restocked.get("id"),tx.execute(t->returns.registrar(f.company,f.branch,tab,item,1,
                "REESTOQUE","Produto lacrado devolvido",key)).get("id"));
        assertEquals(6,stock(f));
        tx.execute(t->returns.registrar(f.company,f.branch,tab,item,2,
                "DESCARTE","Embalagem aberta e avariada",UUID.randomUUID().toString()));
        assertEquals(6,stock(f));
        assertEquals(HttpStatus.CONFLICT,assertThrows(ResponseStatusException.class,
                ()->tx.execute(t->returns.registrar(f.company,f.branch,tab,item,1,
                        "REESTOQUE","Tentativa de quarta unidade",UUID.randomUUID().toString()))).getStatusCode());
        assertEquals(2,returns.historico(f.company,f.branch,tab).size());
    }

    @Test
    void inventoryCountIsAuditedIdempotentAndRejectsStaleBalance() {
        login(OWNER);
        Fixture f=fixture(12,10);
        String key=UUID.randomUUID().toString();
        Map<String,Object> first=tx.execute(t->stocktake.conferir(f.company,f.branch,f.product,
                12,9,"Diferenca na contagem fisica",key));
        assertEquals(first.get("id"),tx.execute(t->stocktake.conferir(f.company,f.branch,f.product,
                12,9,"Diferenca na contagem fisica",key)).get("id"));
        assertEquals(9,stock(f));
        assertEquals(HttpStatus.CONFLICT,assertThrows(ResponseStatusException.class,
                ()->tx.execute(t->stocktake.conferir(f.company,f.branch,f.product,
                        12,4,"Contagem desatualizada na prateleira",UUID.randomUUID().toString())))
                .getStatusCode());
        tx.execute(t->stocktake.conferir(f.company,f.branch,f.product,
                9,9,"Conferencia posterior sem diferenca",UUID.randomUUID().toString()));
        assertEquals(2,stocktake.historico(f.company,f.branch,50).size());
    }

    @Test
    void concurrentAttemptsToSellLastUnitOnlyAllowOne() throws Exception {
        login(OWNER);
        Fixture a=fixture(1,10);
        UUID tab=id(tx.execute(t->comandas.abrir(a.company,a.branch,1)));
        CountDownLatch start=new CountDownLatch(1);
        try(ExecutorService pool=Executors.newFixedThreadPool(2)){
            Future<Boolean> f1=pool.submit(()->attemptSale(a,tab,start));
            Future<Boolean> f2=pool.submit(()->attemptSale(a,tab,start));
            start.countDown();
            boolean one=f1.get(15,TimeUnit.SECONDS),two=f2.get(15,TimeUnit.SECONDS);
            assertNotEquals(one,two,"Exactly one sale must consume the final unit");
            assertEquals(0,stock(a));
            assertEquals(1,movements(a,"ENTREGA"));
        }
    }

    private static boolean attemptSale(Fixture a,UUID tab,CountDownLatch start) {
        login(OWNER);
        try {
            assertTrue(start.await(8,TimeUnit.SECONDS));
            tx.execute(t->comandas.adicionar(a.company,a.branch,tab,a.product,1,UUID.randomUUID().toString()));
            return true;
        } catch(ResponseStatusException ex) {
            if(ex.getStatusCode()!=HttpStatus.CONFLICT)throw ex;
            return false;
        } catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        } finally {SecurityContextHolder.clearContext();}
    }

    private static int stock(Fixture a){
        return jdbc.queryForObject("""
          select estoque_atual from stockos_private.produtos
          where empresa_id=? and filial_id=? and id=?
          """,Integer.class,a.company,a.branch,a.product);
    }
    private static int movements(Fixture a,String type){
        return jdbc.queryForObject("""
          select count(*) from stockos_private.movimentos_estoque
          where empresa_id=? and filial_id=? and tipo=?
          """,Integer.class,a.company,a.branch,type);
    }
}
