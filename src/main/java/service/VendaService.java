package service;

import model.CanalVenda;
import model.LogAcao;
import model.Permissao;
import model.StatusVenda;
import model.Usuario;
import model.Venda;
import model.VendaItem;
import model.VendaItemConsumo;
import model.VendaItemModificadorImpacto;
import model.VendaItemParte;
import org.springframework.transaction.annotation.Transactional;
import repository.LogRepository;
import repository.VendaItemConsumoRepository;
import repository.VendaItemRepository;
import repository.VendaItemModificadorImpactoRepository;
import repository.VendaItemModificadorRepository;
import repository.VendaItemParteRepository;
import repository.VendaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Transactional(readOnly = true)
public class VendaService {

    private final VendaRepository vendaRepository;
    private final VendaItemRepository vendaItemRepository;
    private final VendaItemConsumoRepository consumoRepository;
    private final VendaItemModificadorRepository modificadorRepository;
    private final VendaItemModificadorImpactoRepository modificadorImpactoRepository;
    private final VendaItemParteRepository vendaItemParteRepository;
    private final VendaItemService vendaItemService;
    private final LogRepository logRepository;

    public VendaService(VendaRepository vendaRepository,
                        VendaItemRepository vendaItemRepository,
                        VendaItemConsumoRepository consumoRepository,
                        VendaItemModificadorRepository modificadorRepository,
                        VendaItemModificadorImpactoRepository modificadorImpactoRepository,
                        VendaItemParteRepository vendaItemParteRepository,
                        VendaItemService vendaItemService,
                        LogRepository logRepository) {
        this.vendaRepository = vendaRepository;
        this.vendaItemRepository = vendaItemRepository;
        this.consumoRepository = consumoRepository;
        this.modificadorRepository = modificadorRepository;
        this.modificadorImpactoRepository = modificadorImpactoRepository;
        this.vendaItemParteRepository = vendaItemParteRepository;
        this.vendaItemService = vendaItemService;
        this.logRepository = logRepository;
    }

    @Transactional
    public Venda criarVenda(Usuario usuario,
                            Long filialId,
                            CanalVenda canal,
                            List<NovoItemVenda> itens) {
        validarPermissaoVenda(usuario);
        Long filial = filialId == null ? ProdutoEstoqueService.FILIAL_PADRAO_ID : filialId;
        Venda venda = vendaRepository.salvar(new Venda(
                0,
                filial,
                gerarNumeroVenda(),
                canal == null ? CanalVenda.BALCAO : canal,
                usuario == null ? null : usuario.getRu()
        ));
        auditar(usuario, "VENDA_CRIADA", "Venda " + venda.getId() + " criada em rascunho.");
        if (itens != null) {
            for (NovoItemVenda item : itens) {
                vendaItemService.adicionarItem(
                        usuario,
                        venda.getId(),
                        item.variacaoId(),
                        item.quantidade(),
                        item.observacao(),
                        item.configuracaoVersao(),
                        item.selecoes(),
                        item.configuracaoMultissaborVersao(),
                        item.partes()
                );
            }
        }
        return buscarObrigatoria(venda.getId());
    }

    public Venda buscarObrigatoria(long vendaId) {
        return vendaRepository.buscarPorId(vendaId)
                .orElseThrow(() -> new ErroNegocioException("VENDA_NAO_ENCONTRADA", "Venda nao encontrada."));
    }

    public List<Venda> listar(Long filialId,
                              StatusVenda status,
                              Long usuarioId,
                              LocalDateTime dataInicio,
                              LocalDateTime dataFim) {
        return vendaRepository.listar(filialId, status, usuarioId, dataInicio, dataFim);
    }

    public List<VendaItem> listarItens(long vendaId) {
        return vendaItemRepository.listarPorVenda(vendaId);
    }

    public List<VendaItemConsumo> listarConsumos(long vendaId) {
        return consumoRepository.listarPorVenda(vendaId);
    }

    public List<VendaItemParte> listarPartes(long vendaId) {
        return vendaItemParteRepository.listarPorVenda(vendaId);
    }

    public List<model.VendaItemModificador> listarModificadores(long vendaId) {
        return modificadorRepository.listarPorVenda(vendaId);
    }

    public List<VendaItemModificadorImpacto> listarImpactosModificadores(long vendaId) {
        return modificadorImpactoRepository.listarPorVenda(vendaId);
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
        VendaItem item = vendaItemService.adicionarItem(
                usuario,
                vendaId,
                variacaoId,
                quantidade,
                observacao,
                configuracaoVersao,
                selecoes == null ? List.of() : selecoes,
                null,
                List.of()
        );
        auditar(usuario, "VENDA_ITEM_INCLUIDO", "Item " + item.getId() + " incluido na venda " + vendaId + ".");
        return item;
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
        VendaItem item = vendaItemService.adicionarItem(
                usuario,
                vendaId,
                variacaoId,
                quantidade,
                observacao,
                configuracaoVersao,
                selecoes == null ? List.of() : selecoes,
                configuracaoMultissaborVersao,
                partes == null ? List.of() : partes
        );
        auditar(usuario, "VENDA_ITEM_INCLUIDO", "Item " + item.getId() + " incluido na venda " + vendaId + ".");
        return item;
    }

    @Transactional
    public void removerItemRascunho(Usuario usuario, long vendaId, long itemId) {
        vendaItemService.removerItemRascunho(usuario, vendaId, itemId);
        auditar(usuario, "VENDA_ITEM_REMOVIDO", "Item " + itemId + " removido da venda " + vendaId + ".");
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
        VendaItem item = vendaItemService.editarItemRascunho(
                usuario,
                vendaId,
                itemId,
                variacaoId,
                quantidade,
                observacao,
                configuracaoVersao,
                selecoes == null ? List.of() : selecoes,
                null,
                List.of()
        );
        auditar(usuario, "VENDA_ITEM_EDITADO", "Item " + itemId + " editado no rascunho da venda " + vendaId + ".");
        return item;
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
        VendaItem item = vendaItemService.editarItemRascunho(
                usuario,
                vendaId,
                itemId,
                variacaoId,
                quantidade,
                observacao,
                configuracaoVersao,
                selecoes == null ? List.of() : selecoes,
                configuracaoMultissaborVersao,
                partes == null ? List.of() : partes
        );
        auditar(usuario, "VENDA_ITEM_EDITADO", "Item " + itemId + " editado no rascunho da venda " + vendaId + ".");
        return item;
    }

    private static String gerarNumeroVenda() {
        return "V" + System.currentTimeMillis() + "-" + Math.abs((int) System.nanoTime());
    }

    private void validarPermissaoVenda(Usuario usuario) {
        if (usuario == null
                || usuario.getClasse() == null
                || !usuario.getClasse().possuiPermissao(Permissao.VER_VENDAS)) {
            throw new SecurityException("Voce nao tem permissao para registrar venda.");
        }
    }

    private void auditar(Usuario usuario, String acao, String descricao) {
        logRepository.salvar(new LogAcao(0, usuario == null ? null : usuario.getRu(), acao, descricao));
    }

    public record NovoItemVenda(
            long variacaoId,
            BigDecimal quantidade,
            String observacao,
            String configuracaoVersao,
            List<MontagemConfiguracaoItemService.SelecaoModificadorInput> selecoes,
            String configuracaoMultissaborVersao,
            List<ComposicaoMultiplaService.ParteInput> partes
    ) {}
}
