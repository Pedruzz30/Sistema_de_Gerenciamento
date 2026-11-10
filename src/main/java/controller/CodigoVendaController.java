package controller;

import model.CodigoVenda;
import model.Permissao;
import model.Usuario;
import model.VariacaoItemCardapio;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import repository.UsuarioRepository;
import service.CodigoVendaService;
import service.ErroNegocioException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Códigos lidos no caixa (código de barras ou código interno).
 *
 * <ul>
 *   <li>{@code GET  /api/pdv/codigos/{codigo}} — leitura no PDV (bipe).</li>
 *   <li>{@code GET  /api/pdv/codigos} — lista inteira, para o PDV guardar em
 *       cache na abertura do caixa e ler sem ir ao servidor.</li>
 *   <li>{@code POST /api/pdv/codigos} — vincular código a uma variação.</li>
 *   <li>{@code DELETE /api/pdv/codigos/{id}} — desativar.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/pdv/codigos")
public class CodigoVendaController {

    private final CodigoVendaService codigoVendaService;
    private final UsuarioRepository usuarioRepository;

    public CodigoVendaController(CodigoVendaService codigoVendaService, UsuarioRepository usuarioRepository) {
        this.codigoVendaService = codigoVendaService;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/{codigo}")
    public ResponseEntity<LeituraResponse> ler(@PathVariable String codigo,
                                               @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        exigirVenda(ruHeader);
        CodigoVendaService.Leitura leitura = codigoVendaService.ler(codigo)
                .orElseThrow(() -> new ErroNegocioException(
                        "CODIGO_VENDA_NAO_ENCONTRADO",
                        "Produto nao cadastrado para o codigo " + CodigoVendaService.normalizar(codigo) + ".",
                        Map.of("codigo", CodigoVendaService.normalizar(codigo))));
        return ResponseEntity.ok(LeituraResponse.from(leitura));
    }

    @GetMapping
    public ResponseEntity<List<CodigoResponse>> listar(
            @RequestParam(value = "variacaoId", required = false) Long variacaoId,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        exigirVenda(ruHeader);
        List<CodigoVenda> codigos = variacaoId == null
                ? codigoVendaService.listarAtivos()
                : codigoVendaService.listarPorVariacao(variacaoId);
        return ResponseEntity.ok(codigos.stream().map(CodigoResponse::from).toList());
    }

    @PostMapping
    public ResponseEntity<CodigoResponse> cadastrar(@RequestBody CadastrarCodigoRequest request,
                                                    @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = ControllerUtils.resolveUserAndRequirePermission(
                ruHeader, usuarioRepository, Permissao.EDITAR_ESTOQUE,
                "Voce nao tem permissao para cadastrar codigos de venda.");
        if (request == null || request.variacaoId() == null) {
            throw new IllegalArgumentException("Informe a variacao do codigo.");
        }
        CodigoVenda codigo = codigoVendaService.cadastrar(usuario, request.codigo(), request.variacaoId(), request.padrao());
        return ResponseEntity.ok(CodigoResponse.from(codigo));
    }

    /** Para a folha de códigos: o que ainda não tem como ser bipado. */
    @GetMapping("/sem-codigo")
    public ResponseEntity<List<VariacaoCodigoResponse>> semCodigo(
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        exigirVenda(ruHeader);
        return ResponseEntity.ok(codigoVendaService.listarSemCodigo().stream().map(VariacaoCodigoResponse::from).toList());
    }

    @PostMapping("/gerar-internos")
    public ResponseEntity<List<CodigoResponse>> gerarInternos(
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        model.Usuario usuario = ControllerUtils.resolveUserAndRequirePermission(
                ruHeader, usuarioRepository, Permissao.EDITAR_ESTOQUE,
                "Voce nao tem permissao para criar codigos.");
        return ResponseEntity.ok(codigoVendaService.gerarInternos(usuario).stream().map(CodigoResponse::from).toList());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desativar(@PathVariable long id,
                                          @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = ControllerUtils.resolveUserAndRequirePermission(
                ruHeader, usuarioRepository, Permissao.EDITAR_ESTOQUE,
                "Voce nao tem permissao para remover codigos de venda.");
        codigoVendaService.desativar(usuario, id);
        return ResponseEntity.noContent().build();
    }

    private void exigirVenda(String ruHeader) {
        ControllerUtils.resolveUserAndRequireAnyPermission(
                ruHeader, usuarioRepository,
                "Voce nao tem permissao para consultar codigos de venda.",
                Permissao.VER_VENDAS, Permissao.EDITAR_ESTOQUE);
    }

    public record CadastrarCodigoRequest(String codigo, Long variacaoId, Boolean padrao) {}

    public record VariacaoCodigoResponse(
            long variacaoId,
            String variacaoNome,
            Integer itemCardapioId,
            String itemNome,
            BigDecimal precoVenda,
            Integer produtoId,
            BigDecimal quantidadeProdutoBase
    ) {
        static VariacaoCodigoResponse from(VariacaoItemCardapio v) {
            return new VariacaoCodigoResponse(
                    v.getId(),
                    v.getNome(),
                    v.getItemCardapio() == null ? null : v.getItemCardapio().getId(),
                    v.getItemCardapio() == null ? null : v.getItemCardapio().getNome(),
                    v.getPrecoVenda(),
                    v.getProdutoDireto() == null ? null : v.getProdutoDireto().getId(),
                    v.getQuantidadeProdutoDiretoBase()
            );
        }
    }

    public record CodigoResponse(long id, String codigo, String tipo, boolean padrao, VariacaoCodigoResponse variacao) {
        static CodigoResponse from(CodigoVenda c) {
            return new CodigoResponse(c.getId(), c.getCodigo(), c.getTipo().name(), c.isPadrao(),
                    VariacaoCodigoResponse.from(c.getVariacao()));
        }
    }

    public record LeituraResponse(String codigo, String tipo, VariacaoCodigoResponse variacao,
                                  List<VariacaoCodigoResponse> alternativas) {
        static LeituraResponse from(CodigoVendaService.Leitura leitura) {
            return new LeituraResponse(
                    leitura.codigo(),
                    leitura.tipo().name(),
                    VariacaoCodigoResponse.from(leitura.padrao().getVariacao()),
                    leitura.alternativas().stream().map(c -> VariacaoCodigoResponse.from(c.getVariacao())).toList()
            );
        }
    }
}
