package service;

import model.Produto;
import repository.CotacaoRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        Map<Integer, BigDecimal> custos = cotacaoRepository.custosMaisRecentesPorProduto();
        Map<Integer, Integer> unidadesVendidas = estoqueService.quantidadesVendidasPorProduto();

        List<ItemRentabilidade> itens = new ArrayList<>();
        for (Produto produto : produtos) {
            int produtoId = produto.getId();
            int quantidadeVendida = Objects.requireNonNull(
                    unidadesVendidas.getOrDefault(produtoId, 0),
                    "Quantidade vendida nula para produto: " + produtoId);
            itens.add(criarItem(produto, custos.get(produtoId), quantidadeVendida));
        }

        itens.sort((a, b) -> compararItens(a, b));

        int posicao = 0;
        List<ItemRentabilidade> ranqueados = new ArrayList<>();
        for (ItemRentabilidade item : itens) {
            Integer posicaoItem = item.margemValida() ? ++posicao : null;
            ranqueados.add(item.comPosicao(posicaoItem));
        }

        List<ItemRentabilidade> validos = ranqueados.stream()
                .filter(item -> item.margemValida())
                .toList();

        BigDecimal margemMedia = validos.isEmpty()
                ? null
                : validos.stream()
                .map(item -> Objects.requireNonNull(item.margemPercentual(),
                        "Margem ausente para produto analisado: " + item.produtoId()))
                .reduce(BigDecimal.ZERO, (total, valor) -> total.add(valor))
                .divide(BigDecimal.valueOf(validos.size()), ESCALA_PERCENTUAL, RoundingMode.HALF_UP);

        BigDecimal receitaTotal = validos.stream()
                .map(item -> item.receitaEstimada())
                .reduce(BigDecimal.ZERO, (total, valor) -> total.add(valor))
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);

        BigDecimal lucroTotal = validos.stream()
                .map(item -> item.lucroTotalEstimado())
                .reduce(BigDecimal.ZERO, (total, valor) -> total.add(valor))
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);

        int pendentesCusto = (int) ranqueados.stream()
                .filter(item -> "CUSTO_PENDENTE".equals(item.statusCodigo()))
                .count();

        int produtosSemVenda = (int) ranqueados.stream()
                .filter(item -> item.unidadesVendidas() == 0)
                .count();

        // O contrato publico ainda utiliza int. Soma em long para evitar overflow silencioso.
        long totalUnidades = ranqueados.stream()
                .mapToLong(item -> item.unidadesVendidas())
                .sum();
        int unidadesVendidasTotal = Math.toIntExact(totalUnidades);

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
        BigDecimal custoNormalizado = null;
        if (precoCusto != null && custoValido) {
            custoNormalizado = normalizarMoeda(precoCusto);
        }
        boolean margemValida = vendaValida && custoValido;
        BigDecimal lucroUnitario = null;
        BigDecimal margem = null;
        BigDecimal receitaEstimada = BigDecimal.ZERO.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        BigDecimal lucroTotalEstimado = BigDecimal.ZERO.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);

        if (vendaValida && custoNormalizado != null) {
            lucroUnitario = precoVenda.subtract(custoNormalizado)
                    .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
            margem = lucroUnitario.multiply(CEM)
                    .divide(precoVenda, ESCALA_PERCENTUAL, RoundingMode.HALF_UP);
            receitaEstimada = precoVenda.multiply(BigDecimal.valueOf(unidadesVendidas))
                    .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
            lucroTotalEstimado = lucroUnitario.multiply(BigDecimal.valueOf(unidadesVendidas))
                    .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        }
        StatusRentabilidade status = classificar(vendaValida, custoValido, margem);
        var categoria = produto.getCategoriaEstoque();

        return new ItemRentabilidade(
                null,
                produto.getId(),
                produto.getNome(),
                produto.getQuantidadeAtual(),
                produto.getQuantidadeMinima(),
                categoria != null ? categoria.name() : null,
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

            int porMargem = Objects.requireNonNull(b.margemPercentual())
                    .compareTo(Objects.requireNonNull(a.margemPercentual()));
            if (porMargem != 0) return porMargem;

            int porLucroUnitario = Objects.requireNonNull(b.lucroUnitario())
                    .compareTo(Objects.requireNonNull(a.lucroUnitario()));
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
        BigDecimal margemCalculada = Objects.requireNonNull(margem, "Margem nao calculada para produto valido.");
        if (margemCalculada.signum() < 0) {
            return new StatusRentabilidade("MARGEM_NEGATIVA", "Margem negativa");
        }
        if (margemCalculada.compareTo(BigDecimal.valueOf(50)) >= 0) {
            return new StatusRentabilidade("ALTA_MARGEM", "Alta margem");
        }
        if (margemCalculada.compareTo(BigDecimal.valueOf(25)) >= 0) {
            return new StatusRentabilidade("MARGEM_SAUDAVEL", "Margem saudavel");
        }
        return new StatusRentabilidade("BAIXA_MARGEM", "Baixa margem");
    }

    private BigDecimal normalizarMoeda(BigDecimal valor) {
        return Objects.requireNonNull(valor, "Valor monetario obrigatorio.")
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
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
