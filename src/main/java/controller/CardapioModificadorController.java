package controller;

import model.CanalVenda;
import model.GrupoModificador;
import model.OpcaoModificador;
import model.OpcaoModificadorComponente;
import model.Permissao;
import model.PoliticaFaltaIngrediente;
import model.TipoEfeitoModificador;
import model.TipoSelecaoModificador;
import model.Usuario;
import model.VariacaoGrupoModificador;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import repository.UsuarioRepository;
import service.AssociacaoVariacaoModificadorService;
import service.CalculadoraDisponibilidadeConfiguracaoService;
import service.GrupoModificadorService;
import service.MontagemConfiguracaoItemService;
import service.OpcaoModificadorService;
import service.VariacaoItemCardapioService;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/cardapio")
public class CardapioModificadorController {

    private final GrupoModificadorService grupoService;
    private final OpcaoModificadorService opcaoService;
    private final AssociacaoVariacaoModificadorService associacaoService;
    private final MontagemConfiguracaoItemService montagemConfiguracaoItemService;
    private final CalculadoraDisponibilidadeConfiguracaoService disponibilidadeConfiguracaoService;
    private final VariacaoItemCardapioService variacaoItemCardapioService;
    private final UsuarioRepository usuarioRepository;

    public CardapioModificadorController(GrupoModificadorService grupoService,
                                         OpcaoModificadorService opcaoService,
                                         AssociacaoVariacaoModificadorService associacaoService,
                                         MontagemConfiguracaoItemService montagemConfiguracaoItemService,
                                         CalculadoraDisponibilidadeConfiguracaoService disponibilidadeConfiguracaoService,
                                         VariacaoItemCardapioService variacaoItemCardapioService,
                                         UsuarioRepository usuarioRepository) {
        this.grupoService = grupoService;
        this.opcaoService = opcaoService;
        this.associacaoService = associacaoService;
        this.montagemConfiguracaoItemService = montagemConfiguracaoItemService;
        this.disponibilidadeConfiguracaoService = disponibilidadeConfiguracaoService;
        this.variacaoItemCardapioService = variacaoItemCardapioService;
        this.usuarioRepository = usuarioRepository;
    }

