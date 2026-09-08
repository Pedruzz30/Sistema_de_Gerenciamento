package service;

import model.FichaTecnicaVersao;
import model.Permissao;
import model.Usuario;
import model.VariacaoItemCardapio;
import model.Venda;
import model.VendaItem;
import model.VendaItemModificador;
import model.VendaItemParte;
import repository.FichaTecnicaRepository;
import org.springframework.transaction.annotation.Transactional;
import repository.VariacaoItemCardapioRepository;
import repository.VendaItemRepository;
import repository.VendaItemModificadorRepository;
import repository.VendaItemParteRepository;
import repository.VendaRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Transactional(readOnly = true)
public class VendaItemService {

    private final VendaRepository vendaRepository;
    private final VendaItemRepository vendaItemRepository;
    private final VariacaoItemCardapioRepository variacaoRepository;
    private final FichaTecnicaRepository fichaTecnicaRepository;
    private final MontagemConfiguracaoItemService montagemConfiguracaoItemService;
    private final ComposicaoMultiplaService composicaoMultiplaService;
    private final VendaItemModificadorRepository vendaItemModificadorRepository;
    private final VendaItemParteRepository vendaItemParteRepository;

    public VendaItemService(VendaRepository vendaRepository,
                            VendaItemRepository vendaItemRepository,
                            VariacaoItemCardapioRepository variacaoRepository,
                            FichaTecnicaRepository fichaTecnicaRepository,
                            MontagemConfiguracaoItemService montagemConfiguracaoItemService,
                            ComposicaoMultiplaService composicaoMultiplaService,
                            VendaItemModificadorRepository vendaItemModificadorRepository,
                            VendaItemParteRepository vendaItemParteRepository) {
        this.vendaRepository = vendaRepository;
        this.vendaItemRepository = vendaItemRepository;
        this.variacaoRepository = variacaoRepository;
        this.fichaTecnicaRepository = fichaTecnicaRepository;
        this.montagemConfiguracaoItemService = montagemConfiguracaoItemService;
        this.composicaoMultiplaService = composicaoMultiplaService;
        this.vendaItemModificadorRepository = vendaItemModificadorRepository;
        this.vendaItemParteRepository = vendaItemParteRepository;
    }

    @Transactional
    public VendaItem adicionarItem(Usuario usuario,
                                   long vendaId,
                                   long variacaoId,
                                   BigDecimal quantidade,
                                   String observacao) {
        return adicionarItem(usuario, vendaId, variacaoId, quantidade, observacao, null, List.of());
    }

    @Transactional
    public VendaItem adicionarItem(Usuario usuario,
                                   long vendaId,
                                   long variacaoId,
                                   BigDecimal quantidade,
                                   String observacao,
                                   String configuracaoVersao,
                                   List<MontagemConfiguracaoItemService.SelecaoModificadorInput> selecoes) {
        return adicionarItem(
                usuario,
                vendaId,
                variacaoId,
                quantidade,
                observacao,
                configuracaoVersao,
                selecoes,
                null,
                List.of()
        );
    }

