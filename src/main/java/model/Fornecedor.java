package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Representa um fornecedor cadastrado no sistema.
 *
 * <h2>Responsabilidades</h2>
 * <ul>
 *   <li>Guardar dados cadastrais (nome, CNPJ, telefone).</li>
 *   <li>Manter a lista de produtos que este fornecedor vende.</li>
 *   <li>Controlar o estado ativo/inativo (soft delete — fornecedores nunca
 *       são excluídos porque notas fiscais históricas dependem deles).</li>
 * </ul>
 *
 * <h2>CNPJ</h2>
 * O CNPJ é normalizado para 14 dígitos sem formatação e validado pelo
 * algoritmo oficial da Receita Federal (módulo 11, dois dígitos verificadores).
 * A exibição usa máscara {@code XX.XXX.XXX/XXXX-XX}.
 *
 * <h2>Identidade e padrão de ID</h2>
 * {@code id == 0} indica que o fornecedor ainda não foi persistido.
 * IDs negativos são rejeitados. Use {@link #comId(long)} para que o
 * repositório retorne uma cópia com o ID definitivo após a persistência.
 */
public class Fornecedor {

    // ── Campos imutáveis ──────────────────────────────────────────────────────

    private final long   id;
    private final String nome;
    private final String cnpj;      // sempre 14 dígitos, sem formatação
    private final String telefone;  // opcional; string vazia se ausente

    // ── Campos mutáveis controlados ───────────────────────────────────────────

    private boolean        ativo;
    private final List<Produto> produtos;

    // ── Construtor ────────────────────────────────────────────────────────────

    /**
     * Cria um fornecedor no estado ativo.
     *
     * @param id        identificador único; {@code >= 0} (0 = ainda não persistido)
     * @param nome      nome do fornecedor; não pode ser nulo ou vazio
     * @param cnpj      CNPJ com ou sem formatação (pontos, barras, traços são removidos);
     *                  deve ter 14 dígitos e passar na validação da Receita Federal
     * @param telefone  telefone de contato; {@code null} é aceito (tratado como vazio)
     * @throws IllegalArgumentException se qualquer argumento obrigatório for inválido
     */
    public Fornecedor(long id, String nome, String cnpj, String telefone) {
        validar(id >= 0,                  "ID do fornecedor não pode ser negativo.");
        validar(nome != null && !nome.isBlank(), "Nome é obrigatório.");

        String cnpjNormalizado = normalizarCnpj(cnpj);   // valida comprimento
        validar(cnpjDigitosValidos(cnpjNormalizado),      "CNPJ inválido (dígitos verificadores incorretos).");

        this.id       = id;
        this.nome     = nome.trim();
        this.cnpj     = cnpjNormalizado;
        this.telefone = telefone != null ? telefone.trim() : "";
        this.produtos = new ArrayList<>();
        this.ativo    = true;
    }

    // ── Fábrica para repositório ──────────────────────────────────────────────

    /**
     * Retorna uma cópia deste fornecedor com o ID substituído.
     *
     * <p>Permite que o repositório atribua o ID definitivo após a persistência
     * sem expor um setter de ID:
     * <pre>
     *   Fornecedor salvo = fornecedor.comId(proximoId++);
     *   armazenamento.put(salvo.getId(), salvo);
     *   return salvo;
     * </pre>
     *
     * @param novoId ID definitivo; deve ser {@code > 0}
     * @return novo objeto {@code Fornecedor} com o ID fornecido e mesmo estado
     */
    public Fornecedor comId(long novoId) {
        validar(novoId > 0, "Novo ID deve ser maior que zero.");
        Fornecedor copia = new Fornecedor(novoId, nome, cnpj, telefone);
        if (!ativo) copia.desativar();
        produtos.forEach(copia::adicionarProduto);
        return copia;
    }

    // ── Gerenciamento de produtos ─────────────────────────────────────────────

    /**
     * Vincula um produto a este fornecedor.
     *
     * <p>Cada produto só pode aparecer uma vez na lista. A verificação de
     * duplicata usa {@link Produto#equals}, que compara por ID.
     *
     * @param produto produto a vincular; não pode ser {@code null}
     * @throws IllegalStateException    se o fornecedor estiver inativo
     * @throws IllegalArgumentException se o produto for nulo ou já estiver vinculado
     */
    public void adicionarProduto(Produto produto) {
        validar(ativo,          "Não é possível adicionar produtos a um fornecedor inativo.");
        validar(produto != null, "Produto não pode ser nulo.");
        validar(!produtos.contains(produto),
                "Produto '" + produto.getNome() + "' já está vinculado a este fornecedor.");
        produtos.add(produto);
    }

    /**
     * Remove o vínculo de um produto com este fornecedor.
     *
     * @param produtoId ID do produto a desvincular
     * @throws IllegalArgumentException se nenhum produto com o ID fornecido estiver vinculado
     */
    public void removerProduto(int produtoId) {
        boolean removido = produtos.removeIf(p -> p.getId() == produtoId);
        validar(removido, "Produto com ID " + produtoId + " não está vinculado a este fornecedor.");
    }

    /**
     * Retorna uma visão imutável dos produtos vinculados.
     *
     * <p>Use {@link #adicionarProduto} e {@link #removerProduto} para modificar.
     */
    public List<Produto> getProdutos() {
        return Collections.unmodifiableList(produtos);
    }

    /** Retorna {@code true} quando o fornecedor j&aacute; vende o produto informado. */
    public boolean possuiProduto(int produtoId) {
        return produtos.stream().anyMatch(produto -> produto.getId() == produtoId);
    }

    // ── Ciclo de vida ativo/inativo ───────────────────────────────────────────

    /**
     * Desativa o fornecedor (soft delete).
     *
     * <p>Fornecedores inativos não podem receber novos produtos nem novas notas.
     * O histórico de notas passadas permanece intacto.
     *
     * @throws IllegalStateException se o fornecedor já estiver inativo
     */
    public void desativar() {
        validarTransicaoAtivo(false, "Fornecedor '" + nome + "' já está inativo.");
        this.ativo = false;
    }

    /**
     * Reativa um fornecedor previamente desativado.
     *
     * @throws IllegalStateException se o fornecedor já estiver ativo
     */
    public void reativar() {
        validarTransicaoAtivo(true, "Fornecedor '" + nome + "' já está ativo.");
        this.ativo = true;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public long    getId()      { return id;       }
    public String  getNome()    { return nome;     }
    public String  getTelefone(){ return telefone; }
    public boolean isAtivo()    { return ativo;    }

    /** Retorna o CNPJ formatado no padrão {@code XX.XXX.XXX/XXXX-XX}. */
    public String getCnpj() {
        return formatarCnpj(cnpj);
    }

    /** Retorna o CNPJ como 14 dígitos sem qualquer formatação. */
    public String getCnpjBruto() {
        return cnpj;
    }

    // ── Infraestrutura de objeto ──────────────────────────────────────────────

    @Override
    public String toString() {
        return String.format(
                "Fornecedor{id=%d, nome='%s', cnpj='%s', produtos=%d, ativo=%b}",
                id, nome, mascararCnpj(cnpj), produtos.size(), ativo
        );
    }

    /**
     * Dois fornecedores são iguais se tiverem o mesmo {@code id}.
     * Fornecedores com {@code id == 0} nunca são iguais entre si.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Fornecedor other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    // ── Auxiliares privados ───────────────────────────────────────────────────

    /**
     * Lança {@link IllegalArgumentException} se a condição for falsa.
     * Padrão consistente com {@link Produto}, {@link NotaFiscal} e {@link ItemNotaFiscal}.
     */
    private static void validar(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalArgumentException(mensagem);
    }

    /**
     * Validação de transição de estado ativo — evita repetir a mesma checagem
     * em {@link #desativar()} e {@link #reativar()}.
     */
    private void validarTransicaoAtivo(boolean esperado, String mensagem) {
        if (ativo == esperado) throw new IllegalStateException(mensagem);
    }

    /**
     * Remove caracteres não-numéricos e valida que o resultado tem 14 dígitos.
     *
     * @throws IllegalArgumentException se o CNPJ não contiver exatamente 14 dígitos
     */
    private static String normalizarCnpj(String cnpj) {
        validar(cnpj != null && !cnpj.isBlank(), "CNPJ é obrigatório.");
        String digitos = cnpj.replaceAll("\\D", "");
        validar(digitos.length() == 14,
                "CNPJ deve conter 14 dígitos. Recebido: " + digitos.length() + " dígito(s).");
        return digitos;
    }

    /**
     * Valida os dígitos verificadores do CNPJ pelo algoritmo da Receita Federal.
     *
     * <h3>Como funciona</h3>
     * O CNPJ tem dois dígitos verificadores (posições 12 e 13). Cada um é
     * calculado por uma soma ponderada dos dígitos anteriores, módulo 11:
     * <pre>
     *   Pesos do 1º dígito: 5 4 3 2 9 8 7 6 5 4 3 2
     *   Pesos do 2º dígito: 6 5 4 3 2 9 8 7 6 5 4 3 2
     *
     *   Resto = soma % 11
     *   Dígito = (Resto < 2) ? 0 : (11 - Resto)
     * </pre>
     *
     * Sequências de dígitos iguais ({@code 00000000000000}, {@code 11111111111111}
     * etc.) são matematicamente válidas pelo algoritmo mas são CNPJs fictícios
     * — rejeitados explicitamente.
     *
     * @param digitos 14 dígitos numéricos sem formatação
     * @return {@code true} se os dígitos verificadores estiverem corretos
     */
    private static boolean cnpjDigitosValidos(String digitos) {
        // Rejeita sequências homogêneas (00...0, 11...1, etc.)
        if (digitos.chars().distinct().count() == 1) return false;

        int[] pesos1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] pesos2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

        return digitoValido(digitos, pesos1, 12)
                && digitoValido(digitos, pesos2, 13);
    }

    /** Calcula e compara um único dígito verificador. */
    private static boolean digitoValido(String digitos, int[] pesos, int posicao) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += Character.getNumericValue(digitos.charAt(i)) * pesos[i];
        }
        int resto   = soma % 11;
        int esperado = resto < 2 ? 0 : 11 - resto;
        return Character.getNumericValue(digitos.charAt(posicao)) == esperado;
    }

    /**
     * Formata 14 dígitos no padrão oficial {@code XX.XXX.XXX/XXXX-XX}.
     */
    private static String formatarCnpj(String digitos) {
        return String.format("%s.%s.%s/%s-%s",
                digitos.substring(0, 2),
                digitos.substring(2, 5),
                digitos.substring(5, 8),
                digitos.substring(8, 12),
                digitos.substring(12)
        );
    }

    private static String mascararCnpj(String digitos) {
        return digitos.substring(0, 2) + ".***.***/****-" + digitos.substring(12);
    }
}