    @PostMapping("/grupos-modificadores")
    public ResponseEntity<GrupoResponse> criarGrupo(
            @RequestBody GrupoRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarEdicao(ruHeader);
        GrupoModificador grupo = grupoService.criar(
                request.nome(),
                request.descricao(),
                request.codigo(),
                request.tipoSelecao(),
                request.obrigatorio(),
                request.quantidadeMinima(),
                request.quantidadeMaxima(),
                request.permiteRepeticao(),
                request.ativo(),
                request.ordemExibicao(),
                usuario
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(GrupoResponse.from(grupo));
    }

    @GetMapping("/grupos-modificadores")
    public ResponseEntity<List<GrupoResponse>> listarGrupos(
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        validarConsulta(ruHeader);
        return ResponseEntity.ok(grupoService.listar().stream().map(GrupoResponse::from).toList());
    }

    @GetMapping("/grupos-modificadores/{id}")
    public ResponseEntity<GrupoResponse> buscarGrupo(
            @PathVariable long id,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        validarConsulta(ruHeader);
        return ResponseEntity.ok(GrupoResponse.from(grupoService.buscarObrigatorio(id)));
    }

    @GetMapping("/grupos-modificadores/{id}/opcoes")
    public ResponseEntity<List<OpcaoResponse>> listarOpcoesGrupo(
            @PathVariable long id,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        validarConsulta(ruHeader);
        grupoService.buscarObrigatorio(id);
        return ResponseEntity.ok(opcaoService.listarPorGrupo(id).stream()
                .map(OpcaoResponse::from)
                .toList());
    }

    @PutMapping("/grupos-modificadores/{id}")
    public ResponseEntity<GrupoResponse> atualizarGrupo(
            @PathVariable long id,
            @RequestBody GrupoRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarEdicao(ruHeader);
        return ResponseEntity.ok(GrupoResponse.from(grupoService.atualizar(
                id,
                request.nome(),
                request.descricao(),
                request.codigo(),
                request.tipoSelecao(),
                request.obrigatorio(),
                request.quantidadeMinima(),
                request.quantidadeMaxima(),
                request.permiteRepeticao(),
                request.ativo(),
                request.ordemExibicao(),
                usuario
        )));
    }

    @PatchMapping("/grupos-modificadores/{id}/status")
    public ResponseEntity<GrupoResponse> statusGrupo(
            @PathVariable long id,
            @RequestBody StatusRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarEdicao(ruHeader);
        return ResponseEntity.ok(GrupoResponse.from(grupoService.alterarStatus(id, request.ativo(), usuario)));
    }

    @PostMapping("/grupos-modificadores/{id}/opcoes")
    public ResponseEntity<OpcaoResponse> criarOpcao(
            @PathVariable long id,
            @RequestBody OpcaoRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarEdicao(ruHeader);
        OpcaoModificador opcao = opcaoService.criarOpcao(
                id,
                request.nome(),
                request.descricao(),
                request.codigo(),
                request.acrescimoPreco(),
                request.quantidadeMaximaPorItem(),
                request.selecionadaPorPadrao(),
                request.ativa(),
                request.ordemExibicao(),
                usuario
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(OpcaoResponse.from(opcao));
    }

    @PutMapping("/opcoes-modificadores/{id}")
    public ResponseEntity<OpcaoResponse> atualizarOpcao(
            @PathVariable long id,
            @RequestBody OpcaoRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarEdicao(ruHeader);
        return ResponseEntity.ok(OpcaoResponse.from(opcaoService.atualizarOpcao(
                id,
                request.nome(),
                request.descricao(),
                request.codigo(),
                request.acrescimoPreco(),
                request.quantidadeMaximaPorItem(),
                request.selecionadaPorPadrao(),
                request.ativa(),
                request.ordemExibicao(),
                usuario
        )));
    }

    @PatchMapping("/opcoes-modificadores/{id}/status")
    public ResponseEntity<OpcaoResponse> statusOpcao(
            @PathVariable long id,
            @RequestBody StatusRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarEdicao(ruHeader);
        return ResponseEntity.ok(OpcaoResponse.from(opcaoService.alterarStatus(id, request.ativo(), usuario)));
    }

    @GetMapping("/opcoes-modificadores/{id}/componentes")
    public ResponseEntity<List<ComponenteResponse>> listarComponentesOpcao(
            @PathVariable long id,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        validarConsulta(ruHeader);
        opcaoService.buscarOpcao(id);
        return ResponseEntity.ok(opcaoService.listarComponentes(id).stream()
                .map(ComponenteResponse::from)
                .toList());
    }

    @PostMapping("/opcoes-modificadores/{id}/componentes")
    public ResponseEntity<ComponenteResponse> criarComponente(
            @PathVariable long id,
            @RequestBody ComponenteRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarEdicao(ruHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(ComponenteResponse.from(opcaoService.criarComponente(
                id,
                request.tipoEfeito(),
                request.produtoOrigemId(),
                request.produtoDestinoId(),
                request.quantidadeBase(),
                request.unidadeBaseId(),
                request.multiplicador(),
                request.politicaFalta(),
                request.observacao(),
                request.ordem(),
                usuario
        )));
    }

    @PutMapping("/opcoes-modificadores/{id}/componentes/{componenteId}")
    public ResponseEntity<ComponenteResponse> atualizarComponente(
            @PathVariable long id,
            @PathVariable long componenteId,
            @RequestBody ComponenteRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarEdicao(ruHeader);
        return ResponseEntity.ok(ComponenteResponse.from(opcaoService.atualizarComponente(
                id,
                componenteId,
                request.tipoEfeito(),
                request.produtoOrigemId(),
                request.produtoDestinoId(),
                request.quantidadeBase(),
                request.unidadeBaseId(),
                request.multiplicador(),
                request.politicaFalta(),
                request.observacao(),
                request.ordem(),
                usuario
        )));
    }

    @DeleteMapping("/opcoes-modificadores/{id}/componentes/{componenteId}")
    public ResponseEntity<Void> removerComponente(
            @PathVariable long id,
            @PathVariable long componenteId,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarEdicao(ruHeader);
        opcaoService.removerComponente(id, componenteId, usuario);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/variacoes/{variacaoId}/grupos-modificadores")
    public ResponseEntity<AssociacaoResponse> associarGrupo(
            @PathVariable long variacaoId,
            @RequestBody AssociacaoRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarEdicao(ruHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(AssociacaoResponse.from(associacaoService.associar(
                variacaoId,
                request.grupoId(),
                request.obrigatorioOverride(),
                request.quantidadeMinimaOverride(),
                request.quantidadeMaximaOverride(),
                request.ordemExibicao(),
                request.ativo(),
                usuario
        )));
    }

    @GetMapping("/variacoes/{variacaoId}/grupos-modificadores")
    public ResponseEntity<List<AssociacaoResponse>> listarAssociacoesVariacao(
            @PathVariable long variacaoId,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        validarConsulta(ruHeader);
        variacaoItemCardapioService.buscarObrigatoria(variacaoId);
        return ResponseEntity.ok(associacaoService.listarPorVariacao(variacaoId).stream()
                .map(AssociacaoResponse::from)
                .toList());
    }

    @PutMapping("/variacoes/{variacaoId}/grupos-modificadores/{associacaoId}")
    public ResponseEntity<AssociacaoResponse> atualizarAssociacao(
            @PathVariable long variacaoId,
            @PathVariable long associacaoId,
            @RequestBody AssociacaoRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarEdicao(ruHeader);
        return ResponseEntity.ok(AssociacaoResponse.from(associacaoService.atualizar(
                variacaoId,
                associacaoId,
                request.obrigatorioOverride(),
                request.quantidadeMinimaOverride(),
                request.quantidadeMaximaOverride(),
                request.ordemExibicao(),
                request.ativo(),
                usuario
        )));
    }

    @DeleteMapping("/variacoes/{variacaoId}/grupos-modificadores/{associacaoId}")
    public ResponseEntity<Void> removerAssociacao(
            @PathVariable long variacaoId,
            @PathVariable long associacaoId,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarEdicao(ruHeader);
        associacaoService.inativar(variacaoId, associacaoId, usuario);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/variacoes/{variacaoId}/configuracao")
    public ResponseEntity<ConfiguracaoResponse> configuracao(
            @PathVariable long variacaoId,
            @RequestParam(required = false) Long filialId,
            @RequestParam(required = false) CanalVenda canal,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        validarConsulta(ruHeader);
        if (filialId != null && canal != null) {
            CalculadoraDisponibilidadeConfiguracaoService.ConfiguracaoOpcoesDisponibilidade avaliacao =
                    disponibilidadeConfiguracaoService.avaliarConfiguracaoOpcoes(
                            variacaoItemCardapioService.buscarObrigatoria(variacaoId),
                            filialId,
                            canal
                    );
            return ResponseEntity.ok(ConfiguracaoResponse.from(avaliacao.configuracao(), avaliacao.opcoesPorId()));
        }
        MontagemConfiguracaoItemService.ConfiguracaoVariacao configuracao =
                montagemConfiguracaoItemService.configurar(variacaoItemCardapioService.buscarObrigatoria(variacaoId));
        return ResponseEntity.ok(ConfiguracaoResponse.from(configuracao, java.util.Map.of()));
    }

    @PostMapping("/variacoes/{variacaoId}/validar-configuracao")
    public ResponseEntity<CalculadoraDisponibilidadeConfiguracaoService.ResultadoValidacaoConfiguracao> validarConfiguracao(
            @PathVariable long variacaoId,
            @RequestBody ValidarConfiguracaoRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        validarConsulta(ruHeader);
        return ResponseEntity.ok(disponibilidadeConfiguracaoService.validar(
                variacaoId,
                request.filialId(),
                request.canal(),
                request.quantidadeItem() == null ? BigDecimal.ONE : request.quantidadeItem(),
                request.configuracaoVersao(),
                selecoes(request.selecoes())
        ));
    }

    private Usuario validarConsulta(String ruHeader) {
        return ControllerUtils.resolveUserAndRequireAnyPermission(
                ruHeader,
                usuarioRepository,
                "Voce nao tem permissao para consultar modificadores.",
                Permissao.VER_VENDAS,
                Permissao.VER_ESTOQUE,
                Permissao.VER_FINANCAS
        );
    }

    private Usuario validarEdicao(String ruHeader) {
        return ControllerUtils.resolveUserAndRequirePermission(
                ruHeader,
                usuarioRepository,
                Permissao.EDITAR_ESTOQUE,
                "Voce nao tem permissao para editar modificadores."
        );
    }

    private static List<MontagemConfiguracaoItemService.SelecaoModificadorInput> selecoes(List<SelecaoRequest> selecoes) {
        if (selecoes == null) {
            return List.of();
        }
        return selecoes.stream()
                .map(selecao -> new MontagemConfiguracaoItemService.SelecaoModificadorInput(
                        selecao.grupoId(),
                        selecao.opcaoId(),
                        selecao.quantidade()
                ))
                .toList();
    }

    public record GrupoRequest(
            String nome,
            String descricao,
            String codigo,
            TipoSelecaoModificador tipoSelecao,
            Boolean obrigatorio,
            Integer quantidadeMinima,
            Integer quantidadeMaxima,
            Boolean permiteRepeticao,
            Boolean ativo,
            Integer ordemExibicao
    ) {}

    public record OpcaoRequest(
            String nome,
            String descricao,
            String codigo,
            BigDecimal acrescimoPreco,
            Integer quantidadeMaximaPorItem,
            Boolean selecionadaPorPadrao,
            Boolean ativa,
            Integer ordemExibicao
    ) {}

    public record ComponenteRequest(
            TipoEfeitoModificador tipoEfeito,
            Integer produtoOrigemId,
            Integer produtoDestinoId,
            BigDecimal quantidadeBase,
            Long unidadeBaseId,
            BigDecimal multiplicador,
            PoliticaFaltaIngrediente politicaFalta,
            String observacao,
            Integer ordem
    ) {}

    public record AssociacaoRequest(
            long grupoId,
            Boolean obrigatorioOverride,
            Integer quantidadeMinimaOverride,
            Integer quantidadeMaximaOverride,
            Integer ordemExibicao,
            Boolean ativo
    ) {}

    public record StatusRequest(boolean ativo) {}

    public record ValidarConfiguracaoRequest(
            Long filialId,
            CanalVenda canal,
            BigDecimal quantidadeItem,
            String configuracaoVersao,
            List<SelecaoRequest> selecoes
    ) {}

    public record SelecaoRequest(Long grupoId, Long opcaoId, Integer quantidade) {}

    public record GrupoResponse(
            long id,
            String nome,
            String descricao,
            String codigo,
            TipoSelecaoModificador tipoSelecao,
            boolean obrigatorio,
            int quantidadeMinima,
            int quantidadeMaxima,
            boolean permiteRepeticao,
            boolean ativo,
            int ordemExibicao,
            long version
    ) {
        static GrupoResponse from(GrupoModificador grupo) {
            return new GrupoResponse(
                    grupo.getId(),
                    grupo.getNome(),
                    grupo.getDescricao(),
                    grupo.getCodigo(),
                    grupo.getTipoSelecao(),
                    grupo.isObrigatorio(),
                    grupo.getQuantidadeMinima(),
                    grupo.getQuantidadeMaxima(),
                    grupo.isPermiteRepeticao(),
                    grupo.isAtivo(),
                    grupo.getOrdemExibicao(),
                    grupo.getVersion()
            );
        }
    }

    public record OpcaoResponse(
            long id,
            long grupoId,
            String nome,
            String descricao,
            String codigo,
            BigDecimal acrescimoPreco,
            Integer quantidadeMaximaPorItem,
            boolean selecionadaPorPadrao,
            boolean ativa,
            int ordemExibicao,
            long version
    ) {
        static OpcaoResponse from(OpcaoModificador opcao) {
            return new OpcaoResponse(
                    opcao.getId(),
                    opcao.getGrupoModificador().getId(),
                    opcao.getNome(),
                    opcao.getDescricao(),
                    opcao.getCodigo(),
                    opcao.getAcrescimoPreco(),
                    opcao.getQuantidadeMaximaPorItem(),
                    opcao.isSelecionadaPorPadrao(),
                    opcao.isAtiva(),
                    opcao.getOrdemExibicao(),
                    opcao.getVersion()
            );
        }
    }

    public record ComponenteResponse(
            long id,
            long opcaoId,
            TipoEfeitoModificador tipoEfeito,
            Integer produtoOrigemId,
            Integer produtoDestinoId,
            BigDecimal quantidadeBase,
            Long unidadeBaseId,
            BigDecimal multiplicador,
            PoliticaFaltaIngrediente politicaFalta,
            String observacao,
            int ordem
    ) {
        static ComponenteResponse from(OpcaoModificadorComponente componente) {
            return new ComponenteResponse(
                    componente.getId(),
                    componente.getOpcaoModificador().getId(),
                    componente.getTipoEfeito(),
                    componente.getProdutoOrigem() == null ? null : componente.getProdutoOrigem().getId(),
                    componente.getProdutoDestino() == null ? null : componente.getProdutoDestino().getId(),
                    componente.getQuantidadeBase(),
                    componente.getUnidadeBase() == null ? null : componente.getUnidadeBase().getId(),
                    componente.getMultiplicador(),
                    componente.getPoliticaFalta(),
                    componente.getObservacao(),
                    componente.getOrdem()
            );
        }
    }

    public record AssociacaoResponse(
            long id,
            long variacaoId,
            long grupoId,
            Boolean obrigatorioOverride,
            Integer quantidadeMinimaOverride,
            Integer quantidadeMaximaOverride,
            boolean obrigatorioEfetivo,
            int minimoEfetivo,
            int maximoEfetivo,
            int ordemExibicao,
            boolean ativo,
            long version
    ) {
        static AssociacaoResponse from(VariacaoGrupoModificador associacao) {
            return new AssociacaoResponse(
                    associacao.getId(),
                    associacao.getVariacaoItemCardapio().getId(),
                    associacao.getGrupoModificador().getId(),
                    associacao.getObrigatorioOverride(),
                    associacao.getQuantidadeMinimaOverride(),
                    associacao.getQuantidadeMaximaOverride(),
                    associacao.obrigatorioEfetivo(),
                    associacao.minimoEfetivo(),
                    associacao.maximoEfetivo(),
                    associacao.getOrdemExibicao(),
                    associacao.isAtivo(),
                    associacao.getVersion()
            );
        }
    }

    public record ConfiguracaoResponse(
            long variacaoId,
            String nome,
            BigDecimal precoBase,
            String configuracaoVersao,
            List<GrupoConfiguracaoResponse> grupos
    ) {
        static ConfiguracaoResponse from(MontagemConfiguracaoItemService.ConfiguracaoVariacao configuracao) {
            return from(configuracao, java.util.Map.of());
        }

        static ConfiguracaoResponse from(MontagemConfiguracaoItemService.ConfiguracaoVariacao configuracao,
                                         java.util.Map<Long, CalculadoraDisponibilidadeConfiguracaoService.OpcaoDisponibilidade> disponibilidadePorOpcao) {
            return new ConfiguracaoResponse(
                    configuracao.variacao().getId(),
                    configuracao.variacao().getNome(),
                    configuracao.variacao().getPrecoVenda(),
                    configuracao.configuracaoHash(),
                    configuracao.grupos().stream()
                            .map(grupo -> GrupoConfiguracaoResponse.from(grupo, disponibilidadePorOpcao))
                            .toList()
            );
        }
    }

    public record GrupoConfiguracaoResponse(
            long associacaoId,
            long grupoId,
            String nome,
            String codigo,
            String descricao,
            TipoSelecaoModificador tipoSelecao,
            boolean obrigatorio,
            int minimo,
            int maximo,
            boolean permiteRepeticao,
            boolean ativo,
            int ordemExibicao,
            List<OpcaoConfiguracaoResponse> opcoes
    ) {
        static GrupoConfiguracaoResponse from(MontagemConfiguracaoItemService.GrupoConfiguracao grupo,
                                              java.util.Map<Long, CalculadoraDisponibilidadeConfiguracaoService.OpcaoDisponibilidade> disponibilidadePorOpcao) {
            return new GrupoConfiguracaoResponse(
                    grupo.associacaoId(),
                    grupo.grupoId(),
                    grupo.nome(),
                    grupo.codigo(),
                    grupo.descricao(),
                    grupo.tipoSelecao(),
                    grupo.obrigatorio(),
                    grupo.minimo(),
                    grupo.maximo(),
                    grupo.permiteRepeticao(),
                    grupo.ativo(),
                    grupo.ordemExibicao(),
                    grupo.opcoes().stream()
                            .map(opcao -> OpcaoConfiguracaoResponse.from(opcao, disponibilidadePorOpcao.get(opcao.opcaoId())))
                            .toList()
            );
        }
    }

    public record OpcaoConfiguracaoResponse(
            long opcaoId,
            String nome,
            String codigo,
            String descricao,
            BigDecimal acrescimo,
            Integer quantidadeMaximaPorItem,
            boolean selecionadaPorPadrao,
            boolean ativa,
            boolean disponivel,
            String estado,
            Integer capacidadeEstimada,
            boolean aplicacaoAutomaticaPermitida,
            boolean requerConfirmacao,
            List<service.DisponibilidadeDtos.MotivoDisponibilidade> motivos,
            List<service.DisponibilidadeDtos.MotivoDisponibilidade> alertas,
            int ordemExibicao
    ) {
        static OpcaoConfiguracaoResponse from(MontagemConfiguracaoItemService.OpcaoConfiguracao opcao,
                                              CalculadoraDisponibilidadeConfiguracaoService.OpcaoDisponibilidade disponibilidade) {
            boolean disponivel = disponibilidade == null ? opcao.ativa() : disponibilidade.disponivel();
            boolean gratuito = opcao.acrescimo() == null || opcao.acrescimo().compareTo(BigDecimal.ZERO) == 0;
            return new OpcaoConfiguracaoResponse(
                    opcao.opcaoId(),
                    opcao.nome(),
                    opcao.codigo(),
                    opcao.descricao(),
                    opcao.acrescimo(),
                    opcao.quantidadeMaximaPorItem(),
                    opcao.selecionadaPorPadrao(),
                    opcao.ativa(),
                    disponivel,
                    disponibilidade == null ? (opcao.ativa() ? "DISPONIVEL" : "INDISPONIVEL") : disponibilidade.estado(),
                    disponibilidade == null ? null : disponibilidade.capacidadeEstimada(),
                    opcao.selecionadaPorPadrao() && opcao.ativa() && disponivel && gratuito,
                    opcao.selecionadaPorPadrao() && !gratuito,
                    disponibilidade == null ? List.of() : disponibilidade.motivos(),
                    disponibilidade == null ? List.of() : disponibilidade.alertas(),
                    opcao.ordemExibicao()
            );
        }
    }
}
