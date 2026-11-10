package controller;

import model.Permissao;
import model.Usuario;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import repository.UsuarioRepository;
import service.CozinhaService;

import java.util.List;
import java.util.Map;

/**
 * Pedido impresso para a cozinha. Ver e reimprimir vale para quem opera o caixa (também por
 * PIN); configurar a impressora e o que vai para a cozinha é de quem cuida do estoque ou do
 * financeiro, entrando com a senha.
 */
@RestController
@RequestMapping("/api/cozinha")
public class CozinhaController {

    private final CozinhaService cozinhaService;
    private final UsuarioRepository usuarioRepository;

    public CozinhaController(CozinhaService cozinhaService, UsuarioRepository usuarioRepository) {
        this.cozinhaService = cozinhaService;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/situacao")
    public ResponseEntity<CozinhaService.Situacao> situacao(@RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        operador(ruHeader);
        return ResponseEntity.ok(cozinhaService.situacao());
    }

    @GetMapping("/pedidos")
    public ResponseEntity<List<CozinhaService.Pedido>> pedidos(@RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        operador(ruHeader);
        return ResponseEntity.ok(cozinhaService.recentes());
    }

    @PostMapping("/pedidos/{id}/reimprimir")
    public ResponseEntity<Void> reimprimir(@PathVariable long id,
                                           @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        cozinhaService.reimprimir(operador(ruHeader), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/config")
    public ResponseEntity<ConfigResponse> config(@RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        gerente(ruHeader);
        CozinhaService.Config config = cozinhaService.config();
        return ResponseEntity.ok(new ConfigResponse(config.impressora(), config.colunas(), config.categorias(),
                cozinhaService.impressoras(), cozinhaService.categorias()));
    }

    @PutMapping("/config")
    public ResponseEntity<CozinhaService.Config> salvar(@RequestBody ConfigRequest request,
                                                        @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = gerente(ruHeader);
        if (request == null) throw new IllegalArgumentException("Configuração vazia.");
        return ResponseEntity.ok(cozinhaService.salvarConfig(usuario, new CozinhaService.Config(
                request.impressora(), request.colunas() == null ? 48 : request.colunas(),
                request.categorias() == null ? List.of() : request.categorias())));
    }

    @PostMapping("/teste")
    public ResponseEntity<Map<String, Long>> teste(@RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = gerente(ruHeader);
        if (!cozinhaService.ligada()) {
            throw new IllegalArgumentException("Escolha a impressora da cozinha e salve antes de testar.");
        }
        return ResponseEntity.ok(Map.of("pedido", cozinhaService.testar(usuario)));
    }

    public record ConfigRequest(String impressora, Integer colunas, List<String> categorias) {}

    public record ConfigResponse(String impressora, int colunas, List<String> categorias,
                                 List<String> impressoras, List<Map<String, String>> categoriasDisponiveis) {}

    private Usuario operador(String ruHeader) {
        return ControllerUtils.resolveUserAndRequireAnyPermission(ruHeader, usuarioRepository,
                "Voce nao tem permissao para ver os pedidos da cozinha.",
                Permissao.VER_VENDAS, Permissao.VER_FINANCAS, Permissao.EDITAR_ESTOQUE);
    }

    private Usuario gerente(String ruHeader) {
        return ControllerUtils.resolveUserAndRequireAnyPermission(ruHeader, usuarioRepository,
                "Configurar a cozinha precisa de quem cuida do estoque ou do financeiro.",
                Permissao.EDITAR_ESTOQUE, Permissao.VER_FINANCAS);
    }
}
