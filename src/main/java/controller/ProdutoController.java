package controller;

import java.math.BigDecimal;
import model.CotacaoMensal;
import model.NivelEstoque;
import model.Pedido;
import model.Permissao;
import model.CategoriaEstoque;
import model.Produto;
import model.TipoMovimentacao;
import model.Usuario;
import model.LogAcao;
import repository.CotacaoRepository;
import repository.LogRepository;
import repository.UsuarioRepository;
import service.EstoqueService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class ProdutoController {

    private final EstoqueService estoqueService;
    private final UsuarioRepository usuarioRepository;
    private final LogRepository logRepository;
    private final CotacaoRepository cotacaoRepository;

    public ProdutoController(EstoqueService estoqueService,
                             UsuarioRepository usuarioRepository,
                             LogRepository logRepository,
                             CotacaoRepository cotacaoRepository) {
        this.estoqueService = estoqueService;
        this.usuarioRepository = usuarioRepository;
        this.logRepository = logRepository;
        this.cotacaoRepository = cotacaoRepository;
    }

    // GET /api/produtos
    @GetMapping("/produtos")
    public ResponseEntity<List<ProdutoResponse>> listarProdutos(
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        ControllerUtils.resolveUserAndRequirePermission(
                ruHeader,
                usuarioRepository,
                Permissao.VER_ESTOQUE,
                "Voce nao tem permissao para visualizar produtos."
        );
        Map<Integer, BigDecimal> custos = custoMaisRecentePorProduto();
        List<ProdutoResponse> resposta = estoqueService.listarProdutos().stream()
                .map(p -> ProdutoResponse.from(p, estoqueService.calcularNivelEstoque(p), custos.get(p.getId())))
                .toList();
        return ResponseEntity.ok(resposta);
    }

    // GET /api/produtos/{id}
    @GetMapping("/produtos/{id}")
    public ResponseEntity<ProdutoResponse> buscarProduto(@PathVariable int id,
                                                         @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        ControllerUtils.resolveUserAndRequirePermission(
                ruHeader,
                usuarioRepository,
                Permissao.VER_ESTOQUE,
                "Voce nao tem permissao para visualizar produtos."
        );
        Optional<Produto> produto = estoqueService.buscarProdutoPorId(id);
        if (produto.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Produto p = produto.get();
        return ResponseEntity.ok(ProdutoResponse.from(p, estoqueService.calcularNivelEstoque(p), ultimoCustoOuNulo(p.getId())));
    }

    // POST /api/produtos
    @PostMapping("/produtos")
    public ResponseEntity<?> cadastrarProduto(@RequestBody CadastroProdutoRequest request,
                                              @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        try {
            Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository);
            validarPermissaoEdicaoEstoque(ator);
            Produto produto = estoqueService.cadastrarProduto(
                    ator,
                    request.nome(),
                    request.quantidadeInicial(),
                    request.quantidadeMinima(),
                    request.precoUnitario(),
                    parseCategoriaEstoque(request.categoriaEstoque())
            );

            logRepository.salvar(new LogAcao(
                    0,
                    ator.getRu(),
                    "PRODUTO_CADASTRADO",
                    "Produto cadastrado: " + produto.getNome() + " (ID: " + produto.getId() + ")"
            ));

            return ResponseEntity.ok(ProdutoResponse.from(produto, estoqueService.calcularNivelEstoque(produto), ultimoCustoOuNulo(produto.getId())));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    // PUT /api/produtos/{id}
    @PutMapping("/produtos/{id}")
    public ResponseEntity<?> atualizarProduto(@PathVariable int id,
                                              @RequestBody AtualizarProdutoRequest request,
                                              @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        try {
            Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository);
            validarPermissaoEdicaoEstoque(ator);
            Produto produto = estoqueService.atualizarProduto(
                    ator,
                    id,
                    request.nome(),
                    request.quantidadeMinima(),
                    request.precoUnitario(),
                    parseCategoriaEstoque(request.categoriaEstoque())
            );

            logRepository.salvar(new LogAcao(
                    0,
                    ator.getRu(),
                    "PRODUTO_ATUALIZADO",
                    "Produto atualizado: " + produto.getNome() + " (ID: " + produto.getId() + ")"
            ));

            return ResponseEntity.ok(ProdutoResponse.from(produto, estoqueService.calcularNivelEstoque(produto), ultimoCustoOuNulo(produto.getId())));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    // POST /api/movimentacoes
    @PostMapping("/movimentacoes")
    public ResponseEntity<?> registrarMovimentacao(@RequestBody MovimentacaoRequest request,
                                                   @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        TipoMovimentacao tipo;
        try {
            tipo = TipoMovimentacao.valueOf(request.tipo().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ErroResponse("Tipo inválido. Use ENTRADA ou SAIDA."));
        }

        try {
            Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository);
            Pedido pedido = estoqueService.registrarMovimentacao(
                    request.idProduto(),
                    request.quantidade(),
                    tipo,
                    ator,
                    request.descricao()
            );
            Produto p = pedido.getProduto();

            logRepository.salvar(new LogAcao(
                    0,
                    ator.getRu(),
                    "MOVIMENTACAO_ESTOQUE",
                    "Movimentação " + tipo.name() + " no produto '" + p.getNome() +
                            "' (ID: " + p.getId() + "), quantidade: " + request.quantidade()
            ));

            return ResponseEntity.ok(ProdutoResponse.from(p, estoqueService.calcularNivelEstoque(p), ultimoCustoOuNulo(p.getId())));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    // GET /api/movimentacoes — used by dashboard and history views
    @GetMapping("/movimentacoes")
    public ResponseEntity<List<PedidoResponse>> listarMovimentacoes(
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        ControllerUtils.resolveUserAndRequirePermission(
                ruHeader,
                usuarioRepository,
                Permissao.VER_ESTOQUE,
                "Voce nao tem permissao para visualizar movimentacoes de estoque."
        );
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
            BigDecimal precoUnitario,
            String categoriaEstoque
    ) {}

    public record AtualizarProdutoRequest(
            String nome,
            int quantidadeMinima,
            BigDecimal precoUnitario,
            String categoriaEstoque
    ) {}

    public record MovimentacaoRequest(
            int idProduto,
            int quantidade,
            String tipo,
            String descricao
    ) {}

    public record ProdutoResponse(
            int id,
            String nome,
            int quantidadeAtual,
            int quantidadeMinima,
            BigDecimal precoUnitario,
            BigDecimal precoCusto,
            String nivelEstoque,
            String categoriaEstoque,
            boolean controladoPorEstoque
    ) {
        public static ProdutoResponse from(Produto p, NivelEstoque nivel, BigDecimal precoCusto) {
            return new ProdutoResponse(
                    p.getId(),
                    p.getNome(),
                    p.getQuantidadeAtual(),
                    p.getQuantidadeMinima(),
                    p.getPrecoUnitario(),
                    precoCusto,
                    nivel.name(),
                    p.getCategoriaEstoque() != null ? p.getCategoriaEstoque().name() : null,
                    p.isControladoPorEstoque()
            );
        }
    }

    public record PedidoResponse(
            int id,
            String tipo,
            String produto,
            int quantidade,
            String dataHora,
            int saldoAnterior,
            int saldoPosterior,
            Long usuarioResponsavelRu,
            String usuarioResponsavelNome,
            String descricao
    ) {
        public static PedidoResponse from(Pedido p) {
            return new PedidoResponse(
                    p.getId(),
                    p.getTipo().name(),
                    p.getProduto().getNome(),
                    p.getQuantidade(),
                    p.getDataHora().toString(),
                    p.getSaldoAnterior(),
                    p.getSaldoPosterior(),
                    p.getUsuarioResponsavelRu(),
                    p.getUsuarioResponsavelNome(),
                    p.getDescricao()
            );
        }
    }

    private static void validarPermissaoEdicaoEstoque(Usuario usuario) {
        if (usuario.getClasse() == null
                || !usuario.getClasse().possuiPermissao(Permissao.EDITAR_ESTOQUE)) {
            throw new SecurityException("Voce nao tem permissao para editar estoque.");
        }
    }

    private static CategoriaEstoque parseCategoriaEstoque(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }

        try {
            return CategoriaEstoque.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Categoria de estoque invalida.");
        }
    }

    public record ErroResponse(String erro) {}

    private Map<Integer, BigDecimal> custoMaisRecentePorProduto() {
        Map<Integer, BigDecimal> custos = new LinkedHashMap<>();
        // listarTodas() retorna ordenado por mesReferencia DESC — primeira ocorrência por produto é a mais recente
        for (CotacaoMensal c : cotacaoRepository.listarTodas()) {
            custos.putIfAbsent(c.getProduto().getId(), c.getPrecoUnitario());
        }
        return custos;
    }

    private BigDecimal ultimoCustoOuNulo(int produtoId) {
        List<CotacaoMensal> historico = cotacaoRepository.buscarHistoricoPorProduto(produtoId);
        if (historico.isEmpty()) return null;
        return historico.get(historico.size() - 1).getPrecoUnitario();
    }
}
