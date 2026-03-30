package controller;

import model.NivelEstoque;
import model.Pedido;
import model.Produto;
import model.TipoMovimentacao;
import service.EstoqueService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class ProdutoController {

    private final EstoqueService estoqueService;

    public ProdutoController(EstoqueService estoqueService) {
        this.estoqueService = estoqueService;
    }

    // GET /api/produtos
    @GetMapping("/produtos")
    public ResponseEntity<List<ProdutoResponse>> listarProdutos() {
        List<ProdutoResponse> resposta = estoqueService.listarProdutos().stream()
                .map(p -> ProdutoResponse.from(p, estoqueService.calcularNivelEstoque(p)))
                .toList();
        return ResponseEntity.ok(resposta);
    }

    // GET /api/produtos/{id}
    @GetMapping("/produtos/{id}")
    public ResponseEntity<ProdutoResponse> buscarProduto(@PathVariable int id) {
        Optional<Produto> produto = estoqueService.buscarProdutoPorId(id);
        if (produto.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Produto p = produto.get();
        return ResponseEntity.ok(ProdutoResponse.from(p, estoqueService.calcularNivelEstoque(p)));
    }

    // POST /api/produtos
    @PostMapping("/produtos")
    public ResponseEntity<?> cadastrarProduto(@RequestBody CadastroProdutoRequest request) {
        try {
            Produto produto = estoqueService.cadastrarProduto(
                    request.nome(),
                    request.quantidadeInicial(),
                    request.quantidadeMinima(),
                    request.precoUnitario()
            );
            return ResponseEntity.ok(ProdutoResponse.from(produto, estoqueService.calcularNivelEstoque(produto)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    // POST /api/movimentacoes
    @PostMapping("/movimentacoes")
    public ResponseEntity<?> registrarMovimentacao(@RequestBody MovimentacaoRequest request) {
        TipoMovimentacao tipo;
        try {
            tipo = TipoMovimentacao.valueOf(request.tipo().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ErroResponse("Tipo inválido. Use ENTRADA ou SAIDA."));
        }

        try {
            Pedido pedido = estoqueService.registrarMovimentacao(
                    request.idProduto(),
                    request.quantidade(),
                    tipo,
                    null
            );
            Produto p = pedido.getProduto();
            return ResponseEntity.ok(ProdutoResponse.from(p, estoqueService.calcularNivelEstoque(p)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    // GET /api/movimentacoes — used by dashboard and history views
    @GetMapping("/movimentacoes")
    public ResponseEntity<List<PedidoResponse>> listarMovimentacoes() {
        List<PedidoResponse> resp = estoqueService.listarPedidos().stream()
                .sorted(Comparator.comparing(Pedido::getDataHora).reversed())
                .limit(100)
                .map(PedidoResponse::from)
                .toList();
        return ResponseEntity.ok(resp);
    }

    // ── Records ───────────────────────────────────────────────────────────────

    public record CadastroProdutoRequest(
            String nome,
            int quantidadeInicial,
            int quantidadeMinima,
            double precoUnitario
    ) {}

    public record MovimentacaoRequest(
            int idProduto,
            int quantidade,
            String tipo
    ) {}

    public record ProdutoResponse(
            int id,
            String nome,
            int quantidadeAtual,
            int quantidadeMinima,
            double precoUnitario,
            String nivelEstoque
    ) {
        public static ProdutoResponse from(Produto p, NivelEstoque nivel) {
            return new ProdutoResponse(
                    p.getId(),
                    p.getNome(),
                    p.getQuantidadeAtual(),
                    p.getQuantidadeMinima(),
                    p.getPrecoUnitario(),
                    nivel.name()
            );
        }
    }

    public record PedidoResponse(
            int id,
            String tipo,
            String produto,
            int quantidade,
            String dataHora
    ) {
        public static PedidoResponse from(Pedido p) {
            return new PedidoResponse(
                    p.getId(),
                    p.getTipo().name(),
                    p.getProduto().getNome(),
                    p.getQuantidade(),
                    p.getDataHora().toString()
            );
        }
    }

    public record ErroResponse(String erro) {}
}
