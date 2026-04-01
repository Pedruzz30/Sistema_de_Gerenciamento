package model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Representa um produto gerenciado no sistema de estoque.
 *
 * <h2>Responsabilidades deste modelo</h2>
 * <ul>
 *   <li>Guardar o estado atual do produto (nome, quantidades, preço, categoria).</li>
 *   <li>Expor operações de negócio com validação (adicionar/remover estoque).</li>
 *   <li>Calcular métricas de nível de estoque ({@link #calcularNivel()}).</li>
 * </ul>
 *
 * <h2>Convenções monetárias</h2>
 * O preço é armazenado como {@link BigDecimal} com escala 2 e arredondamento
 * {@link RoundingMode#HALF_UP}. Nunca use {@code double} para valores monetários —
 * ponto flutuante acumula erro de representação (0.1 + 0.2 ≠ 0.3 em IEEE 754).
 *
 * <h2>Padrão de ID</h2>
 * {@code id == 0} indica que o produto ainda não foi persistido pelo repositório.
 * IDs negativos são rejeitados no construtor.
 *
 * <h2>Padrão de cópia com ID</h2>
 * Use {@link #comId(int)} quando o repositório precisar retornar uma cópia
 * do produto com o ID atribuído após a persistência, sem expor um setter de ID.
 */
public class Produto {

    // ── Constantes ────────────────────────────────────────────────────────────

    private static final int ESCALA_MONETARIA = 2;

    // ── Campos imutáveis ──────────────────────────────────────────────────────

    private final int              id;
    private final String           nome;
    private final int              quantidadeMinima;
    private final BigDecimal       precoUnitario;
    private final CategoriaEstoque categoriaEstoque; // opcional — pode ser null

    // ── Campo mutável controlado ──────────────────────────────────────────────

    private int quantidadeAtual;

    // ── Construtores ──────────────────────────────────────────────────────────

    /**
     * Cria um produto sem categoria de estoque.
     *
     * @see #Produto(int, String, int, int, BigDecimal, CategoriaEstoque)
     */
    public Produto(int id, String nome, int quantidadeAtual, int quantidadeMinima, BigDecimal precoUnitario) {
        this(id, nome, quantidadeAtual, quantidadeMinima, precoUnitario, null);
    }

    /**
     * Construtor principal.
     *
     * @param id               identificador único; {@code >= 0} (0 = ainda não persistido)
     * @param nome             nome do produto; não pode ser nulo ou vazio
     * @param quantidadeAtual  estoque disponível no momento; {@code >= 0}
     * @param quantidadeMinima nível mínimo aceitável de estoque; {@code >= 0}
     * @param precoUnitario    preço por unidade; {@code >= 0}; não pode ser {@code null}
     * @param categoriaEstoque categoria de estoque; pode ser {@code null}
     * @throws IllegalArgumentException se qualquer argumento obrigatório for inválido
     */
    public Produto(int id, String nome, int quantidadeAtual, int quantidadeMinima,
                   BigDecimal precoUnitario, CategoriaEstoque categoriaEstoque) {

        validar(id >= 0,                            "ID do produto não pode ser negativo.");
        validar(nome != null && !nome.isBlank(),    "Nome do produto é obrigatório.");
        validar(quantidadeAtual >= 0,               "Quantidade atual não pode ser negativa.");
        validar(quantidadeMinima >= 0,              "Quantidade mínima não pode ser negativa.");
        validar(precoUnitario != null,              "Preço unitário é obrigatório.");
        validar(precoUnitario.compareTo(BigDecimal.ZERO) >= 0,
                "Preço unitário não pode ser negativo.");

        this.id               = id;
        this.nome             = nome.trim();
        this.quantidadeAtual  = quantidadeAtual;
        this.quantidadeMinima = quantidadeMinima;
        this.precoUnitario    = precoUnitario.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        this.categoriaEstoque = categoriaEstoque;
    }

    // ── Fábrica para repositório ──────────────────────────────────────────────

    /**
     * Retorna uma cópia deste produto com o ID substituído.
     *
     * <p>Usado pelo repositório ao persistir um produto com {@code id == 0}:
     * em vez de expor um {@code setId()}, o repositório chama este método
     * e armazena a cópia com o ID gerado.
     *
     * <pre>
     *   Produto salvo = produto.comId(proximoId++);
     *   armazenamento.put(salvo.getId(), salvo);
     *   return salvo;
     * </pre>
     *
     * @param novoId novo identificador; deve ser {@code > 0}
     * @return novo objeto {@code Produto} idêntico, com o novo ID
     * @throws IllegalArgumentException se {@code novoId <= 0}
     */
    public Produto comId(int novoId) {
        validar(novoId > 0, "Novo ID deve ser maior que zero.");
        return new Produto(novoId, nome, quantidadeAtual, quantidadeMinima, precoUnitario, categoriaEstoque);
    }

    // ── Operações de estoque ──────────────────────────────────────────────────

    /**
     * Incrementa o estoque em {@code quantidade} unidades (entrada de mercadoria).
     *
     * @param quantidade número de unidades a adicionar; deve ser {@code > 0}
     * @throws IllegalArgumentException se {@code quantidade <= 0}
     */
    public void adicionarEstoque(int quantidade) {
        validar(quantidade > 0, "A quantidade adicionada deve ser maior que zero.");
        this.quantidadeAtual += quantidade;
    }

    /**
     * Decrementa o estoque em {@code quantidade} unidades (saída de mercadoria).
     *
     * @param quantidade número de unidades a remover; deve ser {@code > 0}
     *                   e não exceder o estoque atual
     * @throws IllegalArgumentException se {@code quantidade <= 0} ou maior que o estoque atual
     */
    public void removerEstoque(int quantidade) {
        validar(quantidade > 0,
                "A quantidade removida deve ser maior que zero.");
        validar(quantidade <= this.quantidadeAtual,
                String.format("Estoque insuficiente. Disponível: %d, solicitado: %d.",
                        this.quantidadeAtual, quantidade));
        this.quantidadeAtual -= quantidade;
    }

    /**
     * Substitui o estoque atual por um valor absoluto.
     *
     * <p><strong>Prefira {@link #adicionarEstoque} e {@link #removerEstoque}</strong>
     * para operações semânticas. Use este método apenas quando o valor absoluto
     * correto for conhecido (ex.: ajuste de inventário físico).
     *
     * @param novaQuantidade nova quantidade em estoque; {@code >= 0}
     * @throws IllegalArgumentException se {@code novaQuantidade < 0}
     */
    public void atualizarQuantidade(int novaQuantidade) {
        validar(novaQuantidade >= 0, "Quantidade em estoque não pode ser negativa.");
        this.quantidadeAtual = novaQuantidade;
    }

    // ── Métricas e diagnóstico de estoque ─────────────────────────────────────

    /**
     * Retorna o nível atual de estoque classificado pelo {@link NivelEstoque}.
     *
     * <p>Evita a duplicação da lógica de classificação que antes existia em
     * {@code getStatusEstoque()} (String) e em {@code EstoqueService.calcularNivel()}.
     * Agora há uma única fonte de verdade: o próprio {@link NivelEstoque#fromPercentage}.
     *
     * @return nível de estoque calculado a partir do percentual atual
     */
    public NivelEstoque calcularNivel() {
        return NivelEstoque.fromPercentage(calcularPercentualEstoque());
    }

    /**
     * Calcula o percentual do estoque atual em relação ao mínimo configurado.
     *
     * <p>Quando {@code quantidadeMinima == 0} (produto sem controle de mínimo):
     * <ul>
     *   <li>Retorna {@code 100.0} se houver qualquer quantidade em estoque.</li>
     *   <li>Retorna {@code 0.0} se o estoque também for zero.</li>
     * </ul>
     *
     * @return percentual entre 0.0 e N (pode ultrapassar 100 se acima do mínimo)
     */
    public double calcularPercentualEstoque() {
        if (quantidadeMinima == 0) {
            return quantidadeAtual > 0 ? 100.0 : 0.0;
        }
        return (quantidadeAtual * 100.0) / quantidadeMinima;
    }

    /**
     * Calcula quantas unidades faltam para atingir o estoque mínimo.
     *
     * @return déficit em unidades; {@code 0} se o estoque já estiver no ou acima do mínimo
     */
    public int calcularDeficit() {
        return Math.max(0, quantidadeMinima - quantidadeAtual);
    }

    /** @return {@code true} se não há nenhuma unidade em estoque */
    public boolean estaSemEstoque() {
        return quantidadeAtual == 0;
    }

    /** @return {@code true} se o estoque está abaixo do mínimo configurado */
    public boolean estaAbaixoDoMinimo() {
        return quantidadeAtual < quantidadeMinima;
    }

    /** @return {@code true} se o estoque está exatamente no mínimo configurado */
    public boolean estaNoMinimo() {
        return quantidadeAtual == quantidadeMinima;
    }

    /** @return {@code true} se o estoque supera o mínimo configurado */
    public boolean estaAcimaDoMinimo() {
        return quantidadeAtual > quantidadeMinima;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public int              getId()               { return id;               }
    public String           getNome()             { return nome;             }
    public int              getQuantidadeAtual()  { return quantidadeAtual;  }
    public int              getQuantidadeMinima() { return quantidadeMinima; }
    public BigDecimal       getPrecoUnitario()    { return precoUnitario;    }
    public CategoriaEstoque getCategoriaEstoque() { return categoriaEstoque; }

    /** @return {@code true} se uma categoria de estoque foi definida */
    public boolean possuiCategoriaEstoque() {
        return categoriaEstoque != null;
    }

    // ── Infraestrutura de objeto ──────────────────────────────────────────────

    /**
     * Representação textual rica, útil para logs e debug.
     *
     * <p>Inclui o nível de estoque ({@link NivelEstoque}) para facilitar
     * o diagnóstico rápido sem precisar de chamadas adicionais.
     */
    @Override
    public String toString() {
        return String.format(
                "Produto{id=%d, nome='%s', qtd=%d/%d, preco=R$%s, nivel=%s, categoria=%s}",
                id,
                nome,
                quantidadeAtual,
                quantidadeMinima,
                precoUnitario.toPlainString(),
                calcularNivel(),
                categoriaEstoque != null ? categoriaEstoque : "N/A"
        );
    }

    /**
     * Dois produtos são iguais se tiverem o mesmo {@code id}.
     *
     * <p>Produtos com {@code id == 0} (não persistidos) nunca são considerados
     * iguais entre si, mesmo que todos os outros campos sejam idênticos.
     * Isso evita falsos positivos em coleções antes da persistência.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Produto other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    // ── Auxiliar privado ──────────────────────────────────────────────────────

    /**
     * Helper de validação centralizado.
     *
     * <p>Equivalente a {@link java.util.Objects#requireNonNull} mas para
     * qualquer condição booleana.
     *
     * @param condicao  deve ser {@code true} para que a validação passe
     * @param mensagem  mensagem da exceção lançada se {@code condicao == false}
     */
    private static void validar(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalArgumentException(mensagem);
    }
}
