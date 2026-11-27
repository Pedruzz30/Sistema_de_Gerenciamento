package service;

import model.EstoqueProduto;
import model.MovimentoEstoque;
import model.Produto;
import model.TipoMovimentoEstoque;
import org.springframework.transaction.annotation.Transactional;
import repository.EstoqueProdutoRepository;
import repository.MovimentoEstoqueRepository;
import repository.ProdutoRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Mantém um único saldo de estoque.
 *
 * <p>O saldo oficial é o decimal ({@code estoques_produto}, filial padrão).
 * A coluna legada {@code produtos.quantidade_atual} (inteira) é um espelho
 * do saldo decimal para as telas e relatórios antigos.
 */
public class SincronizadorEstoque {

    public static final Long FILIAL_PADRAO_ID = ProdutoEstoqueService.FILIAL_PADRAO_ID;

    private static final int ESCALA_DINHEIRO = 2;
    private static final BigDecimal LIMITE_QUANTIDADE_LEGADA = BigDecimal.valueOf(Integer.MAX_VALUE);

    private final ProdutoRepository produtoRepository;
    private final EstoqueProdutoRepository estoqueProdutoRepository;
    private final MovimentoEstoqueRepository movimentoEstoqueRepository;

    public SincronizadorEstoque(ProdutoRepository produtoRepository,
                                EstoqueProdutoRepository estoqueProdutoRepository,
                                MovimentoEstoqueRepository movimentoEstoqueRepository) {
        this.produtoRepository = Objects.requireNonNull(produtoRepository, "produtoRepository");
        this.estoqueProdutoRepository = Objects.requireNonNull(estoqueProdutoRepository, "estoqueProdutoRepository");
        this.movimentoEstoqueRepository = Objects.requireNonNull(movimentoEstoqueRepository, "movimentoEstoqueRepository");
    }

    /** Resultado de um movimento aplicado no saldo oficial. */
    public record ResultadoMovimento(EstoqueProduto estoque, MovimentoEstoque movimento, Produto produtoEspelhado) {}

    /**
     * Saldo disponível oficial na filial padrão. O saldo legado é usado somente
     * quando ainda não existe uma linha decimal.
     */
    public BigDecimal saldoDisponivel(Produto produto) {
        if (produto == null) {
            throw new IllegalArgumentException("Produto e obrigatorio.");
        }
        return estoqueProdutoRepository.buscarPorFilialEProduto(FILIAL_PADRAO_ID, produto.getId())
                .map(estoque -> Objects.requireNonNull(estoque.quantidadeDisponivel(),
                        "Saldo disponivel decimal ausente."))
                .orElseGet(() -> BigDecimal.valueOf(produto.getQuantidadeAtual()));
    }