    @Transactional
    public VendaItem adicionarItem(Usuario usuario,
                                   long vendaId,
                                   long variacaoId,
                                   BigDecimal quantidade,
                                   String observacao,
                                   String configuracaoVersao,
                                   List<MontagemConfiguracaoItemService.SelecaoModificadorInput> selecoes,
                                   String configuracaoMultissaborVersao,
                                   List<ComposicaoMultiplaService.ParteInput> partes) {
        validarPermissaoVenda(usuario);
        Venda venda = buscarVendaComLock(vendaId);
        exigirRascunho(venda);
        VariacaoItemCardapio variacao = variacaoRepository.buscarPorId(variacaoId)
                .orElseThrow(() -> new ErroNegocioException(
                        "VARIACAO_NAO_ENCONTRADA",
                        "Variacao de cardapio nao encontrada."
                ));
        if (!variacao.isAtiva()) {
            throw new ErroNegocioException("VARIACAO_INATIVA", "Variacao inativa nao pode ser vendida.");
        }
        PreparoItemRascunho preparo = prepararRascunho(
                variacao,
                quantidade,
                configuracaoVersao,
                selecoes,
                configuracaoMultissaborVersao,
                partes
        );
        VendaItem item = new VendaItem(
                0,
                venda,
                variacao.getItemCardapio(),
                variacao,
                quantidade,
                observacao
        );
        registrarFichaObservadaSeAplicavel(item, variacao);
        if (preparo.composicaoMultipla() != null) {
            item.registrarComposicaoMultipla(
                    preparo.composicaoMultipla().configuracao().getPoliticaPreco(),
                    preparo.composicaoMultipla().partes().size(),
                    preparo.composicaoMultipla().resumoPartes(),
                    preparo.composicaoMultipla().configuracaoHash(),
                    preparo.precoUnitarioBase(),
                    preparo.acrescimoUnitario()
            );
        }
        item.registrarConfiguracaoModificadoresObservada(
                preparo.configuracaoGlobal().configuracaoHash(),
                preparo.precoUnitarioBase(),
                preparo.acrescimoUnitario()
        );
        item = vendaItemRepository.salvar(item);
        List<VendaItemParte> partesPersistidas = persistirPartes(item, preparo);
        persistirModificadores(item, partesPersistidas, preparo);
        return item;
    }

    @Transactional
    public void removerItemRascunho(Usuario usuario, long vendaId, long itemId) {
        validarPermissaoVenda(usuario);
        Venda venda = buscarVendaComLock(vendaId);
        exigirRascunho(venda);
        VendaItem item = vendaItemRepository.buscarPorId(itemId)
                .orElseThrow(() -> new ErroNegocioException(
                        "ITEM_VENDA_NAO_ENCONTRADO",
                        "Item da venda nao encontrado."
                ));
        if (item.getVenda().getId() != venda.getId()) {
            throw new ErroNegocioException("ITEM_VENDA_NAO_ENCONTRADO", "Item nao pertence a venda informada.");
        }
        vendaItemModificadorRepository.removerPorItem(item.getId());
        vendaItemParteRepository.removerPorItem(item.getId());
        vendaItemRepository.remover(item);
    }

    @Transactional
    public VendaItem editarItemRascunho(Usuario usuario,
                                        long vendaId,
                                        long itemId,
                                        long variacaoId,
                                        BigDecimal quantidade,
                                        String observacao,
                                        String configuracaoVersao,
                                        List<MontagemConfiguracaoItemService.SelecaoModificadorInput> selecoes) {
        return editarItemRascunho(
                usuario,
                vendaId,
                itemId,
                variacaoId,
                quantidade,
                observacao,
                configuracaoVersao,
                selecoes,
                null,
                List.of()
        );
    }

    @Transactional
    public VendaItem editarItemRascunho(Usuario usuario,
                                        long vendaId,
                                        long itemId,
                                        long variacaoId,
                                        BigDecimal quantidade,
                                        String observacao,
                                        String configuracaoVersao,
                                        List<MontagemConfiguracaoItemService.SelecaoModificadorInput> selecoes,
                                        String configuracaoMultissaborVersao,
                                        List<ComposicaoMultiplaService.ParteInput> partes) {
        validarPermissaoVenda(usuario);
        Venda venda = buscarVendaComLock(vendaId);
        exigirRascunho(venda);
        VendaItem item = vendaItemRepository.buscarPorId(itemId)
                .orElseThrow(() -> new ErroNegocioException(
                        "ITEM_VENDA_NAO_ENCONTRADO",
                        "Item da venda nao encontrado."
                ));
        if (item.getVenda().getId() != venda.getId()) {
            throw new ErroNegocioException("ITEM_VENDA_NAO_ENCONTRADO", "Item nao pertence a venda informada.");
        }
        VariacaoItemCardapio variacao = variacaoRepository.buscarPorId(variacaoId)
                .orElseThrow(() -> new ErroNegocioException(
                        "VARIACAO_NAO_ENCONTRADA",
                        "Variacao de cardapio nao encontrada."
                ));
        if (!variacao.isAtiva()) {
            throw new ErroNegocioException("VARIACAO_INATIVA", "Variacao inativa nao pode ser vendida.");
        }
        PreparoItemRascunho preparo = prepararRascunho(
                variacao,
                quantidade,
                configuracaoVersao,
                selecoes,
                configuracaoMultissaborVersao,
                partes
        );

        item.atualizarRascunho(variacao, quantidade, observacao);
        registrarFichaObservadaSeAplicavel(item, variacao);
        if (preparo.composicaoMultipla() != null) {
            item.registrarComposicaoMultipla(
                    preparo.composicaoMultipla().configuracao().getPoliticaPreco(),
                    preparo.composicaoMultipla().partes().size(),
                    preparo.composicaoMultipla().resumoPartes(),
                    preparo.composicaoMultipla().configuracaoHash(),
                    preparo.precoUnitarioBase(),
                    preparo.acrescimoUnitario()
            );
        }
        item.registrarConfiguracaoModificadoresObservada(
                preparo.configuracaoGlobal().configuracaoHash(),
                preparo.precoUnitarioBase(),
                preparo.acrescimoUnitario()
        );
        item = vendaItemRepository.salvar(item);
        vendaItemModificadorRepository.removerPorItem(item.getId());
        vendaItemParteRepository.removerPorItem(item.getId());
        List<VendaItemParte> partesPersistidas = persistirPartes(item, preparo);
        persistirModificadores(item, partesPersistidas, preparo);
        return item;
    }

