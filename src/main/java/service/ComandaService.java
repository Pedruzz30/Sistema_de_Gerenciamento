package service;

import model.CanalVenda;
import model.Comanda;
import model.LogAcao;
import model.Usuario;
import model.Venda;
import model.VendaItem;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import repository.ComandaRepository;
import repository.LogRepository;
import repository.VendaItemRepository;
import repository.VendaRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Comandas de quem consome na loja. Abrir cria a venda em rascunho; os itens entram pelas
 * rotas de sempre da venda; pagar no caixa finaliza a venda e fecha a comanda.
 */
public class ComandaService {

    public record Resumo(int numero, long vendaId, LocalDateTime abertaEm, BigDecimal itens, BigDecimal total) {}

    private final ComandaRepository comandaRepository;
    private final VendaService vendaService;
    private final VendaRepository vendaRepository;
    private final VendaItemRepository itemRepository;
    private final LogRepository logRepository;
    private final Clock clock;
    private VendaItemService vendaItemService;
    private repository.VendaItemModificadorRepository modificadorRepository;

    /** Para transferir parte de um item: reduz a linha de origem e cria outra no destino. */
    public void setTransferencia(VendaItemService vendaItemService, repository.VendaItemModificadorRepository modificadorRepository) {
        this.vendaItemService = vendaItemService;
        this.modificadorRepository = modificadorRepository;
    }

    public ComandaService(ComandaRepository comandaRepository,
                          VendaService vendaService,
                          VendaRepository vendaRepository,
                          VendaItemRepository itemRepository,
                          LogRepository logRepository,
                          Clock clock) {
        this.comandaRepository = comandaRepository;
        this.vendaService = vendaService;
        this.vendaRepository = vendaRepository;
        this.itemRepository = itemRepository;
        this.logRepository = logRepository;
        this.clock = clock;
    }

    /** Bipar o cartão: devolve a comanda aberta com esse número, ou abre uma nova. */
    @Transactional
    public Comanda abrirOuRetomar(Usuario usuario, int numero) {
        Comanda.validarNumero(numero);
        Optional<Comanda> aberta = comandaRepository.buscarAbertaPorNumero(numero);
        if (aberta.isPresent()) {
            return aberta.get();
        }
        Venda venda = vendaService.criarVenda(usuario, null, CanalVenda.SALAO, List.of());
        try {
            Comanda comanda = comandaRepository.salvar(
                    new Comanda(numero, venda.getId(), usuario == null ? null : usuario.getRu(), LocalDateTime.now(clock)));
            logRepository.salvar(new LogAcao(0, usuario == null ? null : usuario.getRu(), "COMANDA_ABERTA",
                    "Comanda " + numero + " aberta (venda " + venda.getId() + ")."));
            return comanda;
        } catch (DataIntegrityViolationException e) {
            // Outro caixa abriu o mesmo número no mesmo instante.
            throw new ErroNegocioException("COMANDA_JA_ABERTA", "A comanda " + numero + " acabou de ser aberta em outro caixa. Bipe de novo.");
        }
    }

    @Transactional(readOnly = true)
    public List<Resumo> listarAbertas() {
        return comandaRepository.listarAbertas().stream().map(this::resumir).toList();
    }

