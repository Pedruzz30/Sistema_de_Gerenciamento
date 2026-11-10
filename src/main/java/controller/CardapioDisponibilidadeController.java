
package controller;

import java.util.List;

import model.CanalVenda;
import model.Permissao;
import model.Usuario;
import model.VariacaoCanalDisponibilidade;

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

/**
 * Controller responsavel pela disponibilidade do cardapio.
 *
 * Permite:
 * - Consultar disponibilidade de variacoes;
 * - Consultar diagnosticos detalhados;
 * - Consultar o cardapio em lote;
 * - Configurar disponibilidade por canal de venda.
 *
 * ATENCAO:
 * A autenticacao por X-User-RU e legada.
 * O acesso real a filial deve ser validado no backend
 * antes da utilizacao em producao.
 */
@RestController
@RequestMapping("/api/cardapio")
public class CardapioDisponibilidadeController {

    private static final String ERRO_CONSULTA =
            "Voce nao tem permissao para consultar disponibilidade do cardapio.";

    private static final String ERRO_DIAGNOSTICO =
            "Voce nao tem permissao para diagnostico de disponibilidade.";

    private static final String ERRO_ADMINISTRACAO =
            "Voce nao tem permissao para administrar disponibilidade do cardapio.";

    private final CalculadoraDisponibilidadeService disponibilidadeService;

    private final DisponibilidadeCanalService disponibilidadeCanalService;

    private final UsuarioRepository usuarioRepository;

    /**
     * Injecao de dependencias via construtor.
     */
    public CardapioDisponibilidadeController(
            CalculadoraDisponibilidadeService disponibilidadeService,
            DisponibilidadeCanalService disponibilidadeCanalService,
            UsuarioRepository usuarioRepository
    ) {
        this.disponibilidadeService = disponibilidadeService;
        this.disponibilidadeCanalService = disponibilidadeCanalService;
        this.usuarioRepository = usuarioRepository;
    }

    // =====================================================
    // CONSULTA DE DISPONIBILIDADE POR VARIACAO
    // =====================================================

    @GetMapping("/variacoes/{variacaoId}/disponibilidade")
    public ResponseEntity<DisponibilidadeDtos.ResultadoDisponibilidade>
    consultarVariacao(
            @PathVariable long variacaoId,
            @RequestParam Long filialId,
            @RequestParam CanalVenda canal,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        validarConsultaResumo(ruHeader);

        validarVariacaoId(variacaoId);
        validarParametrosConsulta(filialId, canal);

        DisponibilidadeDtos.ResultadoDisponibilidade resultado =
                disponibilidadeService.consultar(
                        variacaoId,
                        filialId,
                        canal
                );

        return ResponseEntity.ok(resultado);
    }

    // =====================================================
    // DIAGNOSTICO DETALHADO
    // =====================================================

    @GetMapping("/variacoes/{variacaoId}/diagnostico-disponibilidade")
    public ResponseEntity<DisponibilidadeDtos.DiagnosticoDisponibilidade>
    diagnosticar(
            @PathVariable long variacaoId,
            @RequestParam Long filialId,
            @RequestParam CanalVenda canal,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        validarDiagnostico(ruHeader);

        validarVariacaoId(variacaoId);
        validarParametrosConsulta(filialId, canal);

        DisponibilidadeDtos.DiagnosticoDisponibilidade diagnostico =
                disponibilidadeService.diagnosticar(
                        variacaoId,
                        filialId,
                        canal
                );

        return ResponseEntity.ok(diagnostico);
    }

    // =====================================================
    // CONSULTA COMPLETA DO CARDAPIO
    // =====================================================

    @GetMapping("/disponibilidade")
    public ResponseEntity<DisponibilidadeDtos.ConsultaLoteDisponibilidade>
    consultarCardapio(
            @RequestParam Long filialId,
            @RequestParam CanalVenda canal,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        validarConsultaResumo(ruHeader);

        validarParametrosConsulta(filialId, canal);

        // Lista vazia significa consultar todas as variacoes,
        // conforme o contrato atual do service.
        DisponibilidadeDtos.ConsultaLoteDisponibilidade resultado =
                disponibilidadeService.consultarCardapio(
                        filialId,
                        canal,
                        List.of()
                );

        return ResponseEntity.ok(resultado);
    }

    // =====================================================
    // CONSULTA DE DISPONIBILIDADE EM LOTE
    // =====================================================

    @PostMapping("/disponibilidade/consultar")
    public ResponseEntity<DisponibilidadeDtos.ConsultaLoteDisponibilidade>
    consultarLote(
            @RequestBody(required = false)
            ConsultaDisponibilidadeRequest request,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        validarConsultaResumo(ruHeader);

        validarConsultaLoteRequest(request);

        DisponibilidadeDtos.ConsultaLoteDisponibilidade resultado =
                disponibilidadeService.consultarCardapio(
                        request.filialId(),
                        request.canal(),
                        request.variacaoIds()
                );

        return ResponseEntity.ok(resultado);
    }

