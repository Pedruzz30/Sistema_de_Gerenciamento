package controller;

import model.CategoriaEstoque;
import model.CodigoVenda;
import model.LogAcao;
import model.Permissao;
import model.Usuario;
import model.VariacaoItemCardapio;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import repository.LogRepository;
import repository.UsuarioRepository;
import service.ProdutoRevendaService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/** Cadastro rápido de bebida: {@code POST /api/produtos/revenda}. */
@RestController
@RequestMapping("/api/produtos/revenda")
public class ProdutoRevendaController {

    private final ProdutoRevendaService produtoRevendaService;
    private final UsuarioRepository usuarioRepository;
    private final LogRepository logRepository;

    public ProdutoRevendaController(ProdutoRevendaService produtoRevendaService,
                                    UsuarioRepository usuarioRepository,
                                    LogRepository logRepository) {
        this.produtoRevendaService = produtoRevendaService;
        this.usuarioRepository = usuarioRepository;
        this.logRepository = logRepository;
    }

    @PostMapping
    public ResponseEntity<RevendaResponse> cadastrar(
            @RequestBody CadastroRevendaRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = ControllerUtils.resolveUserAndRequirePermission(
                ruHeader, usuarioRepository, Permissao.EDITAR_ESTOQUE,
                "Voce nao tem permissao para cadastrar produtos.");
        if (request == null) {
            throw new IllegalArgumentException("Dados do produto sao obrigatorios.");
        }
        ProdutoRevendaService.Resultado resultado = produtoRevendaService.cadastrar(usuario, request.paraEntrada());
        logRepository.salvar(new LogAcao(
                0,
                usuario.getRu(),
                "PRODUTO_REVENDA_CADASTRADO",
                "Cadastro rapido: " + resultado.produto().getNome() + " (ID: " + resultado.produto().getId() + ")"
        ));
        return ResponseEntity.ok(RevendaResponse.from(resultado));
    }

    public record FardoRequest(Integer quantidadeUnidades, BigDecimal preco, String codigoBarras) {}

    public record CadastroRevendaRequest(
            String nome,
            String categoriaCardapioCodigo,
            String categoriaCardapioNome,
            String categoriaEstoque,
            String codigoBarras,
            BigDecimal precoVenda,
            BigDecimal precoGelado,
            BigDecimal custoUnitario,
            Integer quantidadeInicial,
            Integer quantidadeMinima,
            FardoRequest fardo
    ) {
        ProdutoRevendaService.Entrada paraEntrada() {
            ProdutoRevendaService.Fardo fardoEntrada = fardo == null
                    ? null
                    : new ProdutoRevendaService.Fardo(
                            fardo.quantidadeUnidades() == null ? 0 : fardo.quantidadeUnidades(),
                            fardo.preco(),
                            fardo.codigoBarras());
            return new ProdutoRevendaService.Entrada(
                    nome,
                    categoriaCardapioCodigo,
                    categoriaCardapioNome,
                    parseCategoriaEstoque(categoriaEstoque),
                    codigoBarras,
                    precoVenda,
                    precoGelado,
                    custoUnitario,
                    quantidadeInicial == null ? 0 : quantidadeInicial,
                    quantidadeMinima == null ? 0 : quantidadeMinima,
                    fardoEntrada
            );
        }

        private static CategoriaEstoque parseCategoriaEstoque(String valor) {
            if (valor == null || valor.isBlank()) {
                return null;
            }
            try {
                return CategoriaEstoque.valueOf(valor.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Categoria de estoque invalida: " + valor);
            }
        }
    }

    public record VariacaoResponse(long id, String nome, String codigo, BigDecimal precoVenda,
                                   BigDecimal quantidadeBase, boolean padrao) {
        static VariacaoResponse from(VariacaoItemCardapio v) {
            return new VariacaoResponse(v.getId(), v.getNome(), v.getCodigo(), v.getPrecoVenda(),
                    v.getQuantidadeProdutoDiretoBase(), v.isPadrao());
        }
    }

    public record CodigoResponse(String codigo, String tipo, long variacaoId, boolean padrao) {
        static CodigoResponse from(CodigoVenda c) {
            return new CodigoResponse(c.getCodigo(), c.getTipo().name(), c.getVariacao().getId(), c.isPadrao());
        }
    }

    public record RevendaResponse(int produtoId, String nome, int quantidadeAtual, int itemCardapioId,
                                  List<VariacaoResponse> variacoes, List<CodigoResponse> codigos) {
        static RevendaResponse from(ProdutoRevendaService.Resultado r) {
            return new RevendaResponse(
                    r.produto().getId(),
                    r.produto().getNome(),
                    r.produto().getQuantidadeAtual(),
                    r.item().getId(),
                    r.variacoes().stream().map(VariacaoResponse::from).toList(),
                    r.codigos().stream().map(CodigoResponse::from).toList()
            );
        }
    }
}
