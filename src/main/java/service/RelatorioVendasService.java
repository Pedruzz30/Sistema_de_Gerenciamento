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
    private final repository.VendaItemConsumoRepository consumoRepository;

    public RelatorioVendasService(CaixaRepository caixaRepository,
                                  VendaRepository vendaRepository,
                                  VendaItemRepository itemRepository,
                                  ComandaRepository comandaRepository,
                                  repository.VendaItemConsumoRepository consumoRepository) {
        this.caixaRepository = caixaRepository;
        this.vendaRepository = vendaRepository;
        this.itemRepository = itemRepository;
        this.comandaRepository = comandaRepository;
        this.consumoRepository = consumoRepository;
    }

    // ── Curva ABC ────────────────────────────────────────────────────────────

    /** Um produto do cardápio no período: gelada, natural e fardo somados. */
    public record VendidoNoPeriodo(String nome, BigDecimal valor, BigDecimal custo, BigDecimal consumoBase,
                                   Integer estoqueAtual) {}

    public record LinhaAbc(String nome, String classe, BigDecimal valor, BigDecimal margem, BigDecimal percentual,
                           BigDecimal acumulado, BigDecimal consumoBase, Integer estoqueAtual, BigDecimal coberturaDias) {}

    public record Abc(LocalDate inicio, LocalDate fim, String criterio, int dias, BigDecimal total,
                      Map<String, Integer> produtosPorClasse, Map<String, BigDecimal> percentualPorClasse,
                      List<LinhaAbc> linhas) {}

    @Transactional(readOnly = true)
    public Abc abc(LocalDate inicio, LocalDate fim, String criterio) {
        if (fim.isBefore(inicio)) throw new IllegalArgumentException("O fim do período vem antes do início.");
        if (java.time.temporal.ChronoUnit.DAYS.between(inicio, fim) > 366) {
            throw new IllegalArgumentException("Escolha um período de até um ano.");
        }
        Map<String, BigDecimal[]> somas = new LinkedHashMap<>();
        Map<String, Map<Integer, model.Produto>> produtos = new LinkedHashMap<>();
        for (Venda venda : vendaRepository.listarFinalizadasEntre(inicio.atStartOfDay(), fim.plusDays(1).atStartOfDay())) {
            List<VendaItem> itens = itemRepository.listarPorVenda(venda.getId()).stream()
                    .filter(i -> i.getStatus() == StatusVendaItem.ATIVO).toList();
            Map<Long, List<model.VendaItemConsumo>> consumosPorItem = new LinkedHashMap<>();
            if (consumoRepository != null && !itens.isEmpty()) {
                for (model.VendaItemConsumo c : consumoRepository.listarPorVendaItens(itens.stream().map(VendaItem::getId).toList())) {
                    consumosPorItem.computeIfAbsent(c.getVendaItem().getId(), k -> new ArrayList<>()).add(c);
                }
            }
            for (VendaItem item : itens) {
                String nome = item.getNomeItemSnapshot();
                BigDecimal[] s = somas.computeIfAbsent(nome, n -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
                s[0] = s[0].add(item.getSubtotal());
                s[1] = s[1].add(item.getCustoTotalEstimado() == null ? BigDecimal.ZERO : item.getCustoTotalEstimado());
                for (model.VendaItemConsumo c : consumosPorItem.getOrDefault(item.getId(), List.of())) {
                    s[2] = s[2].add(c.getQuantidadeBaseTotal());
                    produtos.computeIfAbsent(nome, n -> new LinkedHashMap<>()).put(c.getProduto().getId(), c.getProduto());
                }
            }
        }
        List<VendidoNoPeriodo> vendidos = new ArrayList<>();
        somas.forEach((nome, s) -> {
            // Cobertura só quando o item baixa um produto só (bebida); petisco com receita não tem.
            Map<Integer, model.Produto> doItem = produtos.getOrDefault(nome, Map.of());
            Integer estoque = doItem.size() == 1 ? doItem.values().iterator().next().getQuantidadeAtual() : null;
            vendidos.add(new VendidoNoPeriodo(nome, s[0], s[1], doItem.size() == 1 ? s[2] : null, estoque));
        });
        return classificarAbc(inicio, fim, criterio, vendidos);
    }

    /**
     * A: os que somam os primeiros 80% do critério; B: até 95%; C: o resto. O produto que
     * cruza a faixa fica na classe de cima, como é o costume da curva ABC.
     */
    public static Abc classificarAbc(LocalDate inicio, LocalDate fim, String criterio, List<VendidoNoPeriodo> vendidos) {
        boolean porMargem = "margem".equalsIgnoreCase(criterio);
        int dias = (int) java.time.temporal.ChronoUnit.DAYS.between(inicio, fim) + 1;
        List<VendidoNoPeriodo> ordenados = new ArrayList<>(vendidos);
        java.util.function.Function<VendidoNoPeriodo, BigDecimal> peso = v -> {
            BigDecimal base = porMargem ? v.valor().subtract(v.custo()) : v.valor();
            return base.max(BigDecimal.ZERO);
        };
        ordenados.sort(Comparator.comparing(peso).reversed().thenComparing(VendidoNoPeriodo::nome));
        BigDecimal total = ordenados.stream().map(peso).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cem = BigDecimal.valueOf(100);
        BigDecimal acumuladoAntes = BigDecimal.ZERO;
        List<LinhaAbc> linhas = new ArrayList<>();
        Map<String, Integer> porClasse = new LinkedHashMap<>(Map.of("A", 0, "B", 0, "C", 0));
        Map<String, BigDecimal> percentualPorClasse = new LinkedHashMap<>(Map.of("A", BigDecimal.ZERO, "B", BigDecimal.ZERO, "C", BigDecimal.ZERO));
        for (VendidoNoPeriodo v : ordenados) {
            BigDecimal valorPeso = peso.apply(v);
            BigDecimal percentual = total.signum() == 0 ? BigDecimal.ZERO : valorPeso.multiply(cem).divide(total, 4, RoundingMode.HALF_UP);
            String classe = valorPeso.signum() == 0 ? "C"
                    : acumuladoAntes.compareTo(BigDecimal.valueOf(80)) < 0 ? "A"
                    : acumuladoAntes.compareTo(BigDecimal.valueOf(95)) < 0 ? "B" : "C";
            BigDecimal acumulado = acumuladoAntes.add(percentual);
            acumuladoAntes = acumulado;
            BigDecimal cobertura = null;
            if (v.consumoBase() != null && v.estoqueAtual() != null && v.consumoBase().signum() > 0) {
                BigDecimal porDia = v.consumoBase().divide(BigDecimal.valueOf(dias), 6, RoundingMode.HALF_UP);
                cobertura = BigDecimal.valueOf(Math.max(0, v.estoqueAtual())).divide(porDia, 0, RoundingMode.DOWN);
            }
            linhas.add(new LinhaAbc(v.nome(), classe, dinheiro(v.valor()), dinheiro(v.valor().subtract(v.custo())),
                    percentual.setScale(1, RoundingMode.HALF_UP), acumulado.min(cem).setScale(1, RoundingMode.HALF_UP),
                    v.consumoBase() == null ? null : v.consumoBase().stripTrailingZeros(), v.estoqueAtual(), cobertura));
            porClasse.merge(classe, 1, Integer::sum);
            percentualPorClasse.merge(classe, percentual, BigDecimal::add);
        }
        percentualPorClasse.replaceAll((k, p) -> p.setScale(1, RoundingMode.HALF_UP));
        return new Abc(inicio, fim, porMargem ? "margem" : "valor", dias, dinheiro(total), porClasse, percentualPorClasse, linhas);
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
