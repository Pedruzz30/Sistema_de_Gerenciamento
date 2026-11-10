package service;

import model.CanalVenda;
import model.Comanda;
import model.LogAcao;
import model.Permissao;
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

    private CozinhaService cozinha;

    /** Pedido impresso para a cozinha: lançar, tirar e transferir avisam a cozinha. */
    public void setCozinha(CozinhaService cozinha) {
        this.cozinha = cozinha;
    }

    /** Manda para a cozinha o que é dela e ainda não foi (o caixa chama ao guardar a comanda). */
    @Transactional
    public boolean enviarCozinha(Usuario usuario, int numero) {
        Comanda comanda = comandaRepository.buscarAbertaPorNumero(numero)
                .orElseThrow(() -> new ErroNegocioException("COMANDA_NAO_ENCONTRADA", "Nao ha comanda aberta com o numero " + numero + "."));
        return cozinha != null && cozinha.enviar(usuario, comanda.getVendaId()).isPresent();
    }

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
                VendaItem novo = vendaItemService.adicionarItem(usuario, vendaDestino.getId(), variacaoId, pedido.quantidade(), item.getObservacao());
                if (cozinha != null) cozinha.moverEnviado(item.getId(), novo.getId(), pedido.quantidade());
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

    public record ItemLancado(long variacaoId, BigDecimal quantidade) {}

    public record LinhaDetalhe(String nome, String variacao, BigDecimal quantidade, BigDecimal subtotal) {}

    public record Detalhe(int numero, long vendaId, LocalDateTime abertaEm, BigDecimal itens, BigDecimal total,
                          List<LinhaDetalhe> linhas) {}

    /** O que já está na comanda aberta, para o celular mostrar. */
    @Transactional(readOnly = true)
    public Detalhe detalhar(int numero) {
        Comanda comanda = comandaRepository.buscarAbertaPorNumero(numero)
                .orElseThrow(() -> new ErroNegocioException("COMANDA_NAO_ENCONTRADA", "Nao ha comanda aberta com o numero " + numero + "."));
        return detalhar(comanda);
    }

    private Detalhe detalhar(Comanda comanda) {
        List<VendaItem> itens = itemRepository.listarAtivosPorVenda(comanda.getVendaId());
        List<LinhaDetalhe> linhas = itens.stream()
                .map(i -> new LinhaDetalhe(i.getNomeItemSnapshot(), i.getVariacaoItemCardapio() == null ? "" : i.getVariacaoItemCardapio().getNome(),
                        i.getQuantidade().stripTrailingZeros(), i.getSubtotal().setScale(2, RoundingMode.HALF_UP)))
                .toList();
        Resumo resumo = resumir(comanda);
        return new Detalhe(comanda.getNumero(), comanda.getVendaId(), comanda.getAbertaEm(), resumo.itens(), resumo.total(), linhas);
    }

    /**
     * Lança vários itens de uma vez na comanda (o pedido do celular), abrindo-a se preciso.
     * Item sem adicionais que já está na comanda só ganha quantidade, para a conta não virar
     * uma lista de "1 Skol" repetidos. Tudo ou nada.
     */
    @Transactional
    public Detalhe lancar(Usuario usuario, int numero, List<ItemLancado> itens) {
        List<ItemLancado> validos = itens == null ? List.of()
                : itens.stream().filter(i -> i != null && i.quantidade() != null && i.quantidade().signum() > 0).toList();
        if (validos.isEmpty()) {
            throw new ErroNegocioException("LANCAMENTO_VAZIO", "Escolha o que lançar.");
        }
        if (validos.size() > 50 || validos.stream().anyMatch(i -> i.quantidade().compareTo(BigDecimal.valueOf(999)) > 0)) {
            throw new ErroNegocioException("LANCAMENTO_GRANDE", "Pedido grande demais para um lançamento só.");
        }
        if (vendaItemService == null) {
            throw new IllegalStateException("Lancamento indisponivel.");
        }
        Comanda comanda = abrirOuRetomar(usuario, numero);
        long vendaId = comanda.getVendaId();
        travarRascunho(vendaId);
        StringBuilder descricao = new StringBuilder();
        for (ItemLancado pedido : validos) {
            Optional<VendaItem> mesmo = itemRepository.listarAtivosPorVenda(vendaId).stream()
                    .filter(i -> i.getVariacaoItemCardapio() != null && i.getVariacaoItemCardapio().getId() == pedido.variacaoId())
                    .filter(i -> !i.isComposicaoMultipla() && (i.getObservacao() == null || i.getObservacao().isBlank()))
                    .filter(i -> modificadorRepository == null || modificadorRepository.listarPorItem(i.getId()).isEmpty())
                    .findFirst();
            VendaItem item;
            if (mesmo.isPresent()) {
                item = vendaItemService.editarItemRascunho(usuario, vendaId, mesmo.get().getId(), pedido.variacaoId(),
                        mesmo.get().getQuantidade().add(pedido.quantidade()), null, null, List.of(), null, List.of());
            } else {
                item = vendaItemService.adicionarItem(usuario, vendaId, pedido.variacaoId(), pedido.quantidade(), null);
            }
            if (descricao.length() > 0) descricao.append(", ");
            descricao.append(pedido.quantidade().stripTrailingZeros().toPlainString()).append(" x ").append(item.getNomeItemSnapshot());
        }
        logRepository.salvar(new LogAcao(0, usuario == null ? null : usuario.getRu(), "COMANDA_ITENS_LANCADOS",
                "Comanda " + numero + ": " + descricao + " (por " + nomeDe(usuario) + ")."));
        if (cozinha != null) cozinha.enviar(usuario, vendaId);
        return detalhar(comanda);
    }

    public record ItemRetirado(long itemId, BigDecimal quantidade) {}

    public record Retirada(int numero, int itensRetirados, BigDecimal valor, boolean comandaFechada) {}

    /**
     * Tira itens já lançados numa comanda: lançado errado, cliente desistiu, quebrou.
     * O item pode já ter sido servido, então só quem cuida do financeiro tira (ou o gerente
     * autoriza com a senha), e o motivo vai para o log. Sem itens, a comanda fecha.
     */
    @Transactional
    public Retirada retirar(Usuario operador, Usuario autorizador, int numero, List<ItemRetirado> itens, String motivo) {
        Usuario responsavel = responsavelRetirada(operador, autorizador);
        String motivoLimpo = motivo == null ? "" : motivo.trim().replaceAll("\\s+", " ");
        if (motivoLimpo.length() < 3) {
            throw new ErroNegocioException("RETIRADA_SEM_MOTIVO", "Diga o motivo de tirar o item.");
        }
        if (motivoLimpo.length() > 200) {
            motivoLimpo = motivoLimpo.substring(0, 200);
        }
        if (itens == null || itens.stream().noneMatch(i -> i.quantidade() != null && i.quantidade().signum() > 0)) {
            throw new ErroNegocioException("RETIRADA_VAZIA", "Escolha o que tirar.");
        }
        Comanda comanda = comandaRepository.buscarAbertaPorNumero(numero)
                .orElseThrow(() -> new ErroNegocioException("COMANDA_NAO_ENCONTRADA", "Nao ha comanda aberta com o numero " + numero + "."));
        long vendaId = comanda.getVendaId();
        travarRascunho(vendaId);
        if (vendaItemService == null) {
            throw new IllegalStateException("Retirada de itens indisponivel.");
        }

        BigDecimal valor = BigDecimal.ZERO;
        int retirados = 0;
        StringBuilder descricao = new StringBuilder();
        for (ItemRetirado pedido : itens) {
            if (pedido.quantidade() == null || pedido.quantidade().signum() <= 0) continue;
            VendaItem item = itemRepository.buscarPorId(pedido.itemId())
                    .filter(i -> i.getVenda().getId() == vendaId && i.getStatus() == model.StatusVendaItem.ATIVO)
                    .orElseThrow(() -> new ErroNegocioException("ITEM_VENDA_NAO_ENCONTRADO", "Item não encontrado nesta comanda."));
            int comparacao = pedido.quantidade().compareTo(item.getQuantidade());
            if (comparacao > 0) {
                throw new ErroNegocioException("RETIRADA_QUANTIDADE", "Só há " + item.getQuantidade().stripTrailingZeros().toPlainString()
                        + " de " + item.getNomeItemSnapshot() + " na comanda.");
            }
            BigDecimal parte = item.getSubtotal().multiply(pedido.quantidade())
                    .divide(item.getQuantidade(), 2, RoundingMode.HALF_UP);
            String nome = item.getNomeItemSnapshot();
            if (comparacao == 0) {
                vendaItemService.removerItemRascunho(operador, vendaId, item.getId());
            } else {
                boolean temAdicionais = item.isComposicaoMultipla()
                        || (modificadorRepository != null && !modificadorRepository.listarPorItem(item.getId()).isEmpty());
                if (temAdicionais) {
                    throw new ErroNegocioException("RETIRADA_ITEM_INTEIRO",
                            nome + " tem adicionais: tire o item inteiro.");
                }
                vendaItemService.editarItemRascunho(operador, vendaId, item.getId(), item.getVariacaoItemCardapio().getId(),
                        item.getQuantidade().subtract(pedido.quantidade()), item.getObservacao(), null, List.of(), null, List.of());
            }
            valor = valor.add(parte);
            retirados++;
            if (descricao.length() > 0) descricao.append(", ");
            descricao.append(pedido.quantidade().stripTrailingZeros().toPlainString()).append(" x ").append(nome);
        }

        if (cozinha != null) cozinha.cancelarExcedente(operador, vendaId);
        boolean vazia = itemRepository.listarAtivosPorVenda(vendaId).isEmpty();
        if (vazia) {
            comanda.cancelar(LocalDateTime.now(clock));
            comandaRepository.salvar(comanda);
        }
        String quem = responsavel == operador
                ? nomeDe(operador)
                : nomeDe(operador) + ", autorizado por " + nomeDe(responsavel);
        logRepository.salvar(new LogAcao(0, responsavel == null ? null : responsavel.getRu(), "COMANDA_ITEM_RETIRADO",
                "Comanda " + numero + ": tirou " + descricao + " (R$ " + valor.setScale(2, RoundingMode.HALF_UP).toPlainString()
                        + "). Motivo: " + motivoLimpo + ". Por " + quem + (vazia ? ". Comanda fechada vazia." : ".")));
        return new Retirada(numero, retirados, valor.setScale(2, RoundingMode.HALF_UP), vazia);
    }

    /** Quem tira o item: a própria operadora, se cuida do financeiro, ou o gerente que autorizou. */
    static Usuario responsavelRetirada(Usuario operador, Usuario autorizador) {
        if (podeTirarItem(operador)) {
            return operador;
        }
        if (autorizador != null && autorizador.isAtivo() && podeTirarItem(autorizador)) {
            return autorizador;
        }
        throw new SecurityException(autorizador == null
                ? "Tirar item da comanda precisa do login do gerente."
                : "Quem autorizou não pode tirar itens da comanda.");
    }

    public static boolean podeTirarItem(Usuario usuario) {
        return usuario != null && usuario.getClasse() != null
                && usuario.getClasse().possuiPermissao(Permissao.VER_FINANCAS);
    }

    private static String nomeDe(Usuario usuario) {
        return usuario == null ? "sistema" : usuario.getNomeCompleto();
    }

    /** A venda é de uma comanda que ainda não foi paga? */
    @Transactional(readOnly = true)
    public boolean vendaDeComandaAberta(long vendaId) {
        return comandaRepository.buscarPorVendaId(vendaId).filter(Comanda::isAberta).isPresent();
    }

    /** Fica no log quem encerrou o caixa deixando comandas sem pagar. */
    @Transactional
    public void registrarEncerramentoComAbertas(Usuario usuario, int numeroCaixa, List<Resumo> abertas) {
        if (abertas == null || abertas.isEmpty()) return;
        BigDecimal total = abertas.stream().map(Resumo::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        logRepository.salvar(new LogAcao(0, usuario == null ? null : usuario.getRu(), "CAIXA_ENCERRADO_COM_COMANDAS",
                "Caixa " + numeroCaixa + " encerrado com " + abertas.size() + " comanda(s) aberta(s): "
                        + abertas.stream().map(r -> String.valueOf(r.numero())).collect(java.util.stream.Collectors.joining(", "))
                        + " (R$ " + total.setScale(2, RoundingMode.HALF_UP).toPlainString() + ")."));
    }

    /** Texto do aviso de comandas abertas, usado ao encerrar o caixa. */
    public static String avisoComandasAbertas(List<Resumo> abertas) {
        BigDecimal total = abertas.stream().map(Resumo::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        String numeros = abertas.stream().map(r -> String.valueOf(r.numero())).collect(java.util.stream.Collectors.joining(", "));
        return (abertas.size() == 1 ? "A comanda " + numeros + " está aberta" : "As comandas " + numeros + " estão abertas")
                + ", somando R$ " + total.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',')
                + ". Receba antes de encerrar, ou confirme que elas ficam abertas.";
    }

    private Venda travarRascunho(long vendaId) {
        Venda venda = vendaRepository.buscarPorIdComLock(vendaId)
                .orElseThrow(() -> new ErroNegocioException("VENDA_NAO_ENCONTRADA", "Venda nao encontrada."));
        try {
            venda.exigirRascunho();
        } catch (IllegalStateException e) {
            throw new ErroNegocioException("VENDA_NAO_ESTA_EM_RASCUNHO", "Esta conta já foi paga ou cancelada.");
        }
        return venda;
    }

    public Optional<Venda> vendaDa(Comanda comanda) {
        return vendaRepository.buscarPorId(comanda.getVendaId());
    }
}
