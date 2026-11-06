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

/**
 * Mantém um único saldo de estoque.
 *
 * <p>O saldo oficial é o decimal ({@code estoques_produto}, filial padrão).
 * A coluna legada {@code produtos.quantidade_atual} (inteira) passa a ser só
 * um espelho do saldo decimal, para as telas e relatórios antigos continuarem
 * mostrando o número certo.
 *
 * <p>Regras:
 * <ul>
 *   <li>Movimentos das telas antigas (cadastro com quantidade inicial,
 *       entrada/saída manual, confirmação de nota) são aplicados primeiro no
 *       saldo decimal, com registro em {@code movimentos_estoque_decimal}.</li>
 *   <li>Depois de qualquer alteração do saldo decimal (venda, compra,
 *       estorno), o espelho legado é atualizado com {@link #espelharNoLegado}.</li>
 * </ul>
 */
public class SincronizadorEstoque {

    public static final Long FILIAL_PADRAO_ID = ProdutoEstoqueService.FILIAL_PADRAO_ID;

    private static final int ESCALA_DINHEIRO = 2;

    private final ProdutoRepository produtoRepository;
    private final EstoqueProdutoRepository estoqueProdutoRepository;
    private final MovimentoEstoqueRepository movimentoEstoqueRepository;

    public SincronizadorEstoque(ProdutoRepository produtoRepository,
                                EstoqueProdutoRepository estoqueProdutoRepository,
                                MovimentoEstoqueRepository movimentoEstoqueRepository) {
        this.produtoRepository = produtoRepository;
        this.estoqueProdutoRepository = estoqueProdutoRepository;
        this.movimentoEstoqueRepository = movimentoEstoqueRepository;
    }

    /** Resultado de um movimento aplicado no saldo oficial. */
    public record ResultadoMovimento(EstoqueProduto estoque, MovimentoEstoque movimento, Produto produtoEspelhado) {}

    /**
     * Saldo disponível (oficial) do produto na filial padrão. Produtos ainda
     * sem linha decimal usam o saldo legado (que foi copiado pela migração).
     */
    public BigDecimal saldoDisponivel(Produto produto) {
        return estoqueProdutoRepository.buscarPorFilialEProduto(FILIAL_PADRAO_ID, produto.getId())
                .map(EstoqueProduto::quantidadeDisponivel)
                .orElse(BigDecimal.valueOf(produto.getQuantidadeAtual()));
    }

    /**
     * Aplica um movimento vindo das telas antigas no saldo oficial e espelha
     * o resultado na coluna legada.
     *
     * @param custoUnitarioEntrada custo da entrada (nota fiscal); se nulo, usa
     *                             o custo médio atual
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
        if (quantidade == null || quantidade.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser maior que zero.");
        }

        EstoqueProduto estoque = obterComLockOuCriar(produto);
        BigDecimal saldoAnterior = estoque.getQuantidadeAtual();
        BigDecimal custoMedioAnterior = estoque.getCustoMedio();
        BigDecimal valorAnterior = estoque.valorEstoque();
        BigDecimal custoUnitario;

        if (tipo.ehEntrada()) {
            custoUnitario = custoUnitarioEntrada != null && custoUnitarioEntrada.compareTo(BigDecimal.ZERO) >= 0
                    ? custoUnitarioEntrada
                    : custoMedioAnterior;
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
     * Define o custo médio de um produto ainda sem saldo, para o PDV poder
     * vendê-lo quando o estoque entrar (mesmo por entrada manual, sem custo).
     */
    @Transactional
    public void definirCustoInicial(Produto produto, BigDecimal custoUnitario) {
        if (custoUnitario == null || custoUnitario.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Custo unitario deve ser maior que zero.");
        }
        EstoqueProduto estoque = obterComLockOuCriar(produto);
        estoque.redefinirQuantidadeECusto(estoque.getQuantidadeAtual(), custoUnitario);
        estoqueProdutoRepository.salvar(estoque);
    }

    /**
     * Copia o saldo decimal (arredondado para baixo, nunca negativo) para a
     * coluna legada {@code produtos.quantidade_atual}. Só a filial padrão é
     * espelhada, porque a coluna legada não conhece filiais.
     */
    @Transactional
    public Produto espelharNoLegado(EstoqueProduto estoque) {
        if (estoque == null || !FILIAL_PADRAO_ID.equals(estoque.getFilialId())) {
            return estoque == null ? null : estoque.getProduto();
        }
        Produto produto = estoque.getProduto();
        int espelho = quantidadeLegada(estoque.getQuantidadeAtual());
        if (produto.getQuantidadeAtual() == espelho) {
            return produto;
        }
        produto.atualizarQuantidade(espelho);
        return produtoRepository.salvar(produto);
    }

    static int quantidadeLegada(BigDecimal quantidadeDecimal) {
        if (quantidadeDecimal == null || quantidadeDecimal.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        BigDecimal inteiro = quantidadeDecimal.setScale(0, RoundingMode.FLOOR);
        if (inteiro.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
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
