package integration;

import app.Main;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.ClasseFuncionario;
import model.Permissao;
import model.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import repository.UsuarioRepository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Login no modo de produção: o cabeçalho X-User-RU não vale nada sozinho.
 * Usa usuários próprios para não mudar a senha dos usuários padrão, de que os
 * outros testes dependem.
 */
@SpringBootTest(
        classes = Main.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.port=0", "stockos.auth.aceitar-header-legado=false"}
)
class LoginSeguroApiIntegrationTest {

    private static final AtomicInteger SEQUENCIA = new AtomicInteger();
    private static final String SENHA_INICIAL = "provisoria1";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private service.SenhaService senhaService;

    private record Pessoa(long ru, String nome, String sobrenome) {}

    @Test
    void telaDeLoginConsegueLerAConfiguracaoSemSessao() throws Exception {
        ResponseEntity<String> resposta = get("/api/auth/config", null, null);
        assertEquals(HttpStatus.OK, resposta.getStatusCode());
        assertEquals("local", json(resposta).path("mode").asText());
    }

    @Test
    void cabecalhoForjadoNaoAbreNada() {
        long ruAdmin = adminRu();
        assertEquals(HttpStatus.UNAUTHORIZED, get("/api/produtos", null, ruAdmin).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, get("/api/funcionarios", null, ruAdmin).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, get("/api/caixas", null, ruAdmin).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, get("/api/produtos", "STOCKOS_SESSAO=token-inventado", ruAdmin).getStatusCode());
    }

    @Test
    void senhasFicamComHashENaoApareceNaResposta() throws Exception {
        // O usuário padrão nasce com senha em texto puro e é convertido ao subir o sistema.
        Usuario admin = usuarioRepository.buscarPorRu(adminRu()).orElseThrow();
        assertTrue(senhaService.ehHash(admin.getSenhaInterna()), "Senha do admin sem hash");
        assertTrue(admin.isSenhaProvisoria(), "Senha padrao precisa ser trocada");

        Pessoa pessoa = criarPessoa("VER_VENDAS");
        ResponseEntity<String> login = login(pessoa, SENHA_INICIAL);
        assertFalse(login.getBody().contains("senhaInterna"));
        assertFalse(login.getBody().contains("$2"));
        assertFalse(login.getBody().contains("pinHash"));
    }

    @Test
    void fluxoCompletoDeLoginTrocaDeSenhaEForjaComSessao() throws Exception {
        Pessoa pessoa = criarPessoa("VER_VENDAS");

        assertEquals(HttpStatus.UNAUTHORIZED, login(pessoa, "errada").getStatusCode());

        ResponseEntity<String> login = login(pessoa, SENHA_INICIAL);
        assertEquals(HttpStatus.OK, login.getStatusCode(), login.getBody());
        String setCookie = login.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertTrue(setCookie.contains("HttpOnly"), setCookie);
        assertTrue(setCookie.contains("SameSite=Strict"), setCookie);
        String cookie = setCookie.split(";")[0];
        assertTrue(json(login).path("senhaProvisoria").asBoolean());

        // Senha provisória: só pode trocar a senha.
        ResponseEntity<String> bloqueada = get("/api/caixas", cookie, null);
        assertEquals(HttpStatus.FORBIDDEN, bloqueada.getStatusCode());
        assertEquals("SENHA_PROVISORIA", json(bloqueada).path("codigo").asText());

        assertEquals(HttpStatus.BAD_REQUEST, post("/api/auth/trocar-senha",
                Map.of("senhaAtual", SENHA_INICIAL, "novaSenha", "123"), cookie, null).getStatusCode());
        ResponseEntity<String> trocada = post("/api/auth/trocar-senha",
                Map.of("senhaAtual", SENHA_INICIAL, "novaSenha", "minhaSenhaNova"), cookie, null);
        assertEquals(HttpStatus.OK, trocada.getStatusCode(), trocada.getBody());
        assertFalse(json(trocada).path("senhaProvisoria").asBoolean());

        assertEquals(HttpStatus.OK, get("/api/caixas", cookie, null).getStatusCode());

        // Operadora de caixa tentando se passar pelo Admin: quem vale é a sessão.
        long ruAdmin = adminRu();
        assertEquals(HttpStatus.FORBIDDEN, get("/api/funcionarios", cookie, ruAdmin).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, post("/api/produtos/revenda", Map.of("nome", "Hack"), cookie, ruAdmin).getStatusCode());

        // Depois do logout o mesmo cookie não vale mais.
        assertEquals(HttpStatus.NO_CONTENT, post("/api/auth/logout", Map.of(), cookie, null).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, get("/api/caixas", cookie, null).getStatusCode());

        assertEquals(HttpStatus.UNAUTHORIZED, login(pessoa, SENHA_INICIAL).getStatusCode());
        assertEquals(HttpStatus.OK, login(pessoa, "minhaSenhaNova").getStatusCode());
    }