    // =====================================================
    // CONFIGURACAO DE DISPONIBILIDADE POR CANAL
    // =====================================================

    @PutMapping("/variacoes/{variacaoId}/canais/{canal}")
    public ResponseEntity<CanalDisponibilidadeResponse>
    definirCanal(
            @PathVariable long variacaoId,
            @PathVariable CanalVenda canal,
            @RequestParam Long filialId,
            @RequestBody(required = false)
            CanalDisponibilidadeRequest request,
            @RequestHeader(
                    value = "X-User-RU",
                    required = false
            ) String ruHeader
    ) {
        Usuario usuario = validarAdministracao(ruHeader);

        validarVariacaoId(variacaoId);
        validarParametrosConsulta(filialId, canal);
        validarCanalRequest(request);

        VariacaoCanalDisponibilidade regra =
                disponibilidadeCanalService.definir(
                        variacaoId,
                        filialId,
                        canal,
                        request.permitida(),
                        usuario
                );

        return ResponseEntity.ok(
                CanalDisponibilidadeResponse.from(regra)
        );
    }

    // =====================================================
    // AUTORIZACAO
    // =====================================================

    /**
     * Permissoes para consultas resumidas.
     */
    private void validarConsultaResumo(String ruHeader) {
        ControllerUtils.resolveUserAndRequireAnyPermission(
                ruHeader,
                usuarioRepository,
                ERRO_CONSULTA,
                Permissao.VER_VENDAS,
                Permissao.VER_ESTOQUE,
                Permissao.VER_FINANCAS
        );
    }

    /**
     * Permissoes para diagnosticos detalhados.
     */
    private void validarDiagnostico(String ruHeader) {
        ControllerUtils.resolveUserAndRequireAnyPermission(
                ruHeader,
                usuarioRepository,
                ERRO_DIAGNOSTICO,
                Permissao.VER_ESTOQUE,
                Permissao.EDITAR_ESTOQUE
        );
    }

    /**
     * Permissao para alterar disponibilidade por canal.
     */
    private Usuario validarAdministracao(String ruHeader) {
        return ControllerUtils.resolveUserAndRequirePermission(
                ruHeader,
                usuarioRepository,
                Permissao.EDITAR_ESTOQUE,
                ERRO_ADMINISTRACAO
        );
    }

    // =====================================================
    // VALIDACOES DE ENTRADA
    // =====================================================

    private static void validarVariacaoId(long variacaoId) {
        if (variacaoId <= 0) {
            throw new IllegalArgumentException(
                    "ID da variacao deve ser maior que zero."
            );
        }
    }

    private static void validarParametrosConsulta(
            Long filialId,
            CanalVenda canal
    ) {
        if (filialId == null || filialId <= 0) {
            throw new IllegalArgumentException(
                    "Filial deve possuir um ID valido."
            );
        }

        if (canal == null) {
            throw new IllegalArgumentException(
                    "Canal de venda e obrigatorio."
            );
        }
    }

    private static void validarConsultaLoteRequest(
            ConsultaDisponibilidadeRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Dados da consulta sao obrigatorios."
            );
        }

        validarParametrosConsulta(
                request.filialId(),
                request.canal()
        );

        List<Long> variacaoIds = request.variacaoIds();

        // Nulo ou vazio preserva o comportamento atual:
        // consultar todas as variacoes.
        if (variacaoIds == null || variacaoIds.isEmpty()) {
            return;
        }

        boolean possuiIdInvalido = variacaoIds.stream()
                .anyMatch(id -> id == null || id <= 0);

        if (possuiIdInvalido) {
            throw new IllegalArgumentException(
                    "Todas as variacoes devem possuir IDs positivos."
            );
        }
    }

    private static void validarCanalRequest(
            CanalDisponibilidadeRequest request
    ) {
        if (request == null || request.permitida() == null) {
            throw new IllegalArgumentException(
                    "Campo permitida e obrigatorio."
            );
        }
    }

    // =====================================================
    // REQUEST DTOs
    // =====================================================

    public record ConsultaDisponibilidadeRequest(
            Long filialId,
            CanalVenda canal,
            List<Long> variacaoIds
    ) {
    }

    /**
     * Boolean permite diferenciar:
     * - true;
     * - false;
     * - campo ausente (null).
     */
    public record CanalDisponibilidadeRequest(
            Boolean permitida
    ) {
    }

    // =====================================================
    // RESPONSE DTO
    // =====================================================

    public record CanalDisponibilidadeResponse(
            long id,
            long variacaoId,
            Long filialId,
            CanalVenda canal,
            boolean permitida,
            long version
    ) {

        public static CanalDisponibilidadeResponse from(
                VariacaoCanalDisponibilidade regra
        ) {
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

