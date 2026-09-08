package controller;

import model.DimensaoUnidade;
import model.Permissao;
import model.UnidadeMedida;
import model.Usuario;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import repository.UsuarioRepository;
import service.UnidadeMedidaService;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/unidades")
public class UnidadeMedidaController {

    private final UnidadeMedidaService unidadeMedidaService;
    private final UsuarioRepository usuarioRepository;

    public UnidadeMedidaController(UnidadeMedidaService unidadeMedidaService,
                                   UsuarioRepository usuarioRepository) {
        this.unidadeMedidaService = unidadeMedidaService;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping
    public ResponseEntity<List<UnidadeResponse>> listar(
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        ControllerUtils.resolveUserAndRequirePermission(
                ruHeader,
                usuarioRepository,
                Permissao.VER_ESTOQUE,
                "Voce nao tem permissao para visualizar unidades de medida."
        );
        return ResponseEntity.ok(unidadeMedidaService.listarTodas().stream()
                .map(UnidadeResponse::from)
                .toList());
    }

    @PostMapping
    public ResponseEntity<?> criar(@RequestBody UnidadeRequest request,
                                   @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        try {
            Usuario ator = ControllerUtils.resolveUser(ruHeader, usuarioRepository);
            ControllerUtils.requirePermission(
                    ator,
                    Permissao.EDITAR_ESTOQUE,
                    "Voce nao tem permissao para criar unidades de medida."
            );
            UnidadeMedida unidade = unidadeMedidaService.criar(
                    request.codigo(),
                    request.nome(),
                    parseDimensao(request.dimensao()),
                    request.fatorBase(),
                    request.ativa()
            );
            return ResponseEntity.ok(UnidadeResponse.from(unidade));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ErroResponse(e.getMessage()));
        }
    }

    public record UnidadeRequest(
            String codigo,
            String nome,
            String dimensao,
            BigDecimal fatorBase,
            Boolean ativa
    ) {}

    public record UnidadeResponse(
            long id,
            String codigo,
            String nome,
            String dimensao,
            BigDecimal fatorBase,
            boolean ativa
    ) {
        public static UnidadeResponse from(UnidadeMedida unidade) {
            return new UnidadeResponse(
                    unidade.getId(),
                    unidade.getCodigo(),
                    unidade.getNome(),
                    unidade.getDimensao().name(),
                    unidade.getFatorBase(),
                    unidade.isAtiva()
            );
        }
    }

    public record ErroResponse(String erro) {}

    private static DimensaoUnidade parseDimensao(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Dimensao da unidade e obrigatoria.");
        }
        try {
            return DimensaoUnidade.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Dimensao de unidade invalida.");
        }
    }
}
