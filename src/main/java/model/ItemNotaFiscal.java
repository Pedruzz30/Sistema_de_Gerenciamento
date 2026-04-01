package model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Representa uma linha de item dentro de uma {@link NotaFiscal}.
 *
 * <h2>Analogia</h2>
 * Pense em uma nota fiscal de papel: cada linha impressa é um {@code ItemNotaFiscal}.
 * <pre>
 *   Laranja   | 2 cx  | R$ 4,99/cx  | R$ 9,98
 *   Alface    | 5 cx  | R$ 2,50/cx  | R$ 12,50
 *   Morango   | 1 cx  | R$ 12,00/cx | R$ 12,00
 * </pre>
 *
 * <h2>Identidade</h2>
 * Dois itens são considerados a mesma linha quando representam o mesmo
 * {@link Produto} ao mesmo preço unitário registrado na nota. A quantidade é
 * <em>mutável</em> e não faz parte da identidade — somamos quantidades ao
 * agrupar linhas iguais via {@link #podeSerAgrupadoCom}.
 *
 * <p>Por isso, {@link #equals} e {@link #hashCode} são definidos apenas sobre
 * {@code produto} e {@code precoUnitarioNota}. Incluir {@code quantidade} em
 * {@code equals} violaria o contrato de {@link Object#equals} para objetos
 * mutáveis armazenados em {@code Set} ou {@code Map}.
 *
 * <h2>Convenções monetárias</h2>
 * Todos os valores são {@link BigDecimal} com escala 2 e {@link RoundingMode#HALF_UP}.
 * A normalização ocorre uma única vez, no momento da atribuição — não
 * repetidamente nos cálculos intermediários.
 */
public class ItemNotaFiscal {

    // ── Constantes ────────────────────────────────────────────────────────────

    private static final int ESCALA_MONETARIA = 2;

    // ── Campos imutáveis ──────────────────────────────────────────────────────

    private final Produto produto;

    // ── Campos mutáveis controlados ───────────────────────────────────────────

    private int        quantidade;
    private BigDecimal precoUnitarioNota;

    // ── Construtor ────────────────────────────────────────────────────────────

    /**
     * Cria um novo item de nota fiscal.
     *
     * @param produto            produto referenciado; não pode ser {@code null}
     * @param quantidade         unidades compradas; deve ser {@code > 0}
     * @param precoUnitarioNota  preço unitário na data da compra (pode diferir
     *                           do preço cadastrado no produto); não pode ser
     *                           {@code null} nem negativo
     * @throws IllegalArgumentException se qualquer argumento for inválido
     */
    public ItemNotaFiscal(Produto produto, int quantidade, BigDecimal precoUnitarioNota) {
        validar(produto != null,                                 "Produto é obrigatório.");
        validar(quantidade > 0,                                  "Quantidade deve ser maior que zero.");
        validar(precoUnitarioNota != null,                       "Preço unitário é obrigatório.");
        validar(precoUnitarioNota.compareTo(BigDecimal.ZERO) >= 0,
                "Preço unitário não pode ser negativo.");

        this.produto           = produto;
        this.quantidade        = quantidade;
        this.precoUnitarioNota = normalizar(precoUnitarioNota);
    }

    // ── Cálculo monetário ─────────────────────────────────────────────────────

    /**
     * Calcula o subtotal deste item (quantidade × preço unitário).
     *
     * <pre>  5 × R$ 4,99 = R$ 24,95  </pre>
     *
     * <p>O resultado sempre tem escala 2 e arredondamento {@code HALF_UP}.
     * Como {@code precoUnitarioNota} já é normalizado, a multiplicação pode
     * produzir até 4 casas decimais antes do {@code setScale} final.
     *
     * @return subtotal com precisão monetária; nunca {@code null}
     */
    public BigDecimal calcularSubtotal() {
        return precoUnitarioNota
                .multiply(BigDecimal.valueOf(quantidade))
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }

    // ── Operações de quantidade ───────────────────────────────────────────────

    /**
     * Soma unidades ao item existente (agrupamento de linhas iguais).
     *
     * @param quantidadeAdicional unidades a somar; deve ser {@code > 0}
     * @throws IllegalArgumentException se {@code quantidadeAdicional <= 0}
     */
    public void adicionarQuantidade(int quantidadeAdicional) {
        validar(quantidadeAdicional > 0,
                "Quantidade adicional deve ser maior que zero.");
        this.quantidade += quantidadeAdicional;
    }

    /**
     * Remove unidades do item.
     *
     * <p>A quantidade pode chegar a zero após a remoção — use
     * {@link #estaZerado()} para verificar se o item deve ser excluído da nota.
     *
     * @param quantidadeRemovida unidades a subtrair; deve ser {@code > 0}
     *                           e não exceder a quantidade atual
     * @throws IllegalArgumentException se {@code quantidadeRemovida} for inválida
     */
    public void removerQuantidade(int quantidadeRemovida) {
        validar(quantidadeRemovida > 0,
                "Quantidade removida deve ser maior que zero.");
        validar(quantidadeRemovida <= this.quantidade,
                String.format("Remoção impossível: item tem %d unidade(s), remoção solicitada: %d.",
                        this.quantidade, quantidadeRemovida));
        this.quantidade -= quantidadeRemovida;
    }

    /**
     * Substitui a quantidade total por um valor absoluto.
     *
     * <p><strong>Prefira {@link #adicionarQuantidade} e {@link #removerQuantidade}</strong>
     * para operações semânticas. Use este método apenas para ajustes de
     * inventário em que o valor correto é conhecido diretamente.
     *
     * <p>Aceita {@code 0} — isso marca o item como zerado sem removê-lo da nota.
     * Use {@link #estaZerado()} para decidir se o item deve ser descartado.
     *
     * @param novaQuantidade nova quantidade; deve ser {@code >= 0}
     * @throws IllegalArgumentException se {@code novaQuantidade < 0}
     */
    public void atualizarQuantidade(int novaQuantidade) {
        validar(novaQuantidade >= 0,
                "Quantidade não pode ser negativa.");
        this.quantidade = novaQuantidade;
    }

    /**
     * Atualiza o preço unitário registrado nesta nota.
     *
     * <p>O preço de uma nota pode ser corrigido enquanto ela está
     * {@code PENDENTE}, por exemplo ao receber a nota fiscal correta do
     * fornecedor após a entrega.
     *
     * @param novoPreco novo preço unitário; não pode ser {@code null} nem negativo
     * @throws IllegalArgumentException se {@code novoPreco} for inválido
     */
    public void atualizarPrecoUnitarioNota(BigDecimal novoPreco) {
        validar(novoPreco != null,                           "Preço unitário é obrigatório.");
        validar(novoPreco.compareTo(BigDecimal.ZERO) >= 0,  "Preço unitário não pode ser negativo.");
        this.precoUnitarioNota = normalizar(novoPreco);
    }

    // ── Regras de agrupamento e pertinência ───────────────────────────────────

    /**
     * Verifica se dois itens podem ser agrupados em uma única linha.
     *
     * <p>O agrupamento ocorre quando ambos referenciam o mesmo produto
     * <em>e</em> têm o mesmo preço unitário. Diferenças de quantidade não
     * impedem o agrupamento — as quantidades são somadas pelo chamador.
     *
     * <p>Usa {@link Produto#equals} para comparar produtos (baseado em ID)
     * e {@link BigDecimal#compareTo} para preços (ignora diferença de escala,
     * ex: {@code 4.90} e {@code 4.9} são iguais).
     *
     * @param outro item a comparar; retorna {@code false} se {@code null}
     * @return {@code true} se os itens representam a mesma linha de nota
     */
    public boolean podeSerAgrupadoCom(ItemNotaFiscal outro) {
        if (outro == null) return false;
        return produto.equals(outro.produto)
                && precoUnitarioNota.compareTo(outro.precoUnitarioNota) == 0;
    }

    /**
     * Verifica se este item pertence ao produto com o ID informado.
     *
     * @param produtoId ID do produto a verificar
     * @return {@code true} se o produto deste item tiver o ID fornecido
     */
    public boolean pertenceAoProduto(long produtoId) {
        return produto.getId() == produtoId;
    }

    /** @return {@code true} se a quantidade chegou a zero */
    public boolean estaZerado() {
        return quantidade == 0;
    }

    // ── Exibição ──────────────────────────────────────────────────────────────

    /**
     * Gera uma descrição formatada para exibição em UI ou logs.
     *
     * <pre>  Laranja | qtd: 5 | unit.: R$ 4,99 | subtotal: R$ 24,95  </pre>
     */
    public String gerarDescricao() {
        return String.format(
                "%s | qtd: %d | unit.: R$ %s | subtotal: R$ %s",
                produto.getNome(),
                quantidade,
                precoUnitarioNota.toPlainString(),
                calcularSubtotal().toPlainString()
        );
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Produto    getProduto()           { return produto;           }
    public int        getQuantidade()        { return quantidade;        }
    public BigDecimal getPrecoUnitarioNota() { return precoUnitarioNota; }

    // ── Infraestrutura de objeto ──────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Delega para {@link #gerarDescricao()} — saída legível tanto para
     * logs quanto para o {@link NotaFiscal#toString()}.
     */
    @Override
    public String toString() {
        return gerarDescricao();
    }

    /**
     * Dois itens são iguais se referenciam o mesmo {@link Produto} ao mesmo
     * preço unitário registrado na nota.
     *
     * <p>{@code quantidade} é <em>intencionalmente excluída</em> da comparação:
     * ela é mutável, e inclui-la violaria o contrato de {@code equals} para
     * objetos armazenados em {@code Set} ou como chave de {@code Map} — a
     * identidade do item mudaria toda vez que a quantidade fosse alterada.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemNotaFiscal other)) return false;
        return Objects.equals(produto, other.produto)
                && precoUnitarioNota.compareTo(other.precoUnitarioNota) == 0;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Consistente com {@link #equals}: baseado apenas em {@code produto}
     * e {@code precoUnitarioNota} (normalizado para escala 2 antes do hash,
     * garantindo que {@code 4.9} e {@code 4.90} produzam o mesmo hash).
     */
    @Override
    public int hashCode() {
        // stripTrailingZeros garante que 4.90 e 4.9 tenham o mesmo hashCode,
        // complementando o compareTo usado no equals.
        return Objects.hash(produto, precoUnitarioNota.stripTrailingZeros());
    }

    // ── Auxiliares privados ───────────────────────────────────────────────────

    /**
     * Normaliza um valor monetário para escala 2 com arredondamento {@code HALF_UP}.
     * Executado uma única vez na atribuição — não repetido nos cálculos.
     */
    private static BigDecimal normalizar(BigDecimal valor) {
        return valor.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }

    /**
     * Lança {@link IllegalArgumentException} se a condição for falsa.
     * Padrão consistente com {@link Produto} e {@link NotaFiscal}.
     */
    private static void validar(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalArgumentException(mensagem);
    }
}
