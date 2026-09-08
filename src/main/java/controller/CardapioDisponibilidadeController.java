package controller;

import model.CanalVenda;
import model.Permissao;
import model.Usuario;
import org.springframework.http.ResponseEntity;
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
import service.CalculadoraDisponibilidadeService;
import service.DisponibilidadeCanalService;
import service.DisponibilidadeDtos;

import java.util.List;

@RestController
@RequestMapping("/api/cardapio")
public class CardapioDisponibilidadeController {

    private final CalculadoraDisponibilidadeService disponibilidadeService;
    private final DisponibilidadeCanalService disponibilidadeCanalService;
    private final UsuarioRepository usuarioRepository;

    public CardapioDisponibilidadeController(CalculadoraDisponibilidadeService disponibilidadeService,
                                             DisponibilidadeCanalService disponibilidadeCanalService,
                                             UsuarioRepository usuarioRepository) {
        this.disponibilidadeService = disponibilidadeService;
        this.disponibilidadeCanalService = disponibilidadeCanalService;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/variacoes/{variacaoId}/disponibilidade")
    public ResponseEntity<DisponibilidadeDtos.ResultadoDisponibilidade> consultarVariacao(
            @PathVariable long variacaoId,
            @RequestParam Long filialId,
            @RequestParam CanalVenda canal,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        validarConsultaResumo(ruHeader);
        return ResponseEntity.ok(disponibilidadeService.consultar(variacaoId, filialId, canal));
    }

    @GetMapping("/variacoes/{variacaoId}/diagnostico-disponibilidade")
    public ResponseEntity<DisponibilidadeDtos.DiagnosticoDisponibilidade> diagnosticar(
            @PathVariable long variacaoId,
            @RequestParam Long filialId,
            @RequestParam CanalVenda canal,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        validarDiagnostico(ruHeader);
        return ResponseEntity.ok(disponibilidadeService.diagnosticar(variacaoId, filialId, canal));
    }

    @GetMapping("/disponibilidade")
    public ResponseEntity<DisponibilidadeDtos.ConsultaLoteDisponibilidade> consultarCardapio(
            @RequestParam Long filialId,
            @RequestParam CanalVenda canal,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        validarConsultaResumo(ruHeader);
        return ResponseEntity.ok(disponibilidadeService.consultarCardapio(filialId, canal, List.of()));
    }

    @PostMapping("/disponibilidade/consultar")
    public ResponseEntity<DisponibilidadeDtos.ConsultaLoteDisponibilidade> consultarLote(
            @RequestBody ConsultaDisponibilidadeRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        validarConsultaResumo(ruHeader);
        return ResponseEntity.ok(disponibilidadeService.consultarCardapio(
                request.filialId(),
                request.canal(),
                request.variacaoIds()
        ));
    }

    @PutMapping("/variacoes/{variacaoId}/canais/{canal}")
    public ResponseEntity<CanalDisponibilidadeResponse> definirCanal(
            @PathVariable long variacaoId,
            @PathVariable CanalVenda canal,
            @RequestParam Long filialId,
            @RequestBody CanalDisponibilidadeRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarAdministracao(ruHeader);
        return ResponseEntity.ok(CanalDisponibilidadeResponse.from(
                disponibilidadeCanalService.definir(variacaoId, filialId, canal, request.permitida(), usuario)
        ));
    }

    private Usuario validarConsultaResumo(String ruHeader) {
        return ControllerUtils.resolveUserAndRequireAnyPermission(
                ruHeader,
                usuarioRepository,
                "Voce nao tem permissao para consultar disponibilidade do cardapio.",
                Permissao.VER_VENDAS,
                Permissao.VER_ESTOQUE,
                Permissao.VER_FINANCAS
        );
    }

    private Usuario validarDiagnostico(String ruHeader) {
        return ControllerUtils.resolveUserAndRequireAnyPermission(
                ruHeader,
                usuarioRepository,
                "Voce nao tem permissao para diagnostico de disponibilidade.",
                Permissao.VER_ESTOQUE,
                Permissao.EDITAR_ESTOQUE
        );
    }

    private Usuario validarAdministracao(String ruHeader) {
        return ControllerUtils.resolveUserAndRequirePermission(
                ruHeader,
                usuarioRepository,
                Permissao.EDITAR_ESTOQUE,
                "Voce nao tem permissao para administrar disponibilidade do cardapio."
        );
    }

    public record ConsultaDisponibilidadeRequest(Long filialId, CanalVenda canal, List<Long> variacaoIds) {
    }

    public record CanalDisponibilidadeRequest(boolean permitida) {
    }

    public record CanalDisponibilidadeResponse(
            long id,
            long variacaoId,
            Long filialId,
            CanalVenda canal,
            boolean permitida,
            long version
    ) {
        static CanalDisponibilidadeResponse from(model.VariacaoCanalDisponibilidade regra) {
            return new CanalDisponibilidadeResponse(
                    regra.getId(),
                    regra.getVariacaoItemCardapio().getId(),
                    regra.getFilialId(),
                    regra.getCanal(),
                    regra.isPermitida(),
                    regra.getVersion()
            );
        }
    }
}
