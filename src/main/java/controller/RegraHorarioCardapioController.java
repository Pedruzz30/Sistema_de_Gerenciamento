package controller;

import model.CanalVenda;
import model.Permissao;
import model.RegraHorarioCardapio;
import model.Usuario;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import repository.UsuarioRepository;
import service.RegraHorarioCardapioService;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/cardapio/regras-horario")
public class RegraHorarioCardapioController {

    private final RegraHorarioCardapioService regraService;
    private final UsuarioRepository usuarioRepository;

    public RegraHorarioCardapioController(RegraHorarioCardapioService regraService,
                                          UsuarioRepository usuarioRepository) {
        this.regraService = regraService;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping
    public ResponseEntity<List<RegraHorarioResponse>> listar(
            @RequestParam(required = false) Long filialId,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        validarVisualizacao(ruHeader);
        return ResponseEntity.ok(regraService.listar(filialId).stream().map(RegraHorarioResponse::from).toList());
    }

    @PostMapping
    public ResponseEntity<RegraHorarioResponse> criar(
            @RequestBody RegraHorarioRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarAdministracao(ruHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(RegraHorarioResponse.from(
                regraService.criar(
                        request.filialId(),
                        request.itemId(),
                        request.variacaoId(),
                        request.canal(),
                        request.diaSemana(),
                        request.horaInicio(),
                        request.horaFim(),
                        request.ativa(),
                        usuario
                )
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RegraHorarioResponse> atualizar(
            @PathVariable long id,
            @RequestBody RegraHorarioRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarAdministracao(ruHeader);
        return ResponseEntity.ok(RegraHorarioResponse.from(
                regraService.atualizar(
                        id,
                        request.itemId(),
                        request.variacaoId(),
                        request.canal(),
                        request.diaSemana(),
                        request.horaInicio(),
                        request.horaFim(),
                        request.ativa(),
                        usuario
                )
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(
            @PathVariable long id,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarAdministracao(ruHeader);
        regraService.remover(id, usuario);
        return ResponseEntity.noContent().build();
    }

    private Usuario validarVisualizacao(String ruHeader) {
        return ControllerUtils.resolveUserAndRequireAnyPermission(
                ruHeader,
                usuarioRepository,
                "Voce nao tem permissao para visualizar regras de horario do cardapio.",
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
                "Voce nao tem permissao para gerenciar regras de horario do cardapio."
        );
    }

    public record RegraHorarioRequest(
            Long filialId,
            Integer itemId,
            Long variacaoId,
            CanalVenda canal,
            DayOfWeek diaSemana,
            LocalTime horaInicio,
            LocalTime horaFim,
            Boolean ativa
    ) {
    }

    public record RegraHorarioResponse(
            long id,
            Long filialId,
            Integer itemId,
            Long variacaoId,
            CanalVenda canal,
            DayOfWeek diaSemana,
            LocalTime horaInicio,
            LocalTime horaFim,
            boolean ativa,
            long version
    ) {
        static RegraHorarioResponse from(RegraHorarioCardapio regra) {
            return new RegraHorarioResponse(
                    regra.getId(),
                    regra.getFilialId(),
                    regra.getItemCardapio() == null ? null : regra.getItemCardapio().getId(),
                    regra.getVariacaoItemCardapio() == null ? null : regra.getVariacaoItemCardapio().getId(),
                    regra.getCanal(),
                    regra.getDiaSemana(),
                    regra.getHoraInicio(),
                    regra.getHoraFim(),
                    regra.isAtiva(),
                    regra.getVersion()
            );
        }
    }
}