    @Test
    void trocaDeOperadorPorPinFicaPresaNaAreaDeCaixaEBloqueiaAposErros() throws Exception {
        Pessoa ana = criarPessoa("VER_VENDAS");
        Pessoa gerente = criarPessoa("VER_VENDAS", "VER_FINANCAS", "EDITAR_ESTOQUE", "GERENCIAR_FUNCIONARIOS");
        String cookieGerente = entrarComSenhaDefinitiva(gerente, "senhaDoGerente");
        String cookieAna = entrarComSenhaDefinitiva(ana, "senhaDaAna1");

        // Só com a própria senha se define o PIN.
        assertEquals(HttpStatus.BAD_REQUEST, post("/api/auth/pin",
                Map.of("senhaAtual", "errada", "pin", "7305"), cookieGerente, null).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, post("/api/auth/pin",
                Map.of("senhaAtual", "senhaDoGerente", "pin", "1234"), cookieGerente, null).getStatusCode());
        ResponseEntity<String> comPin = post("/api/auth/pin",
                Map.of("senhaAtual", "senhaDoGerente", "pin", "7305"), cookieGerente, null);
        assertEquals(HttpStatus.OK, comPin.getStatusCode(), comPin.getBody());
        assertTrue(json(comPin).path("temPin").asBoolean());

        // A lista traz só nome e RU.
        ResponseEntity<String> lista = get("/api/auth/operadores", cookieAna, null);
        assertEquals(HttpStatus.OK, lista.getStatusCode());
        assertTrue(lista.getBody().contains(gerente.nome()));
        assertFalse(lista.getBody().contains("pinHash"));
        assertFalse(lista.getBody().contains(ana.nome()));

        assertEquals(HttpStatus.BAD_REQUEST, post("/api/auth/trocar-operador",
                Map.of("ru", gerente.ru(), "pin", "0000"), cookieAna, null).getStatusCode());

        ResponseEntity<String> trocado = post("/api/auth/trocar-operador",
                Map.of("ru", gerente.ru(), "pin", "7305"), cookieAna, null);
        assertEquals(HttpStatus.OK, trocado.getStatusCode(), trocado.getBody());
        assertEquals(gerente.ru(), json(trocado).path("ru").asLong());
        assertTrue(json(trocado).path("viaPin").asBoolean());

        // A sessão agora é do gerente, mas só na área de caixa.
        assertEquals(HttpStatus.OK, get("/api/caixas", cookieAna, null).getStatusCode());
        ResponseEntity<String> restrita = get("/api/funcionarios", cookieAna, null);
        assertEquals(HttpStatus.FORBIDDEN, restrita.getStatusCode());
        assertEquals("ACESSO_RESTRITO_PIN", json(restrita).path("codigo").asText());

        // Entrar com a senha devolve o acesso completo.
        assertEquals(HttpStatus.OK, get("/api/funcionarios", cookieGerente, null).getStatusCode());

        // Cinco erros bloqueiam o PIN, mesmo depois com o PIN certo.
        for (int i = 0; i < 4; i++) {
            assertEquals(HttpStatus.BAD_REQUEST, post("/api/auth/trocar-operador",
                    Map.of("ru", gerente.ru(), "pin", "0000"), cookieGerente, null).getStatusCode());
        }
        assertEquals(HttpStatus.OK, post("/api/auth/trocar-operador",
                Map.of("ru", gerente.ru(), "pin", "7305"), cookieGerente, null).getStatusCode());
        for (int i = 0; i < 5; i++) {
            assertEquals(HttpStatus.BAD_REQUEST, post("/api/auth/trocar-operador",
                    Map.of("ru", gerente.ru(), "pin", "0000"), cookieAna, null).getStatusCode());
        }
        ResponseEntity<String> bloqueado = post("/api/auth/trocar-operador",
                Map.of("ru", gerente.ru(), "pin", "7305"), cookieAna, null);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, bloqueado.getStatusCode(), bloqueado.getBody());
    }

    @Test
    void cincoSenhasErradasBloqueiamOLogin() {
        Pessoa pessoa = criarPessoa("VER_VENDAS");
        for (int i = 0; i < 5; i++) {
            assertEquals(HttpStatus.UNAUTHORIZED, login(pessoa, "errada").getStatusCode());
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, login(pessoa, SENHA_INICIAL).getStatusCode());
    }

    @Test
    void autorizacaoDoGerenteConfereOHash() throws Exception {
        Pessoa ana = criarPessoa("VER_VENDAS");
        Pessoa gerente = criarPessoa("VER_VENDAS", "VER_FINANCAS");
        String cookieAna = entrarComSenhaDefinitiva(ana, "senhaDaAna1");
        entrarComSenhaDefinitiva(gerente, "senhaDoGerente");
        int numeroCaixa = 100_000 + (int) Math.floorMod(System.nanoTime(), 1_000_000_000L);

        assertEquals(HttpStatus.OK, post("/api/caixas/abrir",
                Map.of("numeroCaixa", numeroCaixa, "saldoInicial", 100), cookieAna, null).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, post("/api/caixas/sangria", Map.of(
                "numeroCaixa", numeroCaixa, "valor", 10, "descricao", "Cofre",
                "autorizacao", Map.of("nome", gerente.nome(), "sobrenome", gerente.sobrenome(), "senha", "errada")
        ), cookieAna, null).getStatusCode());
        ResponseEntity<String> autorizada = post("/api/caixas/sangria", Map.of(
                "numeroCaixa", numeroCaixa, "valor", 10, "descricao", "Cofre",
                "autorizacao", Map.of("nome", gerente.nome(), "sobrenome", gerente.sobrenome(), "senha", "senhaDoGerente")
        ), cookieAna, null);
        assertEquals(HttpStatus.OK, autorizada.getStatusCode(), autorizada.getBody());
    }

    private String entrarComSenhaDefinitiva(Pessoa pessoa, String senhaNova) {
        ResponseEntity<String> login = login(pessoa, SENHA_INICIAL);
        assertEquals(HttpStatus.OK, login.getStatusCode(), login.getBody());
        String cookie = login.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";")[0];
        ResponseEntity<String> trocada = post("/api/auth/trocar-senha",
                Map.of("senhaAtual", SENHA_INICIAL, "novaSenha", senhaNova), cookie, null);
        assertEquals(HttpStatus.OK, trocada.getStatusCode(), trocada.getBody());
        return cookie;
    }

    /** Pessoa nova, com nome e CPF únicos: o banco de teste persiste entre as classes. */
    private Pessoa criarPessoa(String... permissoes) {
        ClasseFuncionario classe = new ClasseFuncionario(90, "TESTE_LOGIN", "Teste de login");
        for (String permissao : permissoes) {
            classe.adicionarPermissao(Permissao.valueOf(permissao));
        }
        int seq = SEQUENCIA.incrementAndGet();
        String unico = letras(System.nanoTime()) + letras(seq);
        Usuario nova = new Usuario(0, "Pessoa" + unico, "Teste", cpfValido(), SENHA_INICIAL, classe, Usuario.Perfil.OPERADOR);
        nova.definirSenha(senhaService.gerarHash(SENHA_INICIAL), true);
        Usuario salvo = usuarioRepository.salvar(nova);
        return new Pessoa(salvo.getRu(), salvo.getNome(), salvo.getSobrenome());
    }

    private static String letras(long numero) {
        StringBuilder texto = new StringBuilder();
        long resto = Math.abs(numero);
        do {
            texto.append((char) ('a' + resto % 26));
            resto /= 26;
        } while (resto > 0);
        return texto.toString();
    }

    private static String cpfValido() {
        String tempo = String.valueOf(System.nanoTime() + SEQUENCIA.incrementAndGet());
        String base = tempo.substring(tempo.length() - 9);
        int[] digitos = new int[11];
        for (int i = 0; i < 9; i++) {
            digitos[i] = base.charAt(i) - '0';
        }
        for (int posicao = 9; posicao <= 10; posicao++) {
            int soma = 0;
            for (int i = 0; i < posicao; i++) {
                soma += digitos[i] * (posicao + 1 - i);
            }
            int resto = soma % 11;
            digitos[posicao] = resto < 2 ? 0 : 11 - resto;
        }
        StringBuilder cpf = new StringBuilder();
        for (int digito : digitos) {
            cpf.append(digito);
        }
        return cpf.toString();
    }

    private long adminRu() {
        return usuarioRepository.listarTodos().stream()
                .filter(usuario -> "52998224725".equals(usuario.getCpfBruto()))
                .findFirst()
                .orElseThrow()
                .getRu();
    }

    private ResponseEntity<String> login(Pessoa pessoa, String senha) {
        return post("/api/auth/login",
                Map.of("nome", pessoa.nome(), "sobrenome", pessoa.sobrenome(), "senha", senha), null, null);
    }

    private JsonNode json(ResponseEntity<String> resposta) throws Exception {
        return objectMapper.readTree(resposta.getBody());
    }

    private ResponseEntity<String> get(String path, String cookie, Long ruForjado) {
        return restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers(cookie, ruForjado)), String.class);
    }

    private ResponseEntity<String> post(String path, Object body, String cookie, Long ruForjado) {
        return restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, headers(cookie, ruForjado)), String.class);
    }

    private static HttpHeaders headers(String cookie, Long ruForjado) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (cookie != null) {
            headers.add(HttpHeaders.COOKIE, cookie);
        }
        if (ruForjado != null) {
            headers.set("X-User-RU", String.valueOf(ruForjado));
        }
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
