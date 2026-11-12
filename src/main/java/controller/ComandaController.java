package controller;

import model.Comanda;
import model.Permissao;
import model.Usuario;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import repository.UsuarioRepository;
import service.ComandaService;

import java.util.List;

/** Comandas do salão, usadas pelo PDV. Fica na área de caixa: vale para quem entrou por PIN. */
@RestController
@RequestMapping("/api/comandas")
public class ComandaController {

    private final ComandaService comandaService;
    private final UsuarioRepository usuarioRepository;

    public ComandaController(ComandaService comandaService, UsuarioRepository usuarioRepository) {
        this.comandaService = comandaService;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping
    public ResponseEntity<List<ComandaService.Resumo>> abertas(
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        operador(ruHeader);
        return ResponseEntity.ok(comandaService.listarAbertas());
    }

    @GetMapping("/{numero}")
    public ResponseEntity<ComandaService.Detalhe> detalhe(@PathVariable int numero,
                                                         @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        operador(ruHeader);
        return ResponseEntity.ok(comandaService.detalhar(numero));
    }

    /** O pedido do atendente, lançado de uma vez (usado pelo celular). */
    @PostMapping("/{numero}/lancar")
    public ResponseEntity<ComandaService.Detalhe> lancar(
            @PathVariable int numero,
            @org.springframework.web.bind.annotation.RequestBody LancarRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = operador(ruHeader);
        List<ComandaService.ItemLancado> itens = request == null || request.itens() == null ? List.of()
                : request.itens().stream()
                .filter(i -> i != null && i.variacaoId() != null)
                .map(i -> new ComandaService.ItemLancado(i.variacaoId(), i.quantidade())).toList();
        return ResponseEntity.ok(comandaService.lancar(usuario, numero, itens, request == null ? null : request.cliente()));
    }

    /** O caixa guardou a comanda: o que é da cozinha e ainda não foi, vai agora. */
    @PostMapping("/{numero}/cozinha")
    public ResponseEntity<java.util.Map<String, Boolean>> cozinha(@PathVariable int numero,
                                                                  @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        return ResponseEntity.ok(java.util.Map.of("enviado", comandaService.enviarCozinha(operador(ruHeader), numero)));
    }

    public record ItemLancarRequest(Long variacaoId, java.math.BigDecimal quantidade) {}

    public record LancarRequest(List<ItemLancarRequest> itens, String cliente) {}

    /** Abre (ou retoma) pelo número. O nome do cliente é opcional; em branco, fica o que já estava. */
    @PostMapping("/{numero}/abrir")
    public ResponseEntity<ComandaResponse> abrir(@PathVariable int numero,
                                                 @org.springframework.web.bind.annotation.RequestBody(required = false) ClienteRequest request,
                                                 @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Comanda comanda = comandaService.abrirOuRetomar(operador(ruHeader), numero, request == null ? null : request.cliente());
        return ResponseEntity.ok(ComandaResponse.from(comanda));
    }

    /** Anota, corrige ou apaga (em branco) o nome do cliente da comanda aberta. */
    @org.springframework.web.bind.annotation.PutMapping("/{numero}/cliente")
    public ResponseEntity<ComandaResponse> cliente(@PathVariable int numero,
                                                   @org.springframework.web.bind.annotation.RequestBody(required = false) ClienteRequest request,
                                                   @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Comanda comanda = comandaService.definirCliente(operador(ruHeader), numero, request == null ? null : request.cliente());
        return ResponseEntity.ok(ComandaResponse.from(comanda));
    }

    public record ClienteRequest(String cliente) {}

    /** Passa itens de outra comanda (ou do balcão) para esta. Sem itens na origem, ela fecha. */
    @PostMapping("/{numero}/transferir")
    public ResponseEntity<ComandaService.Transferencia> transferir(
            @PathVariable int numero,
            @org.springframework.web.bind.annotation.RequestBody TransferirRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = operador(ruHeader);
        if (request == null || request.vendaOrigemId() == null) {
            throw new IllegalArgumentException("Informe de onde vêm os itens.");
        }
        List<ComandaService.ItemTransferido> itens = request.itens() == null ? List.of()
                : request.itens().stream().map(i -> new ComandaService.ItemTransferido(i.itemId(), i.quantidade())).toList();
        return ResponseEntity.ok(comandaService.transferir(usuario, numero, request.vendaOrigemId(), itens));
    }

    public record ItemTransferirRequest(long itemId, java.math.BigDecimal quantidade) {}

    private service.AutenticacaoService autenticacaoService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setAutenticacaoService(service.AutenticacaoService autenticacaoService) {
        this.autenticacaoService = autenticacaoService;
    }

    /** Tira itens já lançados. Quem não cuida do financeiro precisa do login do gerente no pedido. */
    @PostMapping("/{numero}/retirar")
    public ResponseEntity<ComandaService.Retirada> retirar(
            @PathVariable int numero,
            @org.springframework.web.bind.annotation.RequestBody RetirarRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = operador(ruHeader);
        if (request == null) {
            throw new IllegalArgumentException("Escolha o que tirar.");
        }
        List<ComandaService.ItemRetirado> itens = request.itens() == null ? List.of()
                : request.itens().stream().map(i -> new ComandaService.ItemRetirado(i.itemId(), i.quantidade())).toList();
        return ResponseEntity.ok(comandaService.retirar(usuario, resolverAutorizador(request.autorizacao()),
                numero, itens, request.motivo()));
    }

    public record RetirarRequest(List<ItemTransferirRequest> itens, String motivo,
                                 CaixaController.AutorizacaoRequest autorizacao) {}

    private Usuario resolverAutorizador(CaixaController.AutorizacaoRequest autorizacao) {
        if (autorizacao == null || autorizacao.nome() == null || autorizacao.nome().isBlank()
                || autorizacao.senha() == null || autorizacao.senha().isBlank()) {
            return null;
        }
        if (autenticacaoService == null) {
            throw new SecurityException("Autorizacao de gerente indisponivel.");
        }
        return autenticacaoService.conferirCredenciais(
                        autorizacao.nome().trim(),
                        autorizacao.sobrenome() == null ? "" : autorizacao.sobrenome().trim(),
                        autorizacao.senha())
                .orElseThrow(() -> new SecurityException("Login do gerente invalido."));
    }

    public record TransferirRequest(Long vendaOrigemId, List<ItemTransferirRequest> itens) {}

    @DeleteMapping("/{numero}")
    public ResponseEntity<Void> excluirVazia(@PathVariable int numero,
                                             @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        comandaService.cancelarVazia(operador(ruHeader), numero);
        return ResponseEntity.noContent().build();
    }

    private Usuario operador(String ruHeader) {
        return ControllerUtils.resolveUserAndRequirePermission(
                ruHeader, usuarioRepository, Permissao.VER_VENDAS,
                "Voce nao tem permissao para usar comandas.");
    }

    public record ComandaResponse(int numero, long vendaId, String status, String abertaEm, String cliente) {
        static ComandaResponse from(Comanda c) {
            return new ComandaResponse(c.getNumero(), c.getVendaId(), c.getStatus().name(), c.getAbertaEm().toString(), c.getCliente());
        }
    }
}
