package service;

import model.FichaTecnica;
import model.FichaTecnicaVersao;
import model.ItemCardapio;
import model.OrigemComposicaoVenda;
import model.Produto;
import model.UnidadeMedida;
import model.VariacaoItemCardapio;
import model.VendaItem;
import model.VendaItemModificador;
import model.VendaItemParte;
import repository.VendaItemModificadorRepository;
import repository.VendaItemParteRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MontagemComposicaoVendaService {

    private static final int ESCALA_QUANTIDADE = 6;

    private final ResolucaoComposicaoVariacaoService resolucaoComposicaoVariacaoService;
    private final MontagemConfiguracaoItemService montagemConfiguracaoItemService;
    private final ComposicaoMultiplaService composicaoMultiplaService;
    private final VendaItemModificadorRepository vendaItemModificadorRepository;
    private final VendaItemParteRepository vendaItemParteRepository;

    public MontagemComposicaoVendaService(ResolucaoComposicaoVariacaoService resolucaoComposicaoVariacaoService,
                                          MontagemConfiguracaoItemService montagemConfiguracaoItemService,
                                          ComposicaoMultiplaService composicaoMultiplaService,
                                          VendaItemModificadorRepository vendaItemModificadorRepository,
                                          VendaItemParteRepository vendaItemParteRepository) {
        this.resolucaoComposicaoVariacaoService = resolucaoComposicaoVariacaoService;
        this.montagemConfiguracaoItemService = montagemConfiguracaoItemService;
        this.composicaoMultiplaService = composicaoMultiplaService;
        this.vendaItemModificadorRepository = vendaItemModificadorRepository;
        this.vendaItemParteRepository = vendaItemParteRepository;
    }

    public ItemMontado montar(VendaItem item) {
        VariacaoItemCardapio variacao = item.getVariacaoItemCardapio();
        validarVariacaoVendavel(variacao);

        List<VendaItemModificador> modificadores = vendaItemModificadorRepository.listarPorItem(item.getId());
        List<VendaItemParte> partes = vendaItemParteRepository.listarPorItemEStatus(item.getId(), model.StatusVendaItem.ATIVO);
        if (!partes.isEmpty()) {
            return montarComposicaoMultipla(item, modificadores, partes);
        }

        List<ConsumoPlanejado> consumos = new ArrayList<>();
        MontagemConfiguracaoItemService.MontagemConfiguracao configuracao = montagemConfiguracaoItemService.montar(
                variacao,
                item.getQuantidade(),
                modificadores.stream()
                        .map(modificador -> new MontagemConfiguracaoItemService.SelecaoModificadorInput(
                                modificador.getGrupoModificador().getId(),
                                modificador.getOpcaoModificador().getId(),
                                modificador.getQuantidadeSelecionada()
                        ))
                        .toList(),
                false
        );
        montagemConfiguracaoItemService.validarHashObservado(
                item.getConfiguracaoModificadoresHashObservada(),
                configuracao.configuracaoHash()
        );
        ResolucaoComposicaoVariacaoService.ResolucaoComposicao resolucao = configuracao.resolucaoBase();

        for (MontagemConfiguracaoItemService.ConsumoConfigurado componente : configuracao.consumos()) {
            Produto produto = componente.produto();
            validarProdutoVendavel(produto);
            UnidadeMedida unidadeBase = componente.unidadeBase();
            if (unidadeBase == null) {
                throw new ErroNegocioException(
                        "ESTOQUE_PRODUTO_NAO_CONFIGURADO",
                        "Produto do consumo precisa de unidade-base para venda decimal."
                );
            }
            BigDecimal porUnidade = componente.quantidadeBasePorUnidade();
            BigDecimal total = porUnidade.multiply(item.getQuantidade())
                    .setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
            consumos.add(new ConsumoPlanejado(
                    produto,
                    unidadeBase,
                    porUnidade.setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP),
                    total,
                    componente.origem(),
                    null,
                    null,
                    componente.componenteModificador()
            ));
        }

        FichaTecnicaVersao versao = resolucao.fichaTecnicaVersao();
        return new ItemMontado(
                item,
                resolucao.fichaTecnica(),
                versao,
                versao == null ? null : versao.getNumeroVersao(),
                List.copyOf(consumos),
                List.copyOf(modificadores),
                configuracao.impactos(),
                false,
                variacao.getPrecoVenda().setScale(VendaItem.ESCALA_DINHEIRO, RoundingMode.HALF_UP),
                null,
                List.of()
        );
    }

    private ItemMontado montarComposicaoMultipla(VendaItem item,
                                                 List<VendaItemModificador> modificadores,
                                                 List<VendaItemParte> partesPersistidas) {
        Map<Long, List<VendaItemModificador>> modificadoresPorParte = modificadores.stream()
                .filter(modificador -> modificador.getVendaItemParte() != null)
                .collect(Collectors.groupingBy(
                        modificador -> modificador.getVendaItemParte().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<VendaItemModificador> modificadoresGlobais = modificadores.stream()
                .filter(modificador -> modificador.getVendaItemParte() == null)
                .toList();

        List<ComposicaoMultiplaService.ParteInput> entradasPartes = partesPersistidas.stream()
                .map(parte -> new ComposicaoMultiplaService.ParteInput(
                        parte.getVariacaoParte().getId(),
                        parte.getProporcao(),
                        parte.getConfiguracaoHashSnapshot(),
                        selecoes(modificadoresPorParte.getOrDefault(parte.getId(), List.of()))
                ))
                .toList();
        ComposicaoMultiplaService.PreparacaoComposicaoMultipla composicao =
                composicaoMultiplaService.preparar(item.getVariacaoItemCardapio(), entradasPartes);
        composicaoMultiplaService.validarHashObservado(
                item.getConfiguracaoMultissaborHashObservada(),
                composicao.configuracaoHash()
        );

        MontagemConfiguracaoItemService.MontagemConfiguracao configuracaoGlobal =
                montagemConfiguracaoItemService.montar(
                        item.getVariacaoItemCardapio(),
                        item.getQuantidade(),
                        selecoes(modificadoresGlobais),
                        false
                );
        montagemConfiguracaoItemService.validarHashObservado(
                item.getConfiguracaoModificadoresHashObservada(),
                configuracaoGlobal.configuracaoHash()
        );

        List<ConsumoPlanejado> consumos = new ArrayList<>();
        List<MontagemConfiguracaoItemService.ImpactoPlanejado> impactos = new ArrayList<>(configuracaoGlobal.impactos());

        Map<Integer, VendaItemParte> partePorOrdem = partesPersistidas.stream()
                .collect(Collectors.toMap(VendaItemParte::getOrdemVisual, parte -> parte));
        for (ComposicaoMultiplaService.ParteResolvida parteResolvida : composicao.partes()) {
            VendaItemParte partePersistida = partePorOrdem.get(parteResolvida.ordemVisual());
            if (partePersistida == null) {
                throw new ErroNegocioException("CONFIGURACAO_MULTISSABOR_ALTERADA", "Snapshot de parte da venda nao encontrado.");
            }
            MontagemConfiguracaoItemService.MontagemConfiguracao configuracaoParte =
                    montagemConfiguracaoItemService.montar(
                            partePersistida.getVariacaoParte(),
                            item.getQuantidade().multiply(partePersistida.getProporcao()).setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP),
                            selecoes(modificadoresPorParte.getOrDefault(partePersistida.getId(), List.of())),
                            false
                    );
            montagemConfiguracaoItemService.validarHashObservado(
                    partePersistida.getConfiguracaoHashSnapshot(),
                    configuracaoParte.configuracaoHash()
            );
            impactos.addAll(configuracaoParte.impactos());
            for (MontagemConfiguracaoItemService.ConsumoConfigurado componente : configuracaoParte.consumos()) {
                Produto produto = componente.produto();
                validarProdutoVendavel(produto);
                UnidadeMedida unidadeBase = componente.unidadeBase();
                if (unidadeBase == null) {
                    throw new ErroNegocioException(
                            "ESTOQUE_PRODUTO_NAO_CONFIGURADO",
                            "Produto do consumo precisa de unidade-base para venda decimal."
                    );
                }
                BigDecimal porUnidade = componente.quantidadeBasePorUnidade()
                        .multiply(partePersistida.getProporcao())
                        .setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
                if (porUnidade.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                BigDecimal total = porUnidade.multiply(item.getQuantidade())
                        .setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
                consumos.add(new ConsumoPlanejado(
                        produto,
                        unidadeBase,
                        porUnidade,
                        total,
                        componente.origem(),
                        partePersistida,
                        null,
                        componente.componenteModificador()
                ));
            }
        }

        for (MontagemConfiguracaoItemService.ConsumoConfigurado componente : configuracaoGlobal.consumos()) {
            if (componente.origem() != OrigemComposicaoVenda.MODIFICADOR_ADICAO
                    && componente.origem() != OrigemComposicaoVenda.MODIFICADOR_SUBSTITUICAO) {
                continue;
            }
            Produto produto = componente.produto();
            validarProdutoVendavel(produto);
            BigDecimal porUnidade = componente.quantidadeBasePorUnidade().setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
            BigDecimal total = porUnidade.multiply(item.getQuantidade()).setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
            consumos.add(new ConsumoPlanejado(
                    produto,
                    componente.unidadeBase(),
                    porUnidade,
                    total,
                    componente.origem(),
                    null,
                    null,
                    componente.componenteModificador()
            ));
        }

        return new ItemMontado(
                item,
                null,
                null,
                null,
                List.copyOf(consumos),
                List.copyOf(modificadores),
                List.copyOf(impactos),
                true,
                composicao.precoUnitarioBase(),
                composicao.configuracaoHash(),
                List.copyOf(partesPersistidas)
        );
    }

    private static List<MontagemConfiguracaoItemService.SelecaoModificadorInput> selecoes(
            List<VendaItemModificador> modificadores) {
        return modificadores.stream()
                .map(modificador -> new MontagemConfiguracaoItemService.SelecaoModificadorInput(
                        modificador.getGrupoModificador().getId(),
                        modificador.getOpcaoModificador().getId(),
                        modificador.getQuantidadeSelecionada()
                ))
                .toList();
    }

    private void validarVariacaoVendavel(VariacaoItemCardapio variacao) {
        if (variacao == null) {
            throw new ErroNegocioException("VARIACAO_NAO_ENCONTRADA", "Variacao de cardapio nao encontrada.");
        }
        if (!variacao.isAtiva()) {
            throw new ErroNegocioException("VARIACAO_INATIVA", "Variacao inativa nao pode ser vendida.");
        }
        ItemCardapio item = variacao.getItemCardapio();
        if (item == null || !item.isAtivo() || !item.isDisponivel()) {
            throw new ErroNegocioException("ITEM_CARDAPIO_INATIVO", "Item de cardapio inativo ou indisponivel.");
        }
        if (item.getCategoriaCardapio() != null && !item.getCategoriaCardapio().isAtivo()) {
            throw new ErroNegocioException("ITEM_CARDAPIO_INATIVO", "Categoria do item de cardapio esta inativa.");
        }
        if (variacao.getPrecoVenda() == null || variacao.getPrecoVenda().compareTo(BigDecimal.ZERO) < 0) {
            throw new ErroNegocioException("PRECO_VARIACAO_INVALIDO", "Preco da variacao e invalido.");
        }
    }

    private void validarProdutoVendavel(Produto produto) {
        if (produto == null) {
            throw new ErroNegocioException("ESTOQUE_PRODUTO_NAO_CONFIGURADO", "Produto do consumo nao configurado.");
        }
        if (!produto.isAtivo()) {
            throw new ErroNegocioException("PRODUTO_INATIVO", "Produto inativo nao pode ser consumido em venda.");
        }
        if (!produto.isControladoPorEstoque()) {
            throw new ErroNegocioException("ESTOQUE_PRODUTO_NAO_CONFIGURADO", "Produto nao controlado por estoque.");
        }
    }

    public record ItemMontado(
            VendaItem item,
            FichaTecnica fichaTecnica,
            FichaTecnicaVersao fichaTecnicaVersao,
            Integer numeroVersaoFicha,
            List<ConsumoPlanejado> consumos,
            List<VendaItemModificador> modificadores,
            List<MontagemConfiguracaoItemService.ImpactoPlanejado> impactos,
            boolean composicaoMultipla,
            BigDecimal precoUnitarioBaseOficial,
            String configuracaoMultissaborHash,
            List<VendaItemParte> partes
    ) {}

    public record ConsumoPlanejado(
            Produto produto,
            UnidadeMedida unidadeBase,
            BigDecimal quantidadeBasePorUnidade,
            BigDecimal quantidadeBaseTotal,
            OrigemComposicaoVenda origem,
            VendaItemParte vendaItemParte,
            model.FichaTecnicaIngrediente ingrediente,
            model.OpcaoModificadorComponente componenteModificador
    ) {}
}
