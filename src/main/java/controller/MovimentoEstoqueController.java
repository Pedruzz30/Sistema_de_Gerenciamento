package controller;

import model.MovimentoEstoque;
import model.Permissao;
import model.Usuario;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import repository.UsuarioRepository;
import service.ErroNegocioException;
import service.MovimentoEstoqueService;

import java.math.BigDecimal;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/movimentos-estoque")
public class MovimentoEstoqueController {

    private final MovimentoEstoqueService movimentoEstoqueService;
    private final UsuarioRepository usuarioRepository;

    public MovimentoEstoqueController(MovimentoEstoqueService movimentoEstoqueService,
                                      UsuarioRepository usuarioRepository) {
        this.movimentoEstoqueService = movimentoEstoqueService;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping
    public ResponseEntity<List<MovimentoResponse>> listar(
            @RequestParam(value = "produtoId", required = false) Integer produtoId,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        ControllerUtils.resolveUserAndRequirePermission(
                ruHeader,
                usuarioRepository,
                Permissao.VER_ESTOQUE,
                "Voce nao tem permissao para visualizar movimentos de estoque."
        );
        return ResponseEntity.ok(movimentoEstoqueService.listarTodos().stream()
                .filter(movimento -> produtoId == null || movimento.getProduto().getId() == produtoId)
                .map(MovimentoResponse::from)
                .toList());
    }

    @PostMapping("/{id}/estornar")
    public ResponseEntity<?> estornar(@PathVariable long id,
                                      @RequestBody EstornoRequest request,
                                      @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        try {
            Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository);
            MovimentoEstoque movimento = movimentoEstoqueService.estornar(
                    ator,
                    id,
                    request.motivo(),
                    request.observacao()
            );
            return ResponseEntity.ok(MovimentoResponse.from(movimento));
        } catch (ErroNegocioException e) {
            return ResponseEntity.status(statusErroNegocio(e)).body(ErroResponse.from(e));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage(), codigoPorMensagem(e.getMessage()), Map.of()));
        }
    }

    public record EstornoRequest(String motivo, String observacao) {}

    public record MovimentoResponse(
            long id,
            Long filialId,
            int produtoId,
            String produtoNome,
            String tipo,
            BigDecimal quantidadeBase,
            BigDecimal saldoAnterior,
            BigDecimal saldoPosterior,
            BigDecimal custoUnitario,
            BigDecimal custoTotal,
            BigDecimal custoMedioAnterior,
            BigDecimal custoMedioPosterior,
            BigDecimal valorEstoqueAnterior,
            BigDecimal valorEstoquePosterior,
            String origemTipo,
            Long origemId,
            Long usuarioId,
            String motivo,
            String observacao,
            String idempotencyKey,
            Long movimentoEstornadoId,
            String criadoEm
    ) {
        public static MovimentoResponse from(MovimentoEstoque movimento) {
            return new MovimentoResponse(
                    movimento.getId(),
                    movimento.getFilialId(),
                    movimento.getProduto().getId(),
                    movimento.getProduto().getNome(),
                    movimento.getTipo().name(),
                    movimento.getQuantidadeBase(),
                    movimento.getSaldoAnterior(),
                    movimento.getSaldoPosterior(),
                    movimento.getCustoUnitario(),
                    movimento.getCustoTotal(),
                    movimento.getCustoMedioAnterior(),
                    movimento.getCustoMedioPosterior(),
                    movimento.getValorEstoqueAnterior(),
                    movimento.getValorEstoquePosterior(),
                    movimento.getOrigemTipo(),
                    movimento.getOrigemId(),
                    movimento.getUsuarioId(),
                    movimento.getMotivo(),
                    movimento.getObservacao(),
                    movimento.getIdempotencyKey(),
                    movimento.getMovimentoEstornado() != null ? movimento.getMovimentoEstornado().getId() : null,
                    movimento.getCriadoEm().toString()
            );
        }
    }

    public record ErroResponse(String erro, String codigo, Map<String, Object> detalhes) {
        public static ErroResponse from(ErroNegocioException e) {
            return new ErroResponse(e.getMessage(), e.getCodigo(), e.getDetalhes());
        }
    }

    private static org.springframework.http.HttpStatus statusErroNegocio(ErroNegocioException e) {
        return switch (e.getCodigo()) {
            case "MOVIMENTO_JA_ESTORNADO", "MOVIMENTO_NAO_PODE_SER_ESTORNADO" -> org.springframework.http.HttpStatus.CONFLICT;
            default -> org.springframework.http.HttpStatus.BAD_REQUEST;
        };
    }

    private static String codigoPorMensagem(String mensagem) {
        if (mensagem == null) return null;
        if (mensagem.contains("ja foi estornado")) return "MOVIMENTO_JA_ESTORNADO";
        return null;
    }
}
