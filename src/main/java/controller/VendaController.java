package controller;

import model.CanalVenda;
import model.MovimentoEstoque;
import model.Permissao;
import model.StatusVenda;
import model.Usuario;
import model.Venda;
import model.VendaItem;
import model.VendaItemConsumo;
import model.VendaItemModificador;
import model.VendaItemModificadorImpacto;
import org.springframework.format.annotation.DateTimeFormat;
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
import service.CancelamentoVendaService;
import service.CalculadoraCustoVendaService;
import service.ComposicaoMultiplaService;
import service.FinalizacaoVendaService;
import service.MontagemConfiguracaoItemService;
import service.VendaService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/vendas")
public class VendaController {

    private final VendaService vendaService;
    private final FinalizacaoVendaService finalizacaoService;
    private final CancelamentoVendaService cancelamentoService;
    private final UsuarioRepository usuarioRepository;

    public VendaController(VendaService vendaService,
                           FinalizacaoVendaService finalizacaoService,
                           CancelamentoVendaService cancelamentoService,
                           UsuarioRepository usuarioRepository) {
        this.vendaService = vendaService;
        this.finalizacaoService = finalizacaoService;
        this.cancelamentoService = cancelamentoService;
        this.usuarioRepository = usuarioRepository;
    }

    @PostMapping
    public ResponseEntity<VendaResponse> criar(
            @RequestBody CriarVendaRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarVenda(ruHeader);
        Venda venda = vendaService.criarVenda(
                usuario,
                request.filialId(),
                request.canal(),
                request.itens() == null ? List.of() : request.itens().stream()
                        .map(item -> new VendaService.NovoItemVenda(
                                item.variacaoId(),
                                item.quantidade(),
                                item.observacao(),
                                item.configuracaoVersao(),
                                selecoes(item.selecoes()),
                                item.configuracaoMultissaborVersao(),
                                partes(item.partes())
                        ))
                        .toList()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response(venda, usuario));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VendaResponse> buscar(
            @PathVariable long id,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarVisualizacao(ruHeader);
        return ResponseEntity.ok(response(vendaService.buscarObrigatoria(id), usuario));
    }

    @GetMapping
    public ResponseEntity<List<VendaResumoResponse>> listar(
            @RequestParam(required = false) Long filialId,
            @RequestParam(required = false) StatusVenda status,
            @RequestParam(required = false) Long usuarioId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dataFim,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarVisualizacao(ruHeader);
        boolean mostrarCustos = podeVerCustos(usuario);
        return ResponseEntity.ok(vendaService.listar(filialId, status, usuarioId, dataInicio, dataFim).stream()
                .map(venda -> VendaResumoResponse.from(venda, mostrarCustos))
                .toList());
    }

    @PostMapping("/{id}/itens")
    public ResponseEntity<VendaItemResponse> adicionarItem(
            @PathVariable long id,
            @RequestBody ItemVendaRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarVenda(ruHeader);
        VendaItem item = vendaService.adicionarItem(
                usuario,
                id,
                request.variacaoId(),
                request.quantidade(),
                request.observacao(),
                request.configuracaoVersao(),
                selecoes(request.selecoes()),
                request.configuracaoMultissaborVersao(),
                partes(request.partes())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(VendaItemResponse.from(item, List.of(), List.of(), podeVerCustos(usuario)));
    }

    @PutMapping("/{id}/itens/{itemId}")
    public ResponseEntity<VendaItemResponse> editarItemRascunho(
            @PathVariable long id,
            @PathVariable long itemId,
            @RequestBody ItemVendaRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarVenda(ruHeader);
        VendaItem item = vendaService.editarItemRascunho(
                usuario,
                id,
                itemId,
                request.variacaoId(),
                request.quantidade(),
                request.observacao(),
                request.configuracaoVersao(),
                selecoes(request.selecoes()),
                request.configuracaoMultissaborVersao(),
                partes(request.partes())
        );
        return ResponseEntity.ok(VendaItemResponse.from(item, List.of(), vendaService.listarModificadores(id).stream()
                .filter(modificador -> modificador.getVendaItem().getId() == item.getId())
                .toList(), podeVerCustos(usuario)));
    }

    @DeleteMapping("/{id}/itens/{itemId}")
    public ResponseEntity<Void> removerItemRascunho(
            @PathVariable long id,
            @PathVariable long itemId,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarVenda(ruHeader);
        vendaService.removerItemRascunho(usuario, id, itemId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/finalizar")
    public ResponseEntity<VendaResponse> finalizar(
            @PathVariable long id,
            @RequestBody(required = false) FinalizarVendaRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyHeader,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarVenda(ruHeader);
        String chave = idempotencyHeader != null && !idempotencyHeader.isBlank()
                ? idempotencyHeader
                : request == null ? null : request.idempotencyKey();
        Venda venda = finalizacaoService.finalizar(
                usuario,
                id,
                chave,
                request == null ? null : request.numeroCaixaLegado()
        );
        return ResponseEntity.ok(response(venda, usuario));
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<VendaResponse> cancelar(
            @PathVariable long id,
            @RequestBody CancelarVendaRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyHeader,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarCancelamento(ruHeader);
        String chave = idempotencyHeader != null && !idempotencyHeader.isBlank()
                ? idempotencyHeader
                : request == null ? null : request.idempotencyKey();
        Venda venda = cancelamentoService.cancelarVenda(
                usuario,
                id,
                request == null ? null : request.motivo(),
                chave
        );
        return ResponseEntity.ok(response(venda, usuario));
    }

    @PostMapping("/{id}/itens/{itemId}/cancelar")
    public ResponseEntity<VendaResponse> cancelarItem(
            @PathVariable long id,
            @PathVariable long itemId,
            @RequestBody CancelarVendaRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyHeader,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarCancelamento(ruHeader);
        String chave = idempotencyHeader != null && !idempotencyHeader.isBlank()
                ? idempotencyHeader
                : request == null ? null : request.idempotencyKey();
        Venda venda = cancelamentoService.cancelarItem(
                usuario,
                id,
                itemId,
                request == null ? null : request.motivo(),
                chave
        );
        return ResponseEntity.ok(response(venda, usuario));
    }

    private VendaResponse response(Venda venda, Usuario usuario) {
        boolean mostrarCustos = podeVerCustos(usuario);
        List<VendaItem> itens = vendaService.listarItens(venda.getId());
        Map<Long, List<model.VendaItemParte>> partesPorItem = vendaService.listarPartes(venda.getId()).stream()
                .collect(Collectors.groupingBy(parte -> parte.getVendaItem().getId()));
        Map<Long, List<VendaItemConsumo>> consumosPorItem = vendaService.listarConsumos(venda.getId()).stream()
                .collect(Collectors.groupingBy(consumo -> consumo.getVendaItem().getId()));
        Map<Long, List<VendaItemModificador>> modificadoresPorItem = vendaService.listarModificadores(venda.getId()).stream()
                .collect(Collectors.groupingBy(modificador -> modificador.getVendaItem().getId()));
        Map<Long, List<VendaItemModificadorImpacto>> impactosPorModificador = vendaService.listarImpactosModificadores(venda.getId()).stream()
                .collect(Collectors.groupingBy(impacto -> impacto.getVendaItemModificador().getId()));
        return VendaResponse.from(venda, itens, partesPorItem, consumosPorItem, modificadoresPorItem, impactosPorModificador, mostrarCustos);
    }

    private Usuario validarVenda(String ruHeader) {
        return ControllerUtils.resolveUserAndRequirePermission(
                ruHeader,
                usuarioRepository,
                Permissao.VER_VENDAS,
                "Voce nao tem permissao para registrar vendas."
        );
    }

    private Usuario validarVisualizacao(String ruHeader) {
        return ControllerUtils.resolveUserAndRequireAnyPermission(
                ruHeader,
                usuarioRepository,
                "Voce nao tem permissao para visualizar vendas.",
                Permissao.VER_VENDAS,
                Permissao.VER_FINANCAS
        );
    }

    private Usuario validarCancelamento(String ruHeader) {
        return ControllerUtils.resolveUserAndRequireAnyPermission(
                ruHeader,
                usuarioRepository,
                "Voce nao tem permissao para cancelar vendas.",
                Permissao.VER_FINANCAS,
                Permissao.EDITAR_ESTOQUE
        );
    }

    private boolean podeVerCustos(Usuario usuario) {
        return usuario != null
                && usuario.getClasse() != null
                && (usuario.getClasse().possuiPermissao(Permissao.VER_FINANCAS)
                || usuario.getClasse().possuiPermissao(Permissao.EDITAR_ESTOQUE));
    }

    private static List<MontagemConfiguracaoItemService.SelecaoModificadorInput> selecoes(
            List<SelecaoModificadorRequest> selecoes) {
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

    private static List<ComposicaoMultiplaService.ParteInput> partes(List<ParteVendaRequest> partes) {
        if (partes == null) {
            return List.of();
        }
        return partes.stream()
                .map(parte -> new ComposicaoMultiplaService.ParteInput(
                        parte.variacaoParteId(),
                        parte.proporcao(),
                        parte.configuracaoVersao(),
                        selecoes(parte.selecoes())
                ))
                .toList();
    }

    public record CriarVendaRequest(Long filialId, CanalVenda canal, List<ItemVendaRequest> itens) {}

    public record ItemVendaRequest(
            long variacaoId,
            BigDecimal quantidade,
            String observacao,
            String configuracaoVersao,
            List<SelecaoModificadorRequest> selecoes,
            String configuracaoMultissaborVersao,
            List<ParteVendaRequest> partes
    ) {}

    public record SelecaoModificadorRequest(Long grupoId, Long opcaoId, Integer quantidade) {}

    public record ParteVendaRequest(
            long variacaoParteId,
            BigDecimal proporcao,
            String configuracaoVersao,
            List<SelecaoModificadorRequest> selecoes
    ) {}

    public record FinalizarVendaRequest(String idempotencyKey, Integer numeroCaixaLegado) {}

    public record CancelarVendaRequest(String motivo, String idempotencyKey) {}

    public record VendaResumoResponse(
            long id,
            String numero,
            Long filialId,
            String status,
            String canal,
            BigDecimal valorTotal,
            BigDecimal custoIngredientesTotal,
            BigDecimal margemBrutaEstimadaTotal,
            String criadaEm,
            String finalizadaEm
    ) {
        public static VendaResumoResponse from(Venda venda, boolean mostrarCustos) {
            return new VendaResumoResponse(
                    venda.getId(),
                    venda.getNumero(),
                    venda.getFilialId(),
                    venda.getStatus().name(),
                    venda.getCanal().name(),
                    venda.getValorTotal(),
                    mostrarCustos ? venda.getCustoIngredientesTotal() : null,
                    mostrarCustos ? venda.getMargemBrutaEstimadaTotal() : null,
                    venda.getCriadaEm().toString(),
                    venda.getFinalizadaEm() == null ? null : venda.getFinalizadaEm().toString()
            );
        }
    }

    public record VendaResponse(
            long id,
            String numero,
            String status,
            Long filialId,
            String canal,
            BigDecimal subtotal,
            BigDecimal descontoTotal,
            BigDecimal acrescimoTotal,
            BigDecimal valorTotal,
            BigDecimal custoIngredientesTotal,
            BigDecimal margemBrutaEstimadaTotal,
            BigDecimal valorAtivo,
            BigDecimal valorCancelado,
            BigDecimal custoAtivo,
            BigDecimal custoCancelado,
            BigDecimal margemBrutaAtiva,
            BigDecimal margemBrutaCancelada,
            String tipoAnalise,
            String criadaEm,
            String finalizadaEm,
            String canceladaEm,
            String motivoCancelamento,
            List<VendaItemResponse> itens
    ) {
        public static VendaResponse from(Venda venda,
                                         List<VendaItem> itens,
                                         Map<Long, List<model.VendaItemParte>> partesPorItem,
                                         Map<Long, List<VendaItemConsumo>> consumosPorItem,
                                         Map<Long, List<VendaItemModificador>> modificadoresPorItem,
                                         Map<Long, List<VendaItemModificadorImpacto>> impactosPorModificador,
                                         boolean mostrarCustos) {
            TotaisVendaCalculados totais = TotaisVendaCalculados.from(itens);
            return new VendaResponse(
                    venda.getId(),
                    venda.getNumero(),
                    venda.getStatus().name(),
                    venda.getFilialId(),
                    venda.getCanal().name(),
                    venda.getSubtotal(),
                    venda.getDescontoTotal(),
                    venda.getAcrescimoTotal(),
                    venda.getValorTotal(),
                    mostrarCustos ? venda.getCustoIngredientesTotal() : null,
                    mostrarCustos ? venda.getMargemBrutaEstimadaTotal() : null,
                    totais.valorAtivo(),
                    totais.valorCancelado(),
                    mostrarCustos ? totais.custoAtivo() : null,
                    mostrarCustos ? totais.custoCancelado() : null,
                    mostrarCustos ? totais.margemAtiva() : null,
                    mostrarCustos ? totais.margemCancelada() : null,
                    mostrarCustos ? CalculadoraCustoVendaService.TIPO_ANALISE : null,
                    venda.getCriadaEm().toString(),
                    venda.getFinalizadaEm() == null ? null : venda.getFinalizadaEm().toString(),
                    venda.getCanceladaEm() == null ? null : venda.getCanceladaEm().toString(),
                    venda.getMotivoCancelamento(),
                    itens.stream()
                            .map(item -> VendaItemResponse.from(
                                    item,
                                    partesPorItem.getOrDefault(item.getId(), List.of()),
                                    consumosPorItem.getOrDefault(item.getId(), List.of()),
                                    modificadoresPorItem.getOrDefault(item.getId(), List.of()),
                                    impactosPorModificador,
                                    mostrarCustos
                            ))
                            .toList()
            );
        }
    }

    private record TotaisVendaCalculados(
            BigDecimal valorAtivo,
            BigDecimal valorCancelado,
            BigDecimal custoAtivo,
            BigDecimal custoCancelado,
            BigDecimal margemAtiva,
            BigDecimal margemCancelada
    ) {
        static TotaisVendaCalculados from(List<VendaItem> itens) {
            BigDecimal valorAtivo = BigDecimal.ZERO;
            BigDecimal valorCancelado = BigDecimal.ZERO;
            BigDecimal custoAtivo = BigDecimal.ZERO;
            BigDecimal custoCancelado = BigDecimal.ZERO;
            BigDecimal margemAtiva = BigDecimal.ZERO;
            BigDecimal margemCancelada = BigDecimal.ZERO;
            for (VendaItem item : itens) {
                if ("CANCELADO".equals(item.getStatus().name())) {
                    valorCancelado = valorCancelado.add(item.getSubtotal());
                    custoCancelado = custoCancelado.add(item.getCustoTotalEstimado());
                    margemCancelada = margemCancelada.add(item.getMargemBrutaTotalEstimada());
                } else {
                    valorAtivo = valorAtivo.add(item.getSubtotal());
                    custoAtivo = custoAtivo.add(item.getCustoTotalEstimado());
                    margemAtiva = margemAtiva.add(item.getMargemBrutaTotalEstimada());
                }
            }
            return new TotaisVendaCalculados(
                    valorAtivo.setScale(4),
                    valorCancelado.setScale(4),
                    custoAtivo.setScale(4),
                    custoCancelado.setScale(4),
                    margemAtiva.setScale(4),
                    margemCancelada.setScale(4)
            );
        }
    }

    public record VendaItemResponse(
            long id,
            int itemCardapioId,
            String item,
            long variacaoId,
            String variacao,
            String codigoVariacao,
            String tipoComposicao,
            BigDecimal quantidade,
            BigDecimal precoUnitarioBase,
            BigDecimal descontoUnitario,
            BigDecimal acrescimoUnitario,
            BigDecimal precoUnitarioFinal,
            BigDecimal subtotal,
            BigDecimal custoUnitarioEstimado,
            BigDecimal custoTotalEstimado,
            BigDecimal margemBrutaTotalEstimada,
            Long fichaTecnicaId,
            Long fichaTecnicaVersaoId,
            Integer numeroVersaoFicha,
            boolean composicaoMultipla,
            String politicaPrecoComposicao,
            Integer quantidadePartes,
            String resumoPartes,
            String configuracaoMultissaborHash,
            String status,
            String observacao,
            List<VendaItemParteResponse> partes,
            List<VendaItemModificadorResponse> modificadores,
            List<ConsumoResponse> consumos
    ) {
        public static VendaItemResponse from(VendaItem item,
                                             List<model.VendaItemParte> partes,
                                             List<VendaItemConsumo> consumos,
                                             List<VendaItemModificador> modificadores,
                                             Map<Long, List<VendaItemModificadorImpacto>> impactosPorModificador,
                                             boolean mostrarCustos) {
            return new VendaItemResponse(
                    item.getId(),
                    item.getItemCardapio().getId(),
                    item.getNomeItemSnapshot(),
                    item.getVariacaoItemCardapio().getId(),
                    item.getNomeVariacaoSnapshot(),
                    item.getCodigoVariacaoSnapshot(),
                    item.getTipoComposicaoSnapshot().name(),
                    item.getQuantidade(),
                    item.getPrecoUnitarioBase(),
                    item.getDescontoUnitario(),
                    item.getAcrescimoUnitario(),
                    item.getPrecoUnitarioFinal(),
                    item.getSubtotal(),
                    mostrarCustos ? item.getCustoUnitarioEstimado() : null,
                    mostrarCustos ? item.getCustoTotalEstimado() : null,
                    mostrarCustos ? item.getMargemBrutaTotalEstimada() : null,
                    item.getFichaTecnica() == null ? null : item.getFichaTecnica().getId(),
                    item.getFichaTecnicaVersao() == null ? null : item.getFichaTecnicaVersao().getId(),
                    item.getNumeroVersaoFicha(),
                    item.isComposicaoMultipla(),
                    item.getPoliticaPrecoComposicao() == null ? null : item.getPoliticaPrecoComposicao().name(),
                    item.getQuantidadePartesSnapshot(),
                    item.getResumoPartesSnapshot(),
                    item.getConfiguracaoMultissaborHashObservada(),
                    item.getStatus().name(),
                    item.getObservacao(),
                    partes.stream().map(VendaItemParteResponse::from).toList(),
                    modificadores.stream()
                            .map(modificador -> VendaItemModificadorResponse.from(
                                    modificador,
                                    impactosPorModificador.getOrDefault(modificador.getId(), List.of()),
                                    mostrarCustos
                            ))
                            .toList(),
                    consumos.stream().map(consumo -> ConsumoResponse.from(consumo, mostrarCustos)).toList()
            );
        }

        public static VendaItemResponse from(VendaItem item,
                                             List<VendaItemConsumo> consumos,
                                             List<VendaItemModificador> modificadores,
                                             boolean mostrarCustos) {
            return from(item, List.of(), consumos, modificadores, Map.of(), mostrarCustos);
        }
    }

    public record VendaItemParteResponse(
            long id,
            long variacaoParteId,
            int itemCardapioParteId,
            String item,
            String variacao,
            String codigoVariacao,
            String tipoComposicao,
            BigDecimal proporcao,
            BigDecimal precoReferencia,
            BigDecimal custoReferencia,
            Long fichaTecnicaId,
            Long fichaTecnicaVersaoId,
            Integer numeroVersaoFicha,
            String configuracaoHash,
            int ordemVisual,
            String status
    ) {
        public static VendaItemParteResponse from(model.VendaItemParte parte) {
            return new VendaItemParteResponse(
                    parte.getId(),
                    parte.getVariacaoParte().getId(),
                    parte.getItemCardapioParte().getId(),
                    parte.getNomeItemSnapshot(),
                    parte.getNomeVariacaoSnapshot(),
                    parte.getCodigoVariacaoSnapshot(),
                    parte.getTipoComposicaoSnapshot().name(),
                    parte.getProporcao(),
                    parte.getPrecoReferenciaSnapshot(),
                    parte.getCustoReferenciaSnapshot(),
                    parte.getFichaTecnica() == null ? null : parte.getFichaTecnica().getId(),
                    parte.getFichaTecnicaVersao() == null ? null : parte.getFichaTecnicaVersao().getId(),
                    parte.getNumeroVersaoFicha(),
                    parte.getConfiguracaoHashSnapshot(),
                    parte.getOrdemVisual(),
                    parte.getStatus().name()
            );
        }
    }

    public record VendaItemModificadorResponse(
            long id,
            long grupoModificadorId,
            long opcaoModificadorId,
            String grupo,
            String opcao,
            String codigoGrupo,
            String codigoOpcao,
            int quantidadeSelecionada,
            boolean selecaoPadrao,
            BigDecimal acrescimoUnitarioSnapshot,
            BigDecimal acrescimoTotalSnapshot,
            List<VendaItemModificadorImpactoResponse> impactos
    ) {
        public static VendaItemModificadorResponse from(VendaItemModificador modificador,
                                                        List<VendaItemModificadorImpacto> impactos,
                                                        boolean mostrarCustos) {
            return new VendaItemModificadorResponse(
                    modificador.getId(),
                    modificador.getGrupoModificador().getId(),
                    modificador.getOpcaoModificador().getId(),
                    modificador.getNomeGrupoSnapshot(),
                    modificador.getNomeOpcaoSnapshot(),
                    modificador.getCodigoGrupoSnapshot(),
                    modificador.getCodigoOpcaoSnapshot(),
                    modificador.getQuantidadeSelecionada(),
                    modificador.isSelecaoPadrao(),
                    modificador.getAcrescimoUnitarioSnapshot(),
                    modificador.getAcrescimoTotalSnapshot(),
                    impactos.stream()
                            .map(impacto -> VendaItemModificadorImpactoResponse.from(impacto, mostrarCustos))
                            .toList()
            );
        }
    }

    public record VendaItemModificadorImpactoResponse(
            long id,
            int produtoId,
            String produto,
            String tipoEfeito,
            BigDecimal quantidadeDeltaBase,
            BigDecimal custoUnitarioSnapshot,
            BigDecimal valorImpactoSnapshot,
            Long vendaItemConsumoId
    ) {
        public static VendaItemModificadorImpactoResponse from(VendaItemModificadorImpacto impacto, boolean mostrarCustos) {
            return new VendaItemModificadorImpactoResponse(
                    impacto.getId(),
                    impacto.getProduto().getId(),
                    impacto.getProduto().getNome(),
                    impacto.getTipoEfeito().name(),
                    impacto.getQuantidadeDeltaBase(),
                    mostrarCustos ? impacto.getCustoUnitarioSnapshot() : null,
                    mostrarCustos ? impacto.getValorImpactoSnapshot() : null,
                    impacto.getVendaItemConsumo() == null ? null : impacto.getVendaItemConsumo().getId()
            );
        }
    }

    public record ConsumoResponse(
            long id,
            int produtoId,
            String produto,
            Long vendaItemParteId,
            BigDecimal quantidadeBasePorUnidade,
            BigDecimal quantidadeBaseTotal,
            String unidadeBase,
            BigDecimal custoMedioUnitarioSnapshot,
            BigDecimal custoTotalSnapshot,
            String origemComposicao,
            Long fichaTecnicaIngredienteId,
            Long movimentoEstoqueId
    ) {
        public static ConsumoResponse from(VendaItemConsumo consumo, boolean mostrarCustos) {
            MovimentoEstoque movimento = consumo.getMovimentoEstoque();
            return new ConsumoResponse(
                    consumo.getId(),
                    consumo.getProduto().getId(),
                    consumo.getNomeProdutoSnapshot(),
                    consumo.getVendaItemParte() == null ? null : consumo.getVendaItemParte().getId(),
                    consumo.getQuantidadeBasePorUnidade(),
                    consumo.getQuantidadeBaseTotal(),
                    consumo.getUnidadeBaseCodigoSnapshot(),
                    mostrarCustos ? consumo.getCustoMedioUnitarioSnapshot() : null,
                    mostrarCustos ? consumo.getCustoTotalSnapshot() : null,
                    consumo.getOrigemComposicao().name(),
                    consumo.getFichaTecnicaIngrediente() == null ? null : consumo.getFichaTecnicaIngrediente().getId(),
                    movimento == null ? null : movimento.getId()
            );
        }
    }
}
