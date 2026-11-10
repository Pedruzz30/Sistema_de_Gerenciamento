package service;

import model.Caixa;
import model.FormaPagamento;
import model.MovimentacaoCaixa;
import model.StatusVendaItem;
import model.TipoMovimentacaoCaixa;
import model.Venda;
import model.VendaItem;
import org.springframework.transaction.annotation.Transactional;
import repository.CaixaRepository;
import repository.ComandaRepository;
import repository.VendaItemRepository;
import repository.VendaRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Relatório do dia e do mês para a dona. O dinheiro vem das movimentações do caixa (o que
 * entrou e saiu de fato, por forma); os produtos, das vendas finalizadas no período.
 */
public class RelatorioVendasService {

    private static final int ESCALA = 2;

    public record Resumo(int vendas, int cancelamentos, BigDecimal bruto, BigDecimal cancelado, BigDecimal liquido,
                         BigDecimal ticketMedio, BigDecimal descontos, BigDecimal custoEstimado,
                         BigDecimal margemEstimada, BigDecimal sangrias, BigDecimal suprimentos, long comandasPagas) {}

    public record Forma(String forma, String rotulo, BigDecimal valor) {}

    public record Hora(int hora, int vendas, BigDecimal valor) {}

    public record Produto(String nome, BigDecimal quantidade, BigDecimal valor, BigDecimal custo) {}

    public record Dia(LocalDate data, Resumo resumo, List<Forma> porForma,
                      List<ResumoOperadoresCaixa.Linha> porOperador, List<Hora> porHora, List<Produto> produtos) {}

    public record DiaDoMes(LocalDate data, int vendas, BigDecimal liquido) {}

    public record Mes(String mes, Resumo resumo, List<Forma> porForma, List<DiaDoMes> dias, List<Produto> produtos) {}

    /** O que o relatório precisa de cada item vendido; desconto já descontado do valor. */
    public record ItemVendido(String nome, BigDecimal quantidade, BigDecimal valor, BigDecimal desconto, BigDecimal custo) {}

    private final CaixaRepository caixaRepository;
    private final VendaRepository vendaRepository;
    private final VendaItemRepository itemRepository;
    private final ComandaRepository comandaRepository;

    public RelatorioVendasService(CaixaRepository caixaRepository,
                                  VendaRepository vendaRepository,
                                  VendaItemRepository itemRepository,
                                  ComandaRepository comandaRepository) {
        this.caixaRepository = caixaRepository;
        this.vendaRepository = vendaRepository;
        this.itemRepository = itemRepository;
        this.comandaRepository = comandaRepository;
    }

    @Transactional(readOnly = true)
    public Dia dia(LocalDate data) {
        LocalDateTime inicio = data.atStartOfDay();
        LocalDateTime fim = data.plusDays(1).atStartOfDay();
        return montarDia(data, movimentacoes(inicio, fim), itensVendidos(inicio, fim),
                comandaRepository == null ? 0 : comandaRepository.contarFechadasEntre(inicio, fim));
    }

    @Transactional(readOnly = true)
    public Mes mes(YearMonth mes) {
        LocalDateTime inicio = mes.atDay(1).atStartOfDay();
        LocalDateTime fim = mes.plusMonths(1).atDay(1).atStartOfDay();
        return montarMes(mes, movimentacoes(inicio, fim), itensVendidos(inicio, fim),
                comandaRepository == null ? 0 : comandaRepository.contarFechadasEntre(inicio, fim));
    }

    private List<MovimentacaoCaixa> movimentacoes(LocalDateTime inicio, LocalDateTime fim) {
        List<MovimentacaoCaixa> todas = new ArrayList<>();
        for (Caixa caixa : caixaRepository.listarAtivosNoPeriodo(inicio, fim)) {
            for (MovimentacaoCaixa m : caixa.getMovimentacoes()) {
                if (m.getDataHora() != null && !m.getDataHora().isBefore(inicio) && m.getDataHora().isBefore(fim)) {
                    todas.add(m);
                }
            }
        }
        return todas;
    }

