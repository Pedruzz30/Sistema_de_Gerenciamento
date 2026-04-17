package controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Redireciona rotas sem extensão para as páginas estáticas correspondentes.
 *
 * <h2>Problema que este controller resolve</h2>
 * O Spring Boot serve arquivos em {@code src/main/resources/static/} diretamente
 * por URL com extensão (ex: {@code /estoque.html}). Mas URLs sem extensão
 * (ex: {@code /estoque}) retornam 404 por padrão — o que quebra links da
 * sidebar e bookmarks dos usuários.
 *
 * <p>Este controller mapeia cada rota limpa para seu template correspondente,
 * retornando o nome da view para o {@code ResourceHttpRequestHandler} do
 * Spring MVC resolver o arquivo estático.
 *
 * <h2>Por que um controller, e não {@code WebMvcConfigurer}?</h2>
 * A alternativa seria um {@code WebMvcConfigurer} com {@code addViewControllers},
 * mas esse mecanismo não funciona bem com templates Thymeleaf em {@code /static/}
 * sem configuração extra. Controllers Thymeleaf são mais explícitos e fáceis
 * de depurar.
 *
 * <h2>Mapeamento completo</h2>
 * <pre>
 *   GET /                  → dashboard
 *   GET /dashboard         → dashboard
 *   GET /cardapio          → cardapio
 *   GET /estoque           → estoque
 *   GET /fornecedores      → fornecedores
 *   GET /notas             → notas
 *   GET /caixas            → caixas
 *   GET /cotacoes          → cotacoes
 *   GET /funcionarios      → funcionarios
 *   GET /logs              → logs
 *   GET /login             → login
 * </pre>
 *
 * <p>Cada método retorna o nome do template sem extensão — o Thymeleaf
 * resolve para {@code src/main/resources/static/{nome}.html}.
 *
 * <h2>Próximo passo</h2>
 * Quando este projeto migrar para autenticação real via Spring Security,
 * este controller é o ponto natural para adicionar {@code @PreAuthorize}
 * por rota — protegendo páginas no servidor, não só no JavaScript.
 */
@Controller
public class EstoquePageController {

    // ── Raiz e dashboard ─────────────────────────────────────────────────────

    /** Rota raiz redireciona para o dashboard. */
    @GetMapping("/")
    public String raiz() {
        return "redirect:/dashboard.html";
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        return "redirect:/dashboard.html";
    }

    // ── Módulos principais ────────────────────────────────────────────────────

    @GetMapping("/cardapio")
    public String cardapio() {
        return "redirect:/cardapio.html";
    }

    @GetMapping("/estoque")
    public String estoque() {
        return "redirect:/estoque.html";
    }

    @GetMapping("/fornecedores")
    public String fornecedores() {
        return "redirect:/fornecedores.html";
    }

    @GetMapping("/notas")
    public String notas() {
        return "redirect:/notas.html";
    }

    @GetMapping("/caixas")
    public String caixas() {
        return "redirect:/caixas.html";
    }

    @GetMapping("/cotacoes")
    public String cotacoes() {
        return "redirect:/cotacoes.html";
    }

    @GetMapping("/funcionarios")
    public String funcionarios() {
        return "redirect:/funcionarios.html";
    }

    @GetMapping("/logs")
    public String logs() {
        return "redirect:/logs.html";
    }

    // ── Autenticação ──────────────────────────────────────────────────────────

    @GetMapping("/login")
    public String login() {
        return "redirect:/login.html";
    }
}
