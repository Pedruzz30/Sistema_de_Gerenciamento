package service;

import model.CotacaoMensal;
import model.Pedido;
import model.Produto;
import repository.CotacaoRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RentabilidadeService {

    private static final int ESCALA_MONETARIA = 2;
    private static final int ESCALA_PERCENTUAL = 4;
    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    private final EstoqueService estoqueService;
    private final CotacaoRepository cotacaoRepository;

    public RentabilidadeService(EstoqueService estoqueService, CotacaoRepository cotacaoRepository) {
        this.estoqueService = estoqueService;
        this.cotacaoRepository = cotacaoRepository;
    }

    public AnaliseRentabilidade gerarRanking() {
        List<Produto> produtos = estoqueService.listarProdutos();
        Map<Integer, BigDecimal> custos = custoMaisRecentePorProduto();
        Map<Integer, Integer> unidadesVendidas = unidadesVendidasPorProduto();

        List<ItemRentabilidade> itens = new ArrayList<>();
        for (Produto produto : produtos) {
            itens.add(criarItem(produto, custos.get(produto.getId()), unidadesVendidas.getOrDefault(produto.getId(), 0)));
        }

        itens.sort(this::compararItens);

        int posicao = 0;
        List<ItemRentabilidade> ranqueados = new ArrayList<>();
        for (ItemRentabilidade item : itens) {
            Integer posicaoItem = item.margemValida() ? ++posicao : null;
            ranqueados.add(item.comPosicao(posicaoItem));
        }

        List<ItemRentabilidade> validos = ranqueados.stream()
                .filter(ItemRentabilidade::margemValida)
                .toList();

        BigDecimal margemMedia = validos.isEmpty()
                ? null
                : validos.stream()
                .map(ItemRentabilidade::margemPercentual)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(validos.size()), ESCALA_PERCENTUAL, RoundingMode.HALF_UP);

        BigDecimal receitaTotal = validos.stream()
                .map(ItemRentabilidade::receitaEstimada)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);

        BigDecimal lucroTotal = validos.stream()
                .map(ItemRentabilidade::lucroTotalEstimado)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);

        int pendentesCusto = (int) ranqueados.stream()
                .filter(item -> "CUSTO_PENDENTE".equals(item.statusCodigo()))
                .count();

        int produtosSemVenda = (int) ranqueados.stream()
                .filter(item -> item.unidadesVendidas() == 0)
                .count();

        int unidadesVendidasTotal = ranqueados.stream()
                .mapToInt(ItemRentabilidade::unidadesVendidas)
                .sum();

        return new AnaliseRentabilidade(
                ranqueados,
                produtos.size(),
                validos.size(),
                pendentesCusto,
                produtosSemVenda,
                unidadesVendidasTotal,
                receitaTotal,
                lucroTotal,
                margemMedia,
                "ESTIMADO_PRECO_CUSTO_ATUAIS_SAIDAS_POR_VENDA",
                true
        );
    }

    private ItemRentabilidade criarItem(Produto produto, BigDecimal precoCusto, int unidadesVendidas) {
        BigDecimal precoVenda = normalizarMoeda(produto.getPrecoUnitario());
        boolean vendaValida = precoVenda.compareTo(BigDecimal.ZERO) > 0;
        boolean custoValido = precoCusto != null && precoCusto.compareTo(BigDecimal.ZERO) >= 0;
        boolean margemValida = vendaValida && custoValido;

        BigDecimal custoNormalizado = custoValido ? normalizarMoeda(precoCusto) : null;
        BigDecimal lucroUnitario = margemValida
                ? precoVenda.subtract(custoNormalizado).setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP)
                : null;
        BigDecimal margem = margemValida
                ? lucroUnitario.multiply(CEM).divide(precoVenda, ESCALA_PERCENTUAL, RoundingMode.HALF_UP)
                : null;
        BigDecimal receitaEstimada = margemValida
                ? precoVenda.multiply(BigDecimal.valueOf(unidadesVendidas)).setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        BigDecimal lucroTotalEstimado = margemValida
                ? lucroUnitario.multiply(BigDecimal.valueOf(unidadesVendidas)).setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        StatusRentabilidade status = classificar(vendaValida, custoValido, margem);

        return new ItemRentabilidade(
                null,
                produto.getId(),
                produto.getNome(),
                produto.getQuantidadeAtual(),
                produto.getQuantidadeMinima(),
                produto.getCategoriaEstoque() != null ? produto.getCategoriaEstoque().name() : null,
                precoVenda,
                custoNormalizado,
                lucroUnitario,
                margem,
                unidadesVendidas,
                receitaEstimada,
                lucroTotalEstimado,
                margemValida,
                status.codigo(),
                status.label()
        );
    }

    private int compararItens(ItemRentabilidade a, ItemRentabilidade b) {
        if (a.margemValida() && b.margemValida()) {
            int porLucroTotal = b.lucroTotalEstimado().compareTo(a.lucroTotalEstimado());
            if (porLucroTotal != 0) return porLucroTotal;

            int porMargem = b.margemPercentual().compareTo(a.margemPercentual());
            if (porMargem != 0) return porMargem;

            int porLucroUnitario = b.lucroUnitario().compareTo(a.lucroUnitario());
            if (porLucroUnitario != 0) return porLucroUnitario;

            return a.nome().compareToIgnoreCase(b.nome());
        }

        if (a.margemValida()) return -1;
        if (b.margemValida()) return 1;

        int porStatus = prioridadeStatus(a.statusCodigo()) - prioridadeStatus(b.statusCodigo());
        if (porStatus != 0) return porStatus;

        return a.nome().compareToIgnoreCase(b.nome());
    }

    private int prioridadeStatus(String statusCodigo) {
        return switch (statusCodigo) {
            case "CUSTO_PENDENTE" -> 1;
            case "PRECO_INVALIDO" -> 2;
            default -> 3;
        };
    }

    private StatusRentabilidade classificar(boolean vendaValida, boolean custoValido, BigDecimal margem) {
        if (!vendaValida) {
            return new StatusRentabilidade("PRECO_INVALIDO", "Preco invalido");
        }
        if (!custoValido) {
            return new StatusRentabilidade("CUSTO_PENDENTE", "Custo pendente");
        }
        if (margem.compareTo(BigDecimal.ZERO) < 0) {
            return new StatusRentabilidade("MARGEM_NEGATIVA", "Margem negativa");
        }
        if (margem.compareTo(BigDecimal.valueOf(50)) >= 0) {
            return new StatusRentabilidade("ALTA_MARGEM", "Alta margem");
        }
        if (margem.compareTo(BigDecimal.valueOf(25)) >= 0) {
            return new StatusRentabilidade("MARGEM_SAUDAVEL", "Margem saudavel");
        }
        return new StatusRentabilidade("BAIXA_MARGEM", "Baixa margem");
    }

    private Map<Integer, BigDecimal> custoMaisRecentePorProduto() {
        Map<Integer, BigDecimal> custos = new LinkedHashMap<>();
        cotacaoRepository.listarTodas().stream()
                .filter(cotacao -> cotacao.getProduto() != null)
                .sorted(Comparator
                        .comparing(CotacaoMensal::getMesReferencia).reversed()
                        .thenComparing(Comparator.comparingLong(CotacaoMensal::getId).reversed()))
                .forEach(cotacao -> custos.putIfAbsent(
                        cotacao.getProduto().getId(),
                        cotacao.getPrecoUnitario()
                ));
        return custos;
    }

    private Map<Integer, Integer> unidadesVendidasPorProduto() {
        Map<Integer, Integer> unidades = new LinkedHashMap<>();
        for (Pedido pedido : estoqueService.listarPedidos()) {
            if (!ehSaidaPorVenda(pedido)) {
                continue;
            }
            int produtoId = pedido.getProduto().getId();
            unidades.merge(produtoId, pedido.getQuantidade(), Integer::sum);
        }
        return unidades;
    }

    private boolean ehSaidaPorVenda(Pedido pedido) {
        if (pedido == null || !pedido.ehSaida() || pedido.getProduto() == null) {
            return false;
        }
        String descricao = pedido.getDescricao();
        return descricao != null && descricao.toLowerCase().contains("venda");
    }

    private BigDecimal normalizarMoeda(BigDecimal valor) {
        return valor.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }

    private record StatusRentabilidade(String codigo, String label) {}

    public record AnaliseRentabilidade(
            List<ItemRentabilidade> itens,
            int totalProdutos,
            int produtosAnalisados,
            int produtosPendentesCusto,
            int produtosSemVenda,
            int unidadesVendidasTotal,
            BigDecimal receitaTotalEstimada,
            BigDecimal lucroTotalEstimado,
            BigDecimal margemMediaPercentual,
            String modoCalculo,
            boolean estimado
    ) {}

    public record ItemRentabilidade(
            Integer posicao,
            int produtoId,
            String nome,
            int quantidadeAtual,
            int quantidadeMinima,
            String categoriaEstoque,
            BigDecimal precoVenda,
            BigDecimal precoCusto,
            BigDecimal lucroUnitario,
            BigDecimal margemPercentual,
            int unidadesVendidas,
            BigDecimal receitaEstimada,
            BigDecimal lucroTotalEstimado,
            boolean margemValida,
            String statusCodigo,
            String statusLabel
    ) {
        private ItemRentabilidade comPosicao(Integer novaPosicao) {
            return new ItemRentabilidade(
                    novaPosicao,
                    produtoId,
                    nome,
                    quantidadeAtual,
                    quantidadeMinima,
                    categoriaEstoque,
                    precoVenda,
                    precoCusto,
                    lucroUnitario,
                    margemPercentual,
                    unidadesVendidas,
                    receitaEstimada,
                    lucroTotalEstimado,
                    margemValida,
                    statusCodigo,
                    statusLabel
            );
        }
    }
}
