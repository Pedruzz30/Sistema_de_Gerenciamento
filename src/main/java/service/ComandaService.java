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

    public Optional<Venda> vendaDa(Comanda comanda) {
        return vendaRepository.buscarPorId(comanda.getVendaId());
    }
}
