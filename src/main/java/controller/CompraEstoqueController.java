package controller;

import model.CompraEstoque;
import model.ItemCompraEstoque;
import model.Permissao;
import model.Usuario;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import repository.UsuarioRepository;
import service.CompraEstoqueService;
import service.ConflitoIdempotenciaException;
import service.ErroNegocioException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/compras")
public class CompraEstoqueController {

    private final CompraEstoqueService compraEstoqueService;
    private final UsuarioRepository usuarioRepository;

    public CompraEstoqueController(CompraEstoqueService compraEstoqueService,
                                   UsuarioRepository usuarioRepository) {
        this.compraEstoqueService = compraEstoqueService;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping
    public ResponseEntity<List<CompraResponse>> listar(
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        ControllerUtils.resolveUserAndRequirePermission(
                ruHeader,
                usuarioRepository,
                Permissao.VER_COMPRAS,
                "Voce nao tem permissao para visualizar compras."
        );
        return ResponseEntity.ok(compraEstoqueService.listarTodas().stream()
                .map(CompraResponse::from)
                .toList());
    }

    @PostMapping
    public ResponseEntity<?> criar(@RequestBody(required = false) CriarCompraRequest request,
                                   @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        try {
            Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository);
            CompraEstoque compra = compraEstoqueService.criarCompra(
                    ator,
                    request != null ? request.fornecedorId() : null
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(CompraResponse.from(compra));
        } catch (ErroNegocioException e) {
            return ResponseEntity.status(statusErroNegocio(e)).body(ErroResponse.from(e));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage(), codigoPorMensagem(e.getMessage()), Map.of()));
        }
    }

    @PostMapping("/{id}/itens")
    public ResponseEntity<?> adicionarItem(@PathVariable long id,
                                           @RequestBody AdicionarItemCompraRequest request,
                                           @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        try {
            Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository);
            CompraEstoque compra = compraEstoqueService.adicionarItem(
                    ator,
                    id,
                    request.produtoId(),
                    request.embalagemId(),
                    request.unidadeCompraCodigo(),
                    request.quantidadeComprada(),
                    request.custoTotal(),
                    request.motivoEntradaGratuita()
            );
            return ResponseEntity.ok(CompraResponse.from(compra));
        } catch (ErroNegocioException e) {
            return ResponseEntity.status(statusErroNegocio(e)).body(ErroResponse.from(e));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage(), codigoPorMensagem(e.getMessage()), Map.of()));
        }
    }

    @PostMapping("/{id}/confirmar")
    public ResponseEntity<?> confirmar(@PathVariable long id,
                                       @RequestBody(required = false) ConfirmarCompraRequest request,
                                       @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyHeader,
                                       @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        try {
            Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository);
            String chave = idempotencyHeader != null && !idempotencyHeader.isBlank()
                    ? idempotencyHeader
                    : request != null ? request.idempotencyKey() : null;
            CompraEstoque compra = compraEstoqueService.confirmarCompra(
                    ator,
                    id,
                    chave,
                    request != null ? request.motivo() : null
            );
            return ResponseEntity.ok(CompraResponse.from(compra));
        } catch (ConflitoIdempotenciaException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ErroResponse.from(e));
        } catch (ErroNegocioException e) {
            return ResponseEntity.status(statusErroNegocio(e)).body(ErroResponse.from(e));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage(), codigoPorMensagem(e.getMessage()), Map.of()));
        }
    }

    public record CriarCompraRequest(Long fornecedorId) {}

    public record AdicionarItemCompraRequest(
            int produtoId,
            Long embalagemId,
            String unidadeCompraCodigo,
            BigDecimal quantidadeComprada,
            BigDecimal custoTotal,
            String motivoEntradaGratuita
    ) {}

    public record ConfirmarCompraRequest(String idempotencyKey, String motivo) {}

    public record CompraResponse(
            long id,
            Long filialId,
            Long fornecedorId,
            String fornecedorNome,
            String status,
            BigDecimal total,
            String dataCriacao,
            String dataConfirmacao,
            Long usuarioCriacaoId,
            Long usuarioConfirmacaoId,
            List<ItemCompraResponse> itens
    ) {
        public static CompraResponse from(CompraEstoque compra) {
            return new CompraResponse(
                    compra.getId(),
                    compra.getFilialId(),
                    compra.getFornecedor() != null ? compra.getFornecedor().getId() : null,
                    compra.getFornecedor() != null ? compra.getFornecedor().getNome() : null,
                    compra.getStatus().name(),
                    compra.calcularTotal(),
                    compra.getDataCriacao().toString(),
                    compra.getDataConfirmacao() != null ? compra.getDataConfirmacao().toString() : null,
                    compra.getUsuarioCriacaoId(),
                    compra.getUsuarioConfirmacaoId(),
                    compra.getItens().stream().map(ItemCompraResponse::from).toList()
            );
        }
    }

    public record ItemCompraResponse(
            long id,
            int produtoId,
            String produtoNome,
            Long embalagemId,
            String embalagemNome,
            String unidadeCompraCodigo,
            BigDecimal quantidadeComprada,
            BigDecimal quantidadeBase,
            String unidadeBaseProdutoCodigo,
            BigDecimal custoTotal,
            BigDecimal custoUnitarioCompra,
            BigDecimal custoUnitarioBase
    ) {
        public static ItemCompraResponse from(ItemCompraEstoque item) {
            return new ItemCompraResponse(
                    item.getId(),
                    item.getProduto().getId(),
                    item.getProduto().getNome(),
                    item.getEmbalagem() != null ? item.getEmbalagem().getId() : null,
                    item.getEmbalagem() != null ? item.getEmbalagem().getNome() : null,
                    item.getUnidadeCompra() != null ? item.getUnidadeCompra().getCodigo() : null,
                    item.getQuantidadeComprada(),
                    item.getQuantidadeBase(),
                    item.getProduto().getUnidadeBase() != null ? item.getProduto().getUnidadeBase().getCodigo() : null,
                    item.getCustoTotal(),
                    item.getCustoUnitarioCompra(),
                    item.getCustoUnitarioBase()
            );
        }
    }

    public record ErroResponse(String erro, String codigo, Map<String, Object> detalhes) {
        public static ErroResponse from(ErroNegocioException e) {
            return new ErroResponse(e.getMessage(), e.getCodigo(), e.getDetalhes());
        }
    }

    private static HttpStatus statusErroNegocio(ErroNegocioException e) {
        return switch (e.getCodigo()) {
            case "IDEMPOTENCY_KEY_REUSED",
                 "CHAVE_IDEMPOTENCIA_REUTILIZADA_COM_PAYLOAD_DIFERENTE",
                 "COMPRA_JA_CONFIRMADA",
                 "CONCORRENCIA_ESTOQUE" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
    }

    private static String codigoPorMensagem(String mensagem) {
        if (mensagem == null) return null;
        if (mensagem.contains("Unidades incompativeis") || mensagem.contains("Unidades incompatíveis")) {
            return "UNIDADE_INCOMPATIVEL";
        }
        if (mensagem.contains("ja confirmada")) {
            return "COMPRA_JA_CONFIRMADA";
        }
        return null;
    }
}
