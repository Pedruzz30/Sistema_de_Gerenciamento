package service;

import controller.SessaoFilter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import model.ClasseFuncionario;
import model.Permissao;
import model.SessaoUsuario;
import model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import repository.InMemoryLogRepository;
import repository.InMemorySessaoUsuarioRepository;
import repository.InMemoryUsuarioRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Senhas com hash, sessão no servidor, PIN de operador e limite de tentativas. */
class LoginSeguroNovoTest {

    private InMemoryUsuarioRepository usuarios;
    private InMemorySessaoUsuarioRepository sessoes;
    private SenhaService senhas;
    private AutenticacaoService autenticacao;
    private SessaoService sessaoService;
    private RelogioAjustavel relogio;
    private Usuario ana;
    private Usuario dona;

    private static final class RelogioAjustavel extends Clock {
        private Instant agora = Instant.parse("2026-11-06T12:00:00Z");

        void avancar(Duration d) {
            agora = agora.plus(d);
        }

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return agora; }
    }

    private static ClasseFuncionario classe(int id, Permissao... permissoes) {
        ClasseFuncionario classe = new ClasseFuncionario(id, "CLASSE_" + id, "Teste");
        for (Permissao p : permissoes) {
            classe.adicionarPermissao(p);
        }
        return classe;
    }

    @BeforeEach
    void setUp() {
        usuarios = new InMemoryUsuarioRepository();
        sessoes = new InMemorySessaoUsuarioRepository();
        senhas = new SenhaService();
        relogio = new RelogioAjustavel();
        InMemoryLogRepository logs = new InMemoryLogRepository();
        autenticacao = new AutenticacaoService(usuarios, logs, senhas, new LimiteTentativas(relogio));
        sessaoService = new SessaoService(sessoes, usuarios, autenticacao, logs, Duration.ofHours(14), relogio);
        ana = usuarios.salvar(new Usuario(0, "Ana", "Caixa", "935.411.347-80", "1234",
                classe(4, Permissao.VER_VENDAS, Permissao.VER_ESTOQUE), Usuario.Perfil.OPERADOR));
        dona = usuarios.salvar(new Usuario(0, "Dona", "Loja", "529.982.247-25", "senhaDaDona",
                classe(1, Permissao.values()), Usuario.Perfil.ADMIN));
    }

    private Usuario recarregar(Usuario usuario) {
        return usuarios.buscarPorRu(usuario.getRu()).orElseThrow();
    }

    // ── Senhas ────────────────────────────────────────────────────────────────

    @Test
    void migracaoConverteSenhasAntigasEExigeTroca() {
        assertEquals(2, autenticacao.migrarSenhasAntigas());

        Usuario migrada = recarregar(ana);
        assertTrue(senhas.ehHash(migrada.getSenhaInterna()));
        assertFalse(migrada.getSenhaInterna().contains("1234"));
        assertTrue(migrada.isSenhaProvisoria());
        // Rodar de novo não refaz nada.
        assertEquals(0, autenticacao.migrarSenhasAntigas());
        // A senha antiga ainda entra, e a troca é exigida.
        assertTrue(autenticacao.autenticar("ana", "CAIXA", "1234").isPresent());
    }

    @Test
    void loginComSenhaAntigaConverteNaHora() {
        Usuario entrou = autenticacao.autenticar("Ana", "Caixa", "1234").orElseThrow();

        assertTrue(senhas.ehHash(entrou.getSenhaInterna()));
        assertTrue(entrou.isSenhaProvisoria());
    }

    @Test
    void senhaErradaOuUsuarioInativoNaoEntram() {
        autenticacao.migrarSenhasAntigas();
        assertTrue(autenticacao.autenticar("Ana", "Caixa", "errada").isEmpty());
        assertTrue(autenticacao.autenticar("Ninguem", "Aqui", "1234").isEmpty());
        recarregar(ana).desativar();
        assertTrue(autenticacao.autenticar("Ana", "Caixa", "1234").isEmpty());
    }

    @Test
    void trocaDeSenhaLiberaEValidaAQualidade() {
        autenticacao.migrarSenhasAntigas();

        assertThrows(IllegalArgumentException.class, () -> autenticacao.trocarSenha(recarregar(ana), "errada", "novaSenha1"));
        assertThrows(IllegalArgumentException.class, () -> autenticacao.trocarSenha(recarregar(ana), "1234", "curta"));
        assertThrows(IllegalArgumentException.class, () -> autenticacao.trocarSenha(recarregar(ana), "1234", "123456"));
        assertThrows(IllegalArgumentException.class, () -> autenticacao.trocarSenha(recarregar(ana), "1234", "aaaaaaaa"));

        Usuario trocada = autenticacao.trocarSenha(recarregar(ana), "1234", "novaSenha1");

        assertFalse(trocada.isSenhaProvisoria());
        assertTrue(autenticacao.autenticar("Ana", "Caixa", "1234").isEmpty());
        assertTrue(autenticacao.autenticar("Ana", "Caixa", "novaSenha1").isPresent());
    }

    @Test
    void novoFuncionarioTemSenhaComHashEProvisoria() {
        CadastroFuncionarioService cadastro = new CadastroFuncionarioService(usuarios, new InMemoryLogRepository());
        cadastro.setSenhaService(senhas);
        Usuario gerente = usuarios.salvar(new Usuario(0, "Gerente", "Teste", "111.444.777-35", "x",
                classe(2, Permissao.GERENCIAR_FUNCIONARIOS), Usuario.Perfil.OPERADOR));

        Usuario novo = cadastro.cadastrarFuncionario(gerente, "Lucas", "Ferreira", "123.456.789-09", "temporaria1",
                classe(4, Permissao.VER_VENDAS));

        assertTrue(senhas.ehHash(novo.getSenhaInterna()));
        assertTrue(novo.isSenhaProvisoria());
    }

    @Test
    void limiteBloqueiaDepoisDeCincoErrosEVoltaDepoisDoPrazo() {
        autenticacao.migrarSenhasAntigas();
        for (int i = 0; i < LimiteTentativas.MAX_FALHAS; i++) {
            assertTrue(autenticacao.autenticar("Ana", "Caixa", "errada").isEmpty());
        }

        // Bloqueada mesmo com a senha certa.
        ErroNegocioException erro = assertThrows(ErroNegocioException.class,
                () -> autenticacao.autenticar("Ana", "Caixa", "1234"));
        assertEquals("MUITAS_TENTATIVAS", erro.getCodigo());
        // Bloqueio é por pessoa: outra pessoa continua entrando.
        assertTrue(autenticacao.autenticar("Dona", "Loja", "senhaDaDona").isPresent());

        relogio.avancar(LimiteTentativas.BLOQUEIO.plusSeconds(1));
        assertTrue(autenticacao.autenticar("Ana", "Caixa", "1234").isPresent());
    }

    @Test
    void acertoZeraOContadorDeErros() {
        autenticacao.migrarSenhasAntigas();
        for (int rodada = 0; rodada < 3; rodada++) {
            for (int i = 0; i < LimiteTentativas.MAX_FALHAS - 1; i++) {
                autenticacao.autenticar("Ana", "Caixa", "errada");
            }
            assertTrue(autenticacao.autenticar("Ana", "Caixa", "1234").isPresent());
        }
    }

    // ── PIN ───────────────────────────────────────────────────────────────────

    @Test
    void pinPedeSenhaEFicaComHash() {
        autenticacao.migrarSenhasAntigas();
        assertThrows(IllegalArgumentException.class, () -> autenticacao.definirPin(recarregar(ana), "errada", "4821"));
        for (String ruim : List.of("12", "abcd", "1234", "1111", "1234567")) {
            assertThrows(IllegalArgumentException.class, () -> autenticacao.definirPin(recarregar(ana), "1234", ruim), ruim);
        }

        Usuario comPin = autenticacao.definirPin(recarregar(ana), "1234", "4821");

        assertTrue(senhas.ehHash(comPin.getPinHash()));
        assertFalse(comPin.getPinHash().contains("4821"));
        assertTrue(autenticacao.conferirPin(comPin, "4821"));
        assertFalse(autenticacao.conferirPin(comPin, "4822"));
    }

    @Test
    void pinErradoBloqueiaDepoisDeCincoTentativas() {
        autenticacao.migrarSenhasAntigas();
        Usuario comPin = autenticacao.definirPin(recarregar(ana), "1234", "4821");
        for (int i = 0; i < LimiteTentativas.MAX_FALHAS; i++) {
            assertFalse(autenticacao.conferirPin(comPin, "0000"));
        }
        ErroNegocioException erro = assertThrows(ErroNegocioException.class, () -> autenticacao.conferirPin(comPin, "4821"));
        assertEquals("MUITAS_TENTATIVAS", erro.getCodigo());
    }

    // ── Sessão ────────────────────────────────────────────────────────────────

    @Test
    void sessaoGuardaSoOHashEExpira() {
        String token = sessaoService.criar(ana);
        assertTrue(token.length() >= 40);

        SessaoUsuario sessao = sessaoService.resolver(token).orElseThrow();
        assertNotEquals(token, sessao.getTokenHash());
        assertEquals(ana.getRu(), sessao.getUsuario().getRu());
        assertTrue(sessaoService.resolver("token-inventado").isEmpty());
        assertTrue(sessaoService.resolver(null).isEmpty());

        relogio.avancar(Duration.ofHours(14).plusSeconds(1));
        assertTrue(sessaoService.resolver(token).isEmpty());
    }

    @Test
    void logoutEDesativarEncerramASessao() {
        String token = sessaoService.criar(ana);
        sessaoService.encerrar(token);
        assertTrue(sessaoService.resolver(token).isEmpty());

        String outro = sessaoService.criar(dona);
        recarregar(dona).desativar();
        assertTrue(sessaoService.resolver(outro).isEmpty());
    }

    @Test
    void trocaDeOperadorExigePinCertoEMarcaASessao() {
        autenticacao.migrarSenhasAntigas();
        autenticacao.definirPin(recarregar(dona), "senhaDaDona", "7305");
        String token = sessaoService.criar(recarregar(ana));

        assertThrows(IllegalArgumentException.class, () -> sessaoService.trocarOperador(token, dona.getRu(), "0000"));
        assertEquals(ana.getRu(), sessaoService.resolver(token).orElseThrow().getUsuario().getRu());

        SessaoUsuario sessao = sessaoService.trocarOperador(token, dona.getRu(), "7305");

        assertEquals(dona.getRu(), sessao.getUsuario().getRu());
        assertTrue(sessao.isViaPin());
        // Quem não tem PIN não assume o caixa, nem aparece na lista.
        assertThrows(IllegalArgumentException.class, () -> sessaoService.trocarOperador(token, ana.getRu(), "1234"));
        assertEquals(List.of(dona.getRu()), sessaoService.listarOperadores().stream().map(Usuario::getRu).toList());
    }

    // ── Filtro ────────────────────────────────────────────────────────────────

    private record Resultado(MockHttpServletResponse resposta, HttpServletRequest requisicaoVista) {}

    private Resultado chamar(String metodo, String caminho, String token, String headerForjado, boolean legado) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(metodo, caminho);
        if (token != null) {
            request.setCookies(new Cookie(SessaoFilter.COOKIE_SESSAO, token));
        }
        if (headerForjado != null) {
            request.addHeader("X-User-RU", headerForjado);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        new SessaoFilter(sessaoService, legado).doFilter(request, response, chain);
        return new Resultado(response, (HttpServletRequest) chain.getRequest());
    }

    @Test
    void cabecalhoForjadoSemSessaoERecusado() throws Exception {
        Resultado r = chamar("GET", "/api/produtos", null, String.valueOf(dona.getRu()), false);

        assertEquals(401, r.resposta().getStatus());
        assertNull(r.requisicaoVista());
    }

    @Test
    void cabecalhoForjadoComSessaoESubstituidoPeloDonoDaSessao() throws Exception {
        autenticacao.migrarSenhasAntigas();
        Usuario anaTrocada = autenticacao.trocarSenha(recarregar(ana), "1234", "novaSenha1");
        String token = sessaoService.criar(anaTrocada);

        Resultado r = chamar("GET", "/api/produtos", token, String.valueOf(dona.getRu()), false);

        assertEquals(200, r.resposta().getStatus());
        assertEquals(String.valueOf(ana.getRu()), r.requisicaoVista().getHeader("X-User-RU"));
        assertEquals(List.of(String.valueOf(ana.getRu())),
                java.util.Collections.list(r.requisicaoVista().getHeaders("x-user-ru")));
    }

    @Test
    void rotasPublicasDescartamOCabecalho() throws Exception {
        Resultado r = chamar("POST", "/api/auth/login", null, String.valueOf(dona.getRu()), false);

        assertEquals(200, r.resposta().getStatus());
        assertNull(r.requisicaoVista().getHeader("X-User-RU"));
        assertEquals(200, chamar("GET", "/api/auth/config", null, null, false).resposta().getStatus());
    }

    @Test
    void senhaProvisoriaSoAcessaAsRotasDeLogin() throws Exception {
        autenticacao.migrarSenhasAntigas();
        String token = sessaoService.criar(recarregar(ana));

        MockHttpServletResponse bloqueada = chamar("GET", "/api/caixas", token, null, false).resposta();
        assertEquals(403, bloqueada.getStatus());
        assertTrue(bloqueada.getContentAsString().contains("SENHA_PROVISORIA"));
        assertEquals(200, chamar("POST", "/api/auth/trocar-senha", token, null, false).resposta().getStatus());
    }

    @Test
    void entradaPorPinFicaPresaNaAreaDeCaixaMesmoSendoADona() throws Exception {
        autenticacao.migrarSenhasAntigas();
        Usuario anaOk = autenticacao.trocarSenha(recarregar(ana), "1234", "novaSenha1");
        Usuario donaOk = autenticacao.trocarSenha(recarregar(dona), "senhaDaDona", "outraSenha2");
        autenticacao.definirPin(donaOk, "outraSenha2", "7305");
        String token = sessaoService.criar(anaOk);
        sessaoService.trocarOperador(token, dona.getRu(), "7305");

        for (String[] liberada : List.of(
                new String[]{"GET", "/api/caixas"},
                new String[]{"POST", "/api/caixas/sangria"},
                new String[]{"POST", "/api/vendas"},
                new String[]{"POST", "/api/vendas/1/finalizar"},
                new String[]{"GET", "/api/pdv/codigos/123"},
                new String[]{"GET", "/api/cardapio/itens"},
                new String[]{"POST", "/api/cardapio/variacoes/1/validar-configuracao"},
                new String[]{"POST", "/api/auth/trocar-operador"})) {
            assertEquals(200, chamar(liberada[0], liberada[1], token, null, false).resposta().getStatus(), liberada[1]);
        }
        for (String[] bloqueada : List.of(
                new String[]{"GET", "/api/funcionarios"},
                new String[]{"POST", "/api/produtos"},
                new String[]{"GET", "/api/logs"},
                new String[]{"POST", "/api/notas/importacao"},
                new String[]{"POST", "/api/pdv/codigos"},
                new String[]{"POST", "/api/cardapio/admin/itens"},
                new String[]{"POST", "/api/cardapio/itens/1/variacoes"},
                new String[]{"POST", "/api/produtos/revenda"})) {
            MockHttpServletResponse resposta = chamar(bloqueada[0], bloqueada[1], token, null, false).resposta();
            assertEquals(403, resposta.getStatus(), bloqueada[1]);
            assertTrue(resposta.getContentAsString().contains("ACESSO_RESTRITO_PIN"));
        }
    }

    @Test
    void caminhoComPontoPontoNaoEscapaDaRestricaoDoPin() throws Exception {
        autenticacao.migrarSenhasAntigas();
        Usuario anaOk = autenticacao.trocarSenha(recarregar(ana), "1234", "novaSenha1");
        Usuario donaOk = autenticacao.trocarSenha(recarregar(dona), "senhaDaDona", "outraSenha2");
        autenticacao.definirPin(donaOk, "outraSenha2", "7305");
        String token = sessaoService.criar(anaOk);
        sessaoService.trocarOperador(token, dona.getRu(), "7305");

        // O servidor normaliza a URL: a crua parece caixa, mas o destino é funcionários.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/caixas/../funcionarios");
        request.setServletPath("/api/funcionarios");
        request.setCookies(new Cookie(SessaoFilter.COOKIE_SESSAO, token));
        MockHttpServletResponse response = new MockHttpServletResponse();
        new SessaoFilter(sessaoService, false).doFilter(request, response, new MockFilterChain());
        assertEquals(403, response.getStatus());

        // Nome parecido com uma rota liberada também não passa.
        assertEquals(403, chamar("GET", "/api/caixasFalsa", token, null, false).resposta().getStatus());
        assertEquals(403, chamar("GET", "/api/authFalsa", token, null, false).resposta().getStatus());
    }

    @Test
    void cabecalhoSoValeSemSessaoComOModoDeTestesLigado() throws Exception {
        Resultado r = chamar("GET", "/api/produtos", null, "1", true);

        assertEquals(200, r.resposta().getStatus());
        assertEquals("1", r.requisicaoVista().getHeader("X-User-RU"));
    }
}
