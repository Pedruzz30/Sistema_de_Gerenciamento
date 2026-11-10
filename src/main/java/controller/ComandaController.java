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

    @PostMapping("/{numero}/abrir")
    public ResponseEntity<ComandaResponse> abrir(@PathVariable int numero,
                                                 @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Comanda comanda = comandaService.abrirOuRetomar(operador(ruHeader), numero);
        return ResponseEntity.ok(ComandaResponse.from(comanda));
    }

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

    public record ComandaResponse(int numero, long vendaId, String status, String abertaEm) {
        static ComandaResponse from(Comanda c) {
            return new ComandaResponse(c.getNumero(), c.getVendaId(), c.getStatus().name(), c.getAbertaEm().toString());
        }
    }
}
