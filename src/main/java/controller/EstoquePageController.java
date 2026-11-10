
package controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Gerencia os redirecionamentos das rotas de paginas
 * do StockOS para seus respectivos arquivos HTML.
 *
 * Exemplos:
 *
 * GET /           -> /dashboard.html
 * GET /dashboard  -> /dashboard.html
 * GET /estoque    -> /estoque.html
 * GET /cardapio   -> /cardapio.html
 * GET /login      -> /login.html
 *
 * As paginas estao localizadas em:
 *
 * src/main/resources/static/
 *
 * O Spring Boot disponibiliza esses arquivos
 * utilizando seu mecanismo de recursos estaticos.
 *
 * IMPORTANTE:
 *
 * Este controller nao realiza autenticacao
 * nem verifica permissoes.
 *
 * Proteger apenas estas rotas nao seria suficiente,
 * porque os arquivos .html podem ser acessados
 * diretamente.
 *
 * A seguranca precisa ser aplicada no backend
 * por filtros e regras de autorizacao.
 */
@Controller
public class EstoquePageController {

    // =====================================================
    // DESTINOS
    // =====================================================

    private static final String DASHBOARD =
            "redirect:/dashboard.html";

    private static final String CARDAPIO =
            "redirect:/cardapio.html";

    private static final String ESTOQUE =
            "redirect:/estoque.html";

    private static final String FORNECEDORES =
            "redirect:/fornecedores.html";

    private static final String NOTAS =
            "redirect:/notas.html";

    private static final String CAIXAS =
            "redirect:/caixas.html";

    private static final String COTACOES =
            "redirect:/cotacoes.html";

    private static final String FUNCIONARIOS =
            "redirect:/funcionarios.html";

    private static final String LOGS =
            "redirect:/logs.html";

    private static final String LOGIN =
            "redirect:/login.html";

    // =====================================================
    // PAGINA INICIAL
    // =====================================================

    /**
     * GET /
     *
     * Redireciona a raiz para o dashboard.
     */
    @GetMapping("/")
    public String raiz() {
        return DASHBOARD;
    }

    /**
     * GET /dashboard
     */
    @GetMapping("/dashboard")
    public String dashboard() {
        return DASHBOARD;
    }

    // =====================================================
    // CARDAPIO
    // =====================================================

    /**
     * GET /cardapio
     */
    @GetMapping("/cardapio")
    public String cardapio() {
        return CARDAPIO;
    }

    // =====================================================
    // ESTOQUE
    // =====================================================

    /**
     * GET /estoque
     */
    @GetMapping("/estoque")
    public String estoque() {
        return ESTOQUE;
    }

    // =====================================================
    // FORNECEDORES
    // =====================================================

    /**
     * GET /fornecedores
     */
    @GetMapping("/fornecedores")
    public String fornecedores() {
        return FORNECEDORES;
    }

    // =====================================================
    // NOTAS
    // =====================================================

    /**
     * GET /notas
     */
    @GetMapping("/notas")
    public String notas() {
        return NOTAS;
    }

    // =====================================================
    // CAIXAS
    // =====================================================

    /**
     * GET /caixas
     */
    @GetMapping("/caixas")
    public String caixas() {
        return CAIXAS;
    }

    // =====================================================
    // COTACOES
    // =====================================================

    /**
     * GET /cotacoes
     */
    @GetMapping("/cotacoes")
    public String cotacoes() {
        return COTACOES;
    }

    // =====================================================
    // FUNCIONARIOS
    // =====================================================

    /**
     * GET /funcionarios
     */
    @GetMapping("/funcionarios")
    public String funcionarios() {
        return FUNCIONARIOS;
    }

    // =====================================================
    // LOGS
    // =====================================================

    /**
     * GET /logs
     */
    @GetMapping("/logs")
    public String logs() {
        return LOGS;
    }

    // =====================================================
    // LOGIN
    // =====================================================

    /**
     * GET /login
     *
     * A pagina de login deve permanecer
     * acessivel sem autenticacao.
     */
    @GetMapping("/login")
    public String login() {
        return LOGIN;
    }
}