    private List<ItemVendido> itensVendidos(LocalDateTime inicio, LocalDateTime fim) {
        List<ItemVendido> itens = new ArrayList<>();
        for (Venda venda : vendaRepository.listarFinalizadasEntre(inicio, fim)) {
            for (VendaItem item : itemRepository.listarPorVenda(venda.getId())) {
                if (item.getStatus() != StatusVendaItem.ATIVO) continue;
                String nome = item.getNomeItemSnapshot()
                        + (item.getNomeVariacaoSnapshot() == null || item.getNomeVariacaoSnapshot().isBlank() ? "" : " " + item.getNomeVariacaoSnapshot());
                itens.add(new ItemVendido(nome, item.getQuantidade(), item.getSubtotal(),
                        item.getDescontoUnitario().multiply(item.getQuantidade()),
                        item.getCustoTotalEstimado() == null ? BigDecimal.ZERO : item.getCustoTotalEstimado()));
            }
        }
        return itens;
    }

    // ── Cálculo (sem banco) ──────────────────────────────────────────────────

    public static Dia montarDia(LocalDate data, List<MovimentacaoCaixa> movs, List<ItemVendido> itens, long comandasPagas) {
        Map<Integer, Set<Long>> vendasPorHora = new TreeMap<>();
        Map<Integer, BigDecimal> valorPorHora = new TreeMap<>();
        for (MovimentacaoCaixa m : movs) {
            if (m.getTipo() != TipoMovimentacaoCaixa.VENDA) continue;
            int hora = m.getDataHora().getHour();
            vendasPorHora.computeIfAbsent(hora, h -> new HashSet<>()).add(chaveVenda(m));
            valorPorHora.merge(hora, m.getValor(), BigDecimal::add);
        }
        List<Hora> porHora = new ArrayList<>();
        vendasPorHora.forEach((hora, vendas) -> porHora.add(new Hora(hora, vendas.size(), dinheiro(valorPorHora.get(hora)))));
        return new Dia(data, resumir(movs, itens, comandasPagas), porForma(movs),
                ResumoOperadoresCaixa.calcular(movs, Map.of()), porHora, produtos(itens));
    }

    public static Mes montarMes(YearMonth mes, List<MovimentacaoCaixa> movs, List<ItemVendido> itens, long comandasPagas) {
        Map<LocalDate, Set<Long>> vendasPorDia = new TreeMap<>();
        Map<LocalDate, BigDecimal> liquidoPorDia = new TreeMap<>();
        for (MovimentacaoCaixa m : movs) {
            LocalDate dia = m.getDataHora().toLocalDate();
            if (m.getTipo() == TipoMovimentacaoCaixa.VENDA) {
                vendasPorDia.computeIfAbsent(dia, d -> new HashSet<>()).add(chaveVenda(m));
                liquidoPorDia.merge(dia, m.getValor(), BigDecimal::add);
            } else if (m.getTipo() == TipoMovimentacaoCaixa.ESTORNO_VENDA) {
                liquidoPorDia.merge(dia, m.getValor().negate(), BigDecimal::add);
            }
        }
        List<DiaDoMes> dias = new ArrayList<>();
        liquidoPorDia.forEach((dia, valor) -> dias.add(new DiaDoMes(dia,
                vendasPorDia.getOrDefault(dia, Set.of()).size(), dinheiro(valor))));
        return new Mes(mes.toString(), resumir(movs, itens, comandasPagas), porForma(movs), dias, produtos(itens));
    }