    private PreparoItemRascunho prepararRascunho(
            VariacaoItemCardapio variacao,
            BigDecimal quantidade,
            String configuracaoVersao,
            List<MontagemConfiguracaoItemService.SelecaoModificadorInput> selecoes,
            String configuracaoMultissaborVersao,
            List<ComposicaoMultiplaService.ParteInput> partes) {
        MontagemConfiguracaoItemService.MontagemConfiguracao configuracaoGlobal =
                montagemConfiguracaoItemService.montar(variacao, quantidade, selecoes == null ? List.of() : selecoes, true);
        montagemConfiguracaoItemService.validarHashObservado(configuracaoVersao, configuracaoGlobal.configuracaoHash());
        ComposicaoMultiplaService.PreparacaoComposicaoMultipla composicaoMultipla =
                composicaoMultiplaService.preparar(variacao, partes == null ? List.of() : partes);
        if (composicaoMultipla == null) {
            return new PreparoItemRascunho(
                    configuracaoGlobal,
                    null,
                    List.of(),
                    variacao.getPrecoVenda(),
                    configuracaoGlobal.acrescimoUnitario()
            );
        }
        composicaoMultiplaService.validarHashObservado(
                configuracaoMultissaborVersao,
                composicaoMultipla.configuracaoHash()
        );
        List<MontagemConfiguracaoItemService.MontagemConfiguracao> configuracoesPartes = new ArrayList<>();
        BigDecimal acrescimoUnitario = configuracaoGlobal.acrescimoUnitario();
        for (ComposicaoMultiplaService.ParteResolvida parte : composicaoMultipla.partes()) {
            BigDecimal quantidadeProporcional = quantidade
                    .multiply(parte.proporcao())
                    .setScale(VendaItem.ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
            MontagemConfiguracaoItemService.MontagemConfiguracao configuracaoParte =
                    montagemConfiguracaoItemService.montar(
                            parte.variacaoParte(),
                            quantidadeProporcional,
                            parte.entrada().selecoes() == null ? List.of() : parte.entrada().selecoes(),
                            true
                    );
            montagemConfiguracaoItemService.validarHashObservado(
                    parte.entrada().configuracaoVersao(),
                    configuracaoParte.configuracaoHash()
            );
            configuracoesPartes.add(configuracaoParte);
            acrescimoUnitario = acrescimoUnitario.add(configuracaoParte.acrescimoUnitario());
        }
        return new PreparoItemRascunho(
                configuracaoGlobal,
                composicaoMultipla,
                List.copyOf(configuracoesPartes),
                composicaoMultipla.precoUnitarioBase(),
                acrescimoUnitario
        );
    }

    private List<VendaItemParte> persistirPartes(VendaItem item, PreparoItemRascunho preparo) {
        if (preparo.composicaoMultipla() == null) {
            return List.of();
        }
        List<VendaItemParte> partes = new ArrayList<>();
        for (int i = 0; i < preparo.composicaoMultipla().partes().size(); i++) {
            ComposicaoMultiplaService.ParteResolvida parte = preparo.composicaoMultipla().partes().get(i);
            MontagemConfiguracaoItemService.MontagemConfiguracao configuracaoParte = preparo.configuracoesPartes().get(i);
            partes.add(new VendaItemParte(
                    0,
                    item,
                    parte.variacaoParte(),
                    parte.proporcao(),
                    parte.variacaoParte().getPrecoVenda(),
                    BigDecimal.ZERO,
                    parte.fichaTecnica(),
                    parte.fichaTecnicaVersao(),
                    configuracaoParte.configuracaoHash(),
                    parte.ordemVisual()
            ));
        }
        return vendaItemParteRepository.salvarTodos(partes);
    }

    private void persistirModificadores(VendaItem item,
                                        List<VendaItemParte> partesPersistidas,
                                        PreparoItemRascunho preparo) {
        List<VendaItemModificador> modificadores = new ArrayList<>();
        for (MontagemConfiguracaoItemService.SelecaoAplicada selecao : preparo.configuracaoGlobal().selecoesAplicadas()) {
            modificadores.add(new VendaItemModificador(
                    0,
                    item,
                    null,
                    selecao.associacao().getGrupoModificador(),
                    selecao.opcao(),
                    selecao.quantidade(),
                    selecao.selecaoPadrao()
            ));
        }
        for (int i = 0; i < preparo.configuracoesPartes().size(); i++) {
            VendaItemParte parte = partesPersistidas.get(i);
            MontagemConfiguracaoItemService.MontagemConfiguracao configuracaoParte = preparo.configuracoesPartes().get(i);
            for (MontagemConfiguracaoItemService.SelecaoAplicada selecao : configuracaoParte.selecoesAplicadas()) {
                modificadores.add(new VendaItemModificador(
                        0,
                        item,
                        parte,
                        selecao.associacao().getGrupoModificador(),
                        selecao.opcao(),
                        selecao.quantidade(),
                        selecao.selecaoPadrao()
                ));
            }
        }
        vendaItemModificadorRepository.salvarTodos(modificadores);
    }

    private Venda buscarVendaComLock(long vendaId) {
        return vendaRepository.buscarPorIdComLock(vendaId)
                .orElseThrow(() -> new ErroNegocioException("VENDA_NAO_ENCONTRADA", "Venda nao encontrada."));
    }

    private void exigirRascunho(Venda venda) {
        try {
            venda.exigirRascunho();
        } catch (IllegalStateException e) {
            throw new ErroNegocioException("VENDA_NAO_ESTA_EM_RASCUNHO", "Venda nao esta em rascunho.");
        }
    }

    private void validarPermissaoVenda(Usuario usuario) {
        if (usuario == null
                || usuario.getClasse() == null
                || !usuario.getClasse().possuiPermissao(Permissao.VER_VENDAS)) {
            throw new SecurityException("Voce nao tem permissao para registrar venda.");
        }
    }

    private void registrarFichaObservadaSeAplicavel(VendaItem item, VariacaoItemCardapio variacao) {
        if (!variacao.isPreparada()) {
            return;
        }
        fichaTecnicaRepository.buscarAtivaPorVariacao(variacao.getId()).ifPresent(ficha -> {
            FichaTecnicaVersao versao = ficha.getVersaoPublicadaAtual();
            if (versao != null) {
                item.registrarFichaObservada(ficha, versao);
            }
        });
    }

    private record PreparoItemRascunho(
            MontagemConfiguracaoItemService.MontagemConfiguracao configuracaoGlobal,
            ComposicaoMultiplaService.PreparacaoComposicaoMultipla composicaoMultipla,
            List<MontagemConfiguracaoItemService.MontagemConfiguracao> configuracoesPartes,
            BigDecimal precoUnitarioBase,
            BigDecimal acrescimoUnitario
    ) {}
}