    /**
     * Aplica um movimento no saldo oficial e espelha o resultado na coluna legada.
     *
     * @param custoUnitarioEntrada custo da entrada; se nulo, usa o custo médio atual
     */
    @Transactional
    public ResultadoMovimento aplicarMovimento(Produto produto,
                                               TipoMovimentoEstoque tipo,
                                               BigDecimal quantidade,
                                               BigDecimal custoUnitarioEntrada,
                                               String origemTipo,
                                               long origemId,
                                               Long usuarioId,
                                               String motivo) {
        if (produto == null) {
            throw new IllegalArgumentException("Produto e obrigatorio.");
        }
        if (tipo == null || tipo.getSinalPadrao() == 0) {
            throw new IllegalArgumentException("Tipo de movimento invalido para sincronizacao.");
        }
        if (quantidade == null || quantidade.signum() <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser maior que zero.");
        }
        if (tipo.ehEntrada() && custoUnitarioEntrada != null && custoUnitarioEntrada.signum() < 0) {
            throw new IllegalArgumentException("Custo unitario da entrada nao pode ser negativo.");
        }

        EstoqueProduto estoque = obterComLockOuCriar(produto);
        BigDecimal saldoAnterior = estoque.getQuantidadeAtual();
        BigDecimal custoMedioAnterior = estoque.getCustoMedio();
        BigDecimal valorAnterior = estoque.valorEstoque();
        BigDecimal custoUnitario;

        if (tipo.ehEntrada()) {
            custoUnitario = custoUnitarioEntrada == null ? custoMedioAnterior : custoUnitarioEntrada;
            estoque.aplicarEntrada(quantidade, custoUnitario);
        } else {
            if (estoque.quantidadeDisponivel().compareTo(quantidade) < 0) {
                throw new IllegalArgumentException("Quantidade insuficiente em estoque para saida.");
            }
            custoUnitario = custoMedioAnterior;
            estoque.aplicarSaida(quantidade, custoUnitario, true);
        }

        estoque = estoqueProdutoRepository.salvar(estoque);
        MovimentoEstoque movimento = movimentoEstoqueRepository.salvar(new MovimentoEstoque(
                0,
                FILIAL_PADRAO_ID,
                estoque.getProduto(),
                tipo,
                quantidade,
                saldoAnterior,
                estoque.getQuantidadeAtual(),
                custoUnitario,
                quantidade.multiply(custoUnitario).setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP),
                custoMedioAnterior,
                estoque.getCustoMedio(),
                valorAnterior,
                estoque.valorEstoque(),
                origemTipo,
                origemId,
                usuarioId,
                motivo == null || motivo.isBlank() ? tipo.name() : limitar(motivo, 240),
                null,
                null,
                null
        ));
        Produto espelhado = espelharNoLegado(estoque);
        return new ResultadoMovimento(estoque, movimento, espelhado);
    }

    /**
     * Define o custo médio inicial. A restrição de saldo ainda não é imposta
     * aqui: depende dos fluxos de cadastro e migração existentes.
     */
    @Transactional
    public void definirCustoInicial(Produto produto, BigDecimal custoUnitario) {
        if (custoUnitario == null || custoUnitario.signum() <= 0) {
            throw new IllegalArgumentException("Custo unitario deve ser maior que zero.");
        }
        if (produto == null) {
            throw new IllegalArgumentException("Produto e obrigatorio.");
        }
        EstoqueProduto estoque = obterComLockOuCriar(produto);
        estoque.redefinirQuantidadeECusto(estoque.getQuantidadeAtual(), custoUnitario);
        estoqueProdutoRepository.salvar(estoque);
    }

    /**
     * Copia o saldo decimal (arredondado para baixo, nunca negativo) para a
     * coluna legada. Só a filial padrão é espelhada.
     */
    @Transactional
    public Produto espelharNoLegado(EstoqueProduto estoque) {
        if (estoque == null) {
            return null;
        }
        Produto produto = estoque.getProduto();
        if (!FILIAL_PADRAO_ID.equals(estoque.getFilialId())) {
            return produto;
        }
        int espelho = quantidadeLegada(estoque.getQuantidadeAtual());
        if (produto.getQuantidadeAtual() == espelho) {
            return produto;
        }
        produto.atualizarQuantidade(espelho);
        return produtoRepository.salvar(produto);
    }

    static int quantidadeLegada(BigDecimal quantidadeDecimal) {
        if (quantidadeDecimal == null || quantidadeDecimal.signum() <= 0) {
            return 0;
        }
        BigDecimal inteiro = quantidadeDecimal.setScale(0, RoundingMode.FLOOR);
        if (inteiro.compareTo(LIMITE_QUANTIDADE_LEGADA) > 0) {
            return Integer.MAX_VALUE;
        }
        return inteiro.intValueExact();
    }

    private EstoqueProduto obterComLockOuCriar(Produto produto) {
        return estoqueProdutoRepository.buscarPorFilialEProdutoComLock(FILIAL_PADRAO_ID, produto.getId())
                .orElseGet(() -> estoqueProdutoRepository.salvar(new EstoqueProduto(
                        0,
                        FILIAL_PADRAO_ID,
                        produto,
                        BigDecimal.valueOf(Math.max(0, produto.getQuantidadeAtual())),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                )));
    }

    private static String limitar(String texto, int max) {
        String limpo = texto.trim();
        return limpo.length() <= max ? limpo : limpo.substring(0, max);
    }
}
