package model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Representa um cargo/função dentro da empresa, com suas permissões associadas.
 *
 * <h2>Papel no domínio</h2>
 * {@code ClasseFuncionario} é o mecanismo de autorização do sistema.
 * Cada {@link Usuario} tem uma classe, e cada classe tem um conjunto de
 * {@link Permissao}. Os serviços verificam permissões via
 * {@link #possuiPermissao(Permissao)} — nunca pelo {@link Usuario.Perfil}.
 *
 * <h2>Exemplos de classes padrão</h2>
 * <pre>
 *   SUPERIOR         → todas as permissões
 *   GERENTE_ESTOQUE  → VER_ESTOQUE, EDITAR_ESTOQUE, VER_COMPRAS,
 *                      VER_FINANCAS, GERENCIAR_FUNCIONARIOS, VER_LOGS
 *   ESTOQUISTA       → VER_ESTOQUE, EDITAR_ESTOQUE
 *   CAIXA            → VER_VENDAS, VER_ESTOQUE
 * </pre>
 *
 * <h2>Construção via Builder</h2>
 * O padrão builder elimina a sequência de {@code adicionarPermissao()} repetida
 * em {@code AppConfig}, {@code FuncionarioController} e
 * {@code SistemaGerenciamentoEstoque}. Use {@link #builder}:
 * <pre>
 *   ClasseFuncionario superior = ClasseFuncionario
 *       .builder(1, "SUPERIOR", "Acesso total")
 *       .todasAsPermissoes()
 *       .build();
 *
 *   ClasseFuncionario estoquista = ClasseFuncionario
 *       .builder(3, "ESTOQUISTA", "Movimenta itens de estoque")
 *       .permissoes(Permissao.VER_ESTOQUE, Permissao.EDITAR_ESTOQUE)
 *       .build();
 * </pre>
 *
 * <h2>Imutabilidade após construção</h2>
 * {@link #adicionarPermissao} ainda está disponível para casos onde a
 * classe é construída incrementalmente (compatibilidade com código legado).
 * Prefira o Builder para novas instâncias — classes construídas via builder
 * têm o conjunto de permissões fixo desde o início.
 *
 * <h2>EnumSet</h2>
 * As permissões são armazenadas em {@link EnumSet}, que é otimizado para
 * enums: operações {@code contains}, {@code add} e iteração são O(1) com
 * representação interna como bitmask — mais eficiente que {@link java.util.HashSet}.
 */
@Embeddable
public class ClasseFuncionario {

    // ── Campos ────────────────────────────────────────────────────────────────

    @Column(name = "classe_id")
    private long id;

    @Column(name = "classe_nome", length = 80)
    private String nome;

    @Column(name = "classe_descricao", length = 180)
    private String descricao;

    @Convert(converter = PermissoesStringConverter.class)
    @Column(name = "classe_permissoes", length = 250)
    private Set<Permissao> permissoes; // EnumSet internamente

    // ── Construtor privado — use o Builder ────────────────────────────────────

    private ClasseFuncionario(long id, String nome, String descricao,
                              EnumSet<Permissao> permissoes) {
        this.id         = id;
        this.nome       = nome;
        this.descricao  = descricao;
        this.permissoes = permissoes; // EnumSet mutável internamente, exposto como imutável
    }

    protected ClasseFuncionario() {
        this.permissoes = EnumSet.noneOf(Permissao.class);
    }

    // ── Construtor público (compatibilidade) ──────────────────────────────────

    /**
     * Construtor de compatibilidade para código que ainda cria
     * {@code ClasseFuncionario} diretamente e adiciona permissões via
     * {@link #adicionarPermissao}.
     *
     * <p>Para novos usos, prefira {@link #builder(long, String, String)}.
     *
     * @param id       identificador da classe; {@code >= 0}
     * @param nome     nome da classe (ex: {@code "ESTOQUISTA"}); não pode ser vazio
     * @param descricao descrição do cargo; não pode ser vazia
     */
    public ClasseFuncionario(long id, String nome, String descricao) {
        validar(id >= 0,                         "ID não pode ser negativo.");
        validar(nome != null && !nome.isBlank(),      "Nome é obrigatório.");
        validar(descricao != null && !descricao.isBlank(), "Descrição é obrigatória.");

        this.id         = id;
        this.nome       = nome.trim();
        this.descricao  = descricao.trim();
        this.permissoes = EnumSet.noneOf(Permissao.class);
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    /**
     * Cria um {@link Builder} para construção fluente de uma
     * {@code ClasseFuncionario}.
     *
     * @param id       identificador; {@code >= 0}
     * @param nome     nome do cargo; não pode ser vazio
     * @param descricao descrição; não pode ser vazia
     * @return builder configurado com os dados fornecidos
     */
    public static Builder builder(long id, String nome, String descricao) {
        return new Builder(id, nome, descricao);
    }

    /**
     * Builder fluente para {@link ClasseFuncionario}.
     *
     * <p>Elimina a repetição de {@code adicionarPermissao()} para cada
     * permissão individualmente. Uso:
     * <pre>
     *   ClasseFuncionario gerente = ClasseFuncionario
     *       .builder(2, "GERENTE_ESTOQUE", "Gerencia estoque e compras")
     *       .permissoes(VER_ESTOQUE, EDITAR_ESTOQUE, VER_COMPRAS, VER_FINANCAS)
     *       .build();
     * </pre>
     */
    public static final class Builder {

        private final long          id;
        private final String        nome;
        private final String        descricao;
        private final EnumSet<Permissao> permissoes = EnumSet.noneOf(Permissao.class);

        private Builder(long id, String nome, String descricao) {
            validar(id >= 0,                              "ID não pode ser negativo.");
            validar(nome != null && !nome.isBlank(),      "Nome é obrigatório.");
            validar(descricao != null && !descricao.isBlank(), "Descrição é obrigatória.");
            this.id        = id;
            this.nome      = nome.trim();
            this.descricao = descricao.trim();
        }

        /**
         * Adiciona uma ou mais permissões à classe em construção.
         *
         * @param perms permissões a adicionar; valores nulos são ignorados com segurança
         * @return este builder (encadeamento fluente)
         */
        public Builder permissoes(Permissao... perms) {
            Arrays.stream(perms)
                    .filter(Objects::nonNull)
                    .forEach(permissoes::add);
            return this;
        }

        /**
         * Concede todas as permissões do sistema — equivalente a iterar
         * {@code Permissao.values()} e adicionar cada uma.
         *
         * @return este builder (encadeamento fluente)
         */
        public Builder todasAsPermissoes() {
            permissoes.addAll(EnumSet.allOf(Permissao.class));
            return this;
        }

        /**
         * Constrói a {@link ClasseFuncionario} com as permissões configuradas.
         *
         * @return nova instância imutável de {@code ClasseFuncionario}
         */
        public ClasseFuncionario build() {
            return new ClasseFuncionario(id, nome, descricao,
                    permissoes.isEmpty()
                            ? EnumSet.noneOf(Permissao.class)
                            : EnumSet.copyOf(permissoes));
        }
    }

    // ── Gerenciamento de permissões ───────────────────────────────────────────

    /**
     * Adiciona uma permissão à classe.
     *
     * <p>Disponível para compatibilidade com código que constrói a classe
     * incrementalmente. Para novas instâncias, prefira o {@link Builder}.
     *
     * @param permissao permissão a adicionar; não pode ser {@code null}
     * @throws IllegalArgumentException se {@code permissao} for nula
     */
    public void adicionarPermissao(Permissao permissao) {
        validar(permissao != null, "Permissão não pode ser nula.");
        permissoes.add(permissao);
    }

    /**
     * Remove uma permissão da classe.
     *
     * <p>Operação útil para rebaixamento de cargo sem recriar a instância inteira.
     *
     * @param permissao permissão a remover; nenhuma exceção se não existir
     */
    public void removerPermissao(Permissao permissao) {
        if (permissao != null) permissoes.remove(permissao);
    }

    /**
     * Verifica se esta classe possui uma permissão específica.
     *
     * <p>Operação O(1) — {@link EnumSet} usa bitmask internamente.
     *
     * @param permissao permissão a verificar; retorna {@code false} se {@code null}
     * @return {@code true} se a permissão estiver associada a esta classe
     */
    public boolean possuiPermissao(Permissao permissao) {
        return permissao != null && permissoes.contains(permissao);
    }

    /**
     * Verifica se esta classe possui <strong>todas</strong> as permissões fornecidas.
     *
     * <p>Útil para verificações compostas sem encadear múltiplos
     * {@link #possuiPermissao} com {@code &&}.
     *
     * @param perms permissões a verificar
     * @return {@code true} se todas estiverem presentes
     */
    public boolean possuiTodasAs(Permissao... perms) {
        return Arrays.stream(perms).allMatch(this::possuiPermissao);
    }

    /**
     * Verifica se esta classe possui <strong>ao menos uma</strong> das permissões fornecidas.
     *
     * @param perms permissões a verificar
     * @return {@code true} se ao menos uma estiver presente
     */
    public boolean possuiAlgumaDas(Permissao... perms) {
        return Arrays.stream(perms).anyMatch(this::possuiPermissao);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public long   getId()       { return id;       }
    public String getNome()     { return nome;     }
    public String getDescricao(){ return descricao;}

    /**
     * Retorna uma visão imutável do conjunto de permissões.
     *
     * <p>O {@link EnumSet} interno é mutável (para suportar {@link #adicionarPermissao}
     * e {@link #removerPermissao}), mas a visão retornada aqui impede modificações externas.
     */
    public Set<Permissao> getPermissoes() {
        return Collections.unmodifiableSet(permissoes);
    }

    // ── Infraestrutura de objeto ──────────────────────────────────────────────

    @Override
    public String toString() {
        return String.format("ClasseFuncionario{id=%d, nome='%s', permissoes=%d}",
                id, nome, permissoes.size());
    }

    /**
     * Duas classes são iguais se tiverem o mesmo {@code id}.
     * Classes com {@code id == 0} nunca são iguais entre si.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClasseFuncionario other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    // ── Auxiliar privado ──────────────────────────────────────────────────────

    /** Lança {@link IllegalArgumentException} se a condição for falsa. */
    private static void validar(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalArgumentException(mensagem);
    }
}
