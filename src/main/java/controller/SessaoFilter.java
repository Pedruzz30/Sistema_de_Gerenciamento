package controller;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import model.SessaoUsuario;
import org.springframework.web.filter.OncePerRequestFilter;
import service.SessaoService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Porta de entrada de {@code /api/*}: a identidade vem da sessão no cookie.
 *
 * <p>Os controllers continuam lendo o cabeçalho {@code X-User-RU}, mas o valor
 * agora é posto aqui, a partir da sessão, e o que o cliente enviar é descartado.
 * Assim todos os endpoints ficam protegidos sem precisar mudar um por um.
 *
 * <p>{@code stockos.auth.aceitar-header-legado=true} só existe para os testes
 * automatizados; nunca ligue em uma loja.
 */
public class SessaoFilter extends OncePerRequestFilter {

    public static final String COOKIE_SESSAO = "STOCKOS_SESSAO";
    private static final String HEADER_USUARIO = "X-User-RU";
    private static final Set<String> ROTAS_PUBLICAS = Set.of("/api/auth/login", "/api/auth/config");

    private final SessaoService sessaoService;
    private final boolean aceitarHeaderLegado;

    public SessaoFilter(SessaoService sessaoService, boolean aceitarHeaderLegado) {
        this.sessaoService = sessaoService;
        this.aceitarHeaderLegado = aceitarHeaderLegado;
        if (aceitarHeaderLegado) {
            logger.warn("stockos.auth.aceitar-header-legado ligado: qualquer um pode se passar por outro usuario. Use so em testes.");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String caminho = caminhoNormalizado(request);
        if (ROTAS_PUBLICAS.contains(caminho)) {
            chain.doFilter(new RequisicaoComUsuario(request, null), response);
            return;
        }

        Optional<SessaoUsuario> sessao = sessaoService.resolver(tokenDoCookie(request));
        if (sessao.isEmpty()) {
            if (aceitarHeaderLegado) {
                chain.doFilter(request, response);
                return;
            }
            recusar(response, HttpServletResponse.SC_UNAUTHORIZED, "NAO_AUTENTICADO",
                    "Sessao encerrada. Entre de novo.");
            return;
        }

        boolean rotaDeAuth = dentroDe(caminho, "/api/auth");
        if (sessao.get().getUsuario().isSenhaProvisoria() && !rotaDeAuth) {
            recusar(response, HttpServletResponse.SC_FORBIDDEN, "SENHA_PROVISORIA",
                    "Crie uma senha nova para continuar.");
            return;
        }
        if (sessao.get().isViaPin() && !permitidoPorPin(caminho, request.getMethod())) {
            recusar(response, HttpServletResponse.SC_FORBIDDEN, "ACESSO_RESTRITO_PIN",
                    "Quem entrou por PIN usa so o caixa. Entre com a senha para abrir esta area.");
            return;
        }
        chain.doFilter(new RequisicaoComUsuario(request, String.valueOf(sessao.get().getUsuario().getRu())), response);
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return true;
    }

    /** Área de caixa: o que o PDV precisa para vender. */
    static boolean permitidoPorPin(String caminho, String metodo) {
        if (dentroDe(caminho, "/api/auth") || dentroDe(caminho, "/api/caixas") || dentroDe(caminho, "/api/vendas")) {
            return true;
        }
        boolean leitura = "GET".equalsIgnoreCase(metodo);
        if (dentroDe(caminho, "/api/pdv/codigos")) {
            return leitura;
        }
        // Cadastro feito do caixa: quem não pode cadastrar precisa do login do gerente no pedido.
        if (dentroDe(caminho, "/api/pdv/cadastro")) {
            return "POST".equalsIgnoreCase(metodo);
        }
        if (dentroDe(caminho, "/api/cardapio/admin")) {
            return false;
        }
        // Validar a configuração do item é um POST que não grava nada.
        return dentroDe(caminho, "/api/cardapio") && (leitura || caminho.endsWith("/validar-configuracao"));
    }

    private static boolean dentroDe(String caminho, String prefixo) {
        return caminho.equals(prefixo) || caminho.startsWith(prefixo + "/");
    }

    /**
     * O caminho que o servidor vai mesmo atender. A URL crua não serve:
     * "/api/caixas/../funcionarios" começa com /api/caixas, mas chega em /api/funcionarios.
     */
    private static String caminhoNormalizado(HttpServletRequest request) {
        String caminho = request.getServletPath() == null ? "" : request.getServletPath();
        if (request.getPathInfo() != null) {
            caminho += request.getPathInfo();
        }
        return caminho.isEmpty() ? request.getRequestURI() : caminho;
    }

    public static String tokenDoCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_SESSAO.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static void recusar(HttpServletResponse response, int status, String codigo, String mensagem) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"erro\":\"" + mensagem + "\",\"codigo\":\"" + codigo + "\",\"detalhes\":{}}");
    }

    /** Substitui o X-User-RU do cliente pelo da sessão (ou remove, se {@code ru} for nulo). */
    private static final class RequisicaoComUsuario extends HttpServletRequestWrapper {
        private final String ru;

        private RequisicaoComUsuario(HttpServletRequest request, String ru) {
            super(request);
            this.ru = ru;
        }

        @Override
        public String getHeader(String nome) {
            return HEADER_USUARIO.equalsIgnoreCase(nome) ? ru : super.getHeader(nome);
        }

        @Override
        public Enumeration<String> getHeaders(String nome) {
            if (!HEADER_USUARIO.equalsIgnoreCase(nome)) {
                return super.getHeaders(nome);
            }
            return ru == null ? Collections.emptyEnumeration() : Collections.enumeration(List.of(ru));
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> nomes = new ArrayList<>(Collections.list(super.getHeaderNames()));
            nomes.removeIf(HEADER_USUARIO::equalsIgnoreCase);
            if (ru != null) {
                nomes.add(HEADER_USUARIO);
            }
            return Collections.enumeration(nomes);
        }
    }
}
