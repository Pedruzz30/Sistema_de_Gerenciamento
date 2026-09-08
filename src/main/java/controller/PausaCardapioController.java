package controller;

import model.CanalVenda;
import model.MotivoPausaCardapio;
import model.PausaCardapio;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import repository.UsuarioRepository;
import service.PausaCardapioService;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/cardapio/pausas")
public class PausaCardapioController {

    private final PausaCardapioService pausaService;
    private final UsuarioRepository usuarioRepository;

    public PausaCardapioController(PausaCardapioService pausaService,
                                   UsuarioRepository usuarioRepository) {
        this.pausaService = pausaService;
        this.usuarioRepository = usuarioRepository;
    }

    @PostMapping
    public ResponseEntity<PausaResponse> criar(
            @RequestBody PausaRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarAdministracao(ruHeader);
        PausaCardapio pausa = pausaService.criar(
                request.filialId(),
                request.itemId(),
                request.variacaoId(),
                request.canal(),
                request.inicio(),
                request.fim(),
                request.motivoCodigo(),
                request.motivoDescricao(),
                usuario
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(PausaResponse.from(pausa));
    }

    @GetMapping
    public ResponseEntity<List<PausaResponse>> listar(
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        validarVisualizacao(ruHeader);
        return ResponseEntity.ok(pausaService.listarTodas().stream().map(PausaResponse::from).toList());
    }

    @GetMapping("/ativas")
    public ResponseEntity<List<PausaResponse>> listarAtivas(
            @RequestParam Long filialId,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        validarVisualizacao(ruHeader);
        return ResponseEntity.ok(pausaService.listarAtivas(filialId).stream().map(PausaResponse::from).toList());
    }

    @PostMapping("/{id}/encerrar")
    public ResponseEntity<PausaResponse> encerrar(
            @PathVariable long id,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarAdministracao(ruHeader);
        return ResponseEntity.ok(PausaResponse.from(pausaService.encerrar(id, usuario)));
    }

    private Usuario validarVisualizacao(String ruHeader) {
        return ControllerUtils.resolveUserAndRequireAnyPermission(
                ruHeader,
                usuarioRepository,
                "Voce nao tem permissao para visualizar pausas do cardapio.",
                Permissao.VER_VENDAS,
                Permissao.VER_ESTOQUE,
                Permissao.VER_FINANCAS
        );
    }

    private Usuario validarAdministracao(String ruHeader) {
        return ControllerUtils.resolveUserAndRequirePermission(
                ruHeader,
                usuarioRepository,
                Permissao.EDITAR_ESTOQUE,
                "Voce nao tem permissao para gerenciar pausas do cardapio."
        );
    }

    public record PausaRequest(
            Long filialId,
            Integer itemId,
            Long variacaoId,
            CanalVenda canal,
            LocalDateTime inicio,
            LocalDateTime fim,
            MotivoPausaCardapio motivoCodigo,
            String motivoDescricao
    ) {
    }

    public record PausaResponse(
            long id,
            Long filialId,
            Integer itemCardapioId,
            Long variacaoId,
            CanalVenda canal,
            LocalDateTime inicio,
            LocalDateTime fim,
            MotivoPausaCardapio motivoCodigo,
            String motivoDescricao,
            boolean ativa,
            LocalDateTime criadaEm,
            Long criadaPor,
            LocalDateTime encerradaEm,
            Long encerradaPor,
            long version
    ) {
        static PausaResponse from(PausaCardapio pausa) {
            return new PausaResponse(
                    pausa.getId(),
                    pausa.getFilialId(),
                    pausa.getItemCardapio() == null ? null : pausa.getItemCardapio().getId(),
                    pausa.getVariacaoItemCardapio() == null ? null : pausa.getVariacaoItemCardapio().getId(),
                    pausa.getCanal(),
                    pausa.getInicio(),
                    pausa.getFim(),
                    pausa.getMotivoCodigo(),
                    pausa.getMotivoDescricao(),
                    pausa.isAtiva(),
                    pausa.getCriadaEm(),
                    pausa.getCriadaPor(),
                    pausa.getEncerradaEm(),
                    pausa.getEncerradaPor(),
                    pausa.getVersion()
            );
        }
    }
}