    private static Resumo resumir(List<MovimentacaoCaixa> movs, List<ItemVendido> itens, long comandasPagas) {
        Set<Long> vendas = new HashSet<>();
        Map<Long, BigDecimal> vendidoPorVenda = new LinkedHashMap<>();
        Map<Long, BigDecimal> estornadoPorVenda = new LinkedHashMap<>();
        BigDecimal bruto = BigDecimal.ZERO;
        BigDecimal cancelado = BigDecimal.ZERO;
        BigDecimal sangrias = BigDecimal.ZERO;
        BigDecimal suprimentos = BigDecimal.ZERO;
        for (MovimentacaoCaixa m : movs) {
            switch (m.getTipo()) {
                case VENDA -> {
                    vendas.add(chaveVenda(m));
                    bruto = bruto.add(m.getValor());
                    vendidoPorVenda.merge(chaveVenda(m), m.getValor(), BigDecimal::add);
                }
                case ESTORNO_VENDA -> {
                    cancelado = cancelado.add(m.getValor());
                    estornadoPorVenda.merge(chaveVenda(m), m.getValor(), BigDecimal::add);
                }
                case SANGRIA -> sangrias = sangrias.add(m.getValor());
                case SUPRIMENTO -> suprimentos = suprimentos.add(m.getValor());
                default -> { }
            }
        }
        // Venda cancelada inteira não entra no ticket médio.
        int canceladasInteiras = (int) estornadoPorVenda.entrySet().stream()
                .filter(e -> e.getValue().compareTo(vendidoPorVenda.getOrDefault(e.getKey(), BigDecimal.ZERO)) >= 0
                        && vendidoPorVenda.containsKey(e.getKey()))
                .count();
        BigDecimal liquido = bruto.subtract(cancelado);
        int validas = vendas.size() - canceladasInteiras;
        BigDecimal ticket = validas > 0 ? liquido.divide(BigDecimal.valueOf(validas), ESCALA, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal descontos = itens.stream().map(ItemVendido::desconto).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal custo = itens.stream().map(ItemVendido::custo).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal vendidoItens = itens.stream().map(ItemVendido::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Resumo(vendas.size(), estornadoPorVenda.size(), dinheiro(bruto), dinheiro(cancelado), dinheiro(liquido),
                dinheiro(ticket), dinheiro(descontos), dinheiro(custo), dinheiro(vendidoItens.subtract(custo)),
                dinheiro(sangrias), dinheiro(suprimentos), comandasPagas);
    }

    private static List<Forma> porForma(List<MovimentacaoCaixa> movs) {
        Map<FormaPagamento, BigDecimal> valores = new EnumMap<>(FormaPagamento.class);
        for (MovimentacaoCaixa m : movs) {
            if (m.getFormaPagamento() == null) continue;
            if (m.getTipo() == TipoMovimentacaoCaixa.VENDA) {
                valores.merge(m.getFormaPagamento(), m.getValor(), BigDecimal::add);
            } else if (m.getTipo() == TipoMovimentacaoCaixa.ESTORNO_VENDA) {
                valores.merge(m.getFormaPagamento(), m.getValor().negate(), BigDecimal::add);
            }
        }
        List<Forma> formas = new ArrayList<>();
        valores.forEach((forma, valor) -> formas.add(new Forma(forma.name(), forma.getLabel(), dinheiro(valor))));
        formas.sort(Comparator.comparing(Forma::valor).reversed());
        return formas;
    }

    /** Mais vendidos primeiro, pelo valor. */
    private static List<Produto> produtos(List<ItemVendido> itens) {
        Map<String, BigDecimal[]> porNome = new LinkedHashMap<>();
        for (ItemVendido item : itens) {
            BigDecimal[] soma = porNome.computeIfAbsent(item.nome(), n -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            soma[0] = soma[0].add(item.quantidade());
            soma[1] = soma[1].add(item.valor());
            soma[2] = soma[2].add(item.custo());
        }
        List<Produto> produtos = new ArrayList<>();
        porNome.forEach((nome, s) -> produtos.add(new Produto(nome, s[0].stripTrailingZeros(), dinheiro(s[1]), dinheiro(s[2]))));
        produtos.sort(Comparator.comparing(Produto::valor).reversed().thenComparing(Produto::nome));
        return produtos;
    }

    private static Long chaveVenda(MovimentacaoCaixa m) {
        return m.getVendaId() != null ? m.getVendaId() : -m.getId() - 1;
    }

    private static BigDecimal dinheiro(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(ESCALA, RoundingMode.HALF_UP);
    }
}