    private Resumo resumir(Comanda comanda) {
        List<VendaItem> itens = itemRepository.listarAtivosPorVenda(comanda.getVendaId());
        BigDecimal quantidade = itens.stream().map(VendaItem::getQuantidade).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = itens.stream().map(VendaItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Resumo(comanda.getNumero(), comanda.getVendaId(), comanda.getAbertaEm(),
                quantidade.stripTrailingZeros(), total.setScale(2, RoundingMode.HALF_UP));
    }

    /** Chamado depois que a venda é paga no caixa. Venda sem comanda não muda nada. */
    @Transactional
    public void fecharPorVenda(Usuario usuario, long vendaId) {
        comandaRepository.buscarPorVendaId(vendaId).filter(Comanda::isAberta).ifPresent(comanda -> {
            comanda.fechar(LocalDateTime.now(clock));
            comandaRepository.salvar(comanda);
            logRepository.salvar(new LogAcao(0, usuario == null ? null : usuario.getRu(), "COMANDA_FECHADA",
                    "Comanda " + comanda.getNumero() + " paga (venda " + vendaId + ")."));
        });
    }

    /** Comanda aberta por engano. Com itens, é preciso tirar os itens antes. */
    @Transactional
    public void cancelarVazia(Usuario usuario, int numero) {
        Comanda comanda = comandaRepository.buscarAbertaPorNumero(numero)
                .orElseThrow(() -> new ErroNegocioException("COMANDA_NAO_ENCONTRADA", "Nao ha comanda aberta com o numero " + numero + "."));
        if (!itemRepository.listarAtivosPorVenda(comanda.getVendaId()).isEmpty()) {
            throw new ErroNegocioException("COMANDA_COM_ITENS", "A comanda " + numero + " tem itens. Tire os itens antes de excluir.");
        }
        comanda.cancelar(LocalDateTime.now(clock));
        comandaRepository.salvar(comanda);
        logRepository.salvar(new LogAcao(0, usuario == null ? null : usuario.getRu(), "COMANDA_CANCELADA",
                "Comanda " + numero + " excluida vazia."));
    }

    public record ItemTransferido(long itemId, BigDecimal quantidade) {}

    public record Transferencia(int destino, long vendaDestinoId, int itensMovidos, boolean origemVazia,
                                Integer comandaOrigemFechada) {}

    /**
     * Passa itens de uma venda em rascunho (outra comanda ou o balcão) para a comanda de destino,
     * abrindo-a se preciso. Tudo ou nada. Se a comanda de origem ficar vazia, ela fecha: é o
     * "juntar comandas".
     */
    @Transactional
    public Transferencia transferir(Usuario usuario, int numeroDestino, long vendaOrigemId, List<ItemTransferido> itens) {
        if (itens == null || itens.stream().noneMatch(i -> i.quantidade() != null && i.quantidade().signum() > 0)) {
            throw new ErroNegocioException("TRANSFERENCIA_VAZIA", "Escolha o que transferir.");
        }
        Comanda destino = abrirOuRetomar(usuario, numeroDestino);
        if (destino.getVendaId() == vendaOrigemId) {
            throw new ErroNegocioException("TRANSFERENCIA_MESMA_COMANDA", "Os itens já estão na comanda " + numeroDestino + ".");
        }
        // Trava as duas vendas sempre na mesma ordem, para dois caixas não se travarem.
        long primeira = Math.min(vendaOrigemId, destino.getVendaId());
        long segunda = Math.max(vendaOrigemId, destino.getVendaId());
        Venda a = travarRascunho(primeira);
        Venda b = travarRascunho(segunda);
        Venda vendaDestino = a.getId() == destino.getVendaId() ? a : b;

        int movidos = 0;
        for (ItemTransferido pedido : itens) {
            if (pedido.quantidade() == null || pedido.quantidade().signum() <= 0) continue;
            VendaItem item = itemRepository.buscarPorId(pedido.itemId())
                    .filter(i -> i.getVenda().getId() == vendaOrigemId && i.getStatus() == model.StatusVendaItem.ATIVO)
                    .orElseThrow(() -> new ErroNegocioException("ITEM_VENDA_NAO_ENCONTRADO", "Item não encontrado nesta conta."));
            int comparacao = pedido.quantidade().compareTo(item.getQuantidade());
            if (comparacao > 0) {
                throw new ErroNegocioException("TRANSFERENCIA_QUANTIDADE", "Só há " + item.getQuantidade().stripTrailingZeros().toPlainString()
                        + " de " + item.getNomeItemSnapshot() + " para transferir.");
            }
            if (comparacao == 0) {
                item.transferirPara(vendaDestino);
                itemRepository.salvar(item);
            } else {
                boolean temAdicionais = item.isComposicaoMultipla()
                        || (modificadorRepository != null && !modificadorRepository.listarPorItem(item.getId()).isEmpty());
                if (temAdicionais || vendaItemService == null) {
                    throw new ErroNegocioException("TRANSFERENCIA_ITEM_INTEIRO",
                            item.getNomeItemSnapshot() + " tem adicionais: transfira o item inteiro.");
                }
                long variacaoId = item.getVariacaoItemCardapio().getId();
                vendaItemService.editarItemRascunho(usuario, vendaOrigemId, item.getId(), variacaoId,
                        item.getQuantidade().subtract(pedido.quantidade()), item.getObservacao(), null, List.of(), null, List.of());
                vendaItemService.adicionarItem(usuario, vendaDestino.getId(), variacaoId, pedido.quantidade(), item.getObservacao());
            }
            movidos++;
        }

        boolean origemVazia = itemRepository.listarAtivosPorVenda(vendaOrigemId).isEmpty();
        Integer fechada = null;
        if (origemVazia) {
            Optional<Comanda> origem = comandaRepository.buscarPorVendaId(vendaOrigemId).filter(Comanda::isAberta);
            if (origem.isPresent()) {
                origem.get().cancelar(LocalDateTime.now(clock));
                comandaRepository.salvar(origem.get());
                fechada = origem.get().getNumero();
            }
        }
        logRepository.salvar(new LogAcao(0, usuario == null ? null : usuario.getRu(), "COMANDA_TRANSFERENCIA",
                movidos + " item(ns) da venda " + vendaOrigemId + " para a comanda " + numeroDestino
                        + (fechada != null ? "; comanda " + fechada + " juntada e fechada." : ".")));
        return new Transferencia(numeroDestino, vendaDestino.getId(), movidos, origemVazia, fechada);
    }

    private Venda travarRascunho(long vendaId) {
        Venda venda = vendaRepository.buscarPorIdComLock(vendaId)
                .orElseThrow(() -> new ErroNegocioException("VENDA_NAO_ENCONTRADA", "Venda nao encontrada."));
        try {
            venda.exigirRascunho();
        } catch (IllegalStateException e) {
            throw new ErroNegocioException("VENDA_NAO_ESTA_EM_RASCUNHO", "Só dá para transferir de uma conta ainda não paga.");
        }
        return venda;
    }

    public Optional<Venda> vendaDa(Comanda comanda) {
        return vendaRepository.buscarPorId(comanda.getVendaId());
    }
}
