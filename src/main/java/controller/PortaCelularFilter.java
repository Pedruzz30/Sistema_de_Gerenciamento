package controller;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A porta do celular do atendente fica aberta para a rede da loja. Por isso ela só serve a
 * tela de comanda e o pouco que essa tela usa; todo o resto responde "não encontrado". O
 * sistema completo continua só no notebook.
 */
public class PortaCelularFilter extends OncePerRequestFilter {

    private static final Set<String> LEITURA = Set.of(
            "/celular.html", "/css/celular.css", "/js/celular.js", "/vendor/zxing-0.21.3.min.js", "/certificado", "/certificado.mobileconfig",
            "/api/auth/celular/operadores", "/api/auth/celular/sessao",
            "/api/comandas", "/api/pdv/teclas", "/api/pdv/teclas/opcoes");
    private static final Set<String> ESCRITA = Set.of("/api/auth/celular/entrar", "/api/auth/logout");
    private static final Pattern COMANDA = Pattern.compile("/api/comandas/\\d{1,4}");
    private static final Pattern COMANDA_ACAO = Pattern.compile("/api/comandas/\\d{1,4}/(abrir|lancar)");

    private final int porta;

    public PortaCelularFilter(int porta) {
        this.porta = porta;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (porta <= 0 || request.getLocalPort() != porta) {
            chain.doFilter(request, response);
            return;
        }
        // O caminho cru, como chegou: "%2e%2e" ou ";" não batem com nada e caem no "não encontrado".
        String caminho = request.getRequestURI();
        if ("/".equals(caminho) && "GET".equals(request.getMethod())) {
            response.sendRedirect("/celular.html");
            return;
        }
        if (!permitidoNoCelular(caminho, request.getMethod())) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return true;
    }

    static boolean permitidoNoCelular(String caminho, String metodo) {
        if (caminho == null) return false;
        if ("GET".equals(metodo)) {
            return LEITURA.contains(caminho) || COMANDA.matcher(caminho).matches();
        }
        if ("POST".equals(metodo)) {
            return ESCRITA.contains(caminho) || COMANDA_ACAO.matcher(caminho).matches();
        }
        return false;
    }
}
