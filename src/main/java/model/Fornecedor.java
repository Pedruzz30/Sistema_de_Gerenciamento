package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Representa um fornecedor cadastrado no sistema.
 *
 * Um fornecedor tem seus próprios produtos vinculados — isso significa que
 * o mesmo produto físico (ex: "Laranja") pode ser fornecido por fornecedores
 * diferentes, com preços diferentes.
 */
public class Fornecedor {

    private final long id;
    private final String nome;
    private final String cnpj;
    private final String telefone;

    // Lista dos produtos que ESTE fornecedor vende
    // Usamos List<Produto> aqui pois a ordem de cadastro pode importar
    private final List<Produto> produtos;

    private boolean ativo;

    public Fornecedor(long id, String nome, String cnpj, String telefone) {
        validarTexto(nome, "Nome");
        validarTexto(cnpj, "CNPJ");

        this.id = id;
        this.nome = nome.trim();
        this.cnpj = cnpj.trim();
        this.telefone = telefone != null ? telefone.trim() : "";
        this.produtos = new ArrayList<>();
        this.ativo = true;
    }

    // ── Produtos vinculados ──────────────────────────────────

    public void adicionarProduto(Produto produto) {
        if (produto == null) {
            throw new IllegalArgumentException("Produto não pode ser nulo.");
        }
        // Evita duplicata pelo ID do produto
        boolean jaExiste = produtos.stream()
                .anyMatch(p -> p.getId() == produto.getId());
        if (jaExiste) {
            throw new IllegalArgumentException(
                    "Produto '" + produto.getNome() + "' já está vinculado a este fornecedor."
            );
        }
        produtos.add(produto);
    }

    public List<Produto> getProdutos() {
        // Retorna cópia imutável — ninguém de fora pode modificar a lista diretamente
        return Collections.unmodifiableList(produtos);
    }

    // ── Getters ──────────────────────────────────────────────

    public long getId()       { return id; }
    public String getNome()   { return nome; }
    public String getCnpj()   { return cnpj; }
    public String getTelefone() { return telefone; }
    public boolean isAtivo()  { return ativo; }

    public void desativar() {
        this.ativo = false;
    }

    public void reativar() {
        this.ativo = true;
    }

    // ── Validação interna ─────────────────────────────────────

    private void validarTexto(String valor, String campo) {
        if (valor == null || valor.trim().isEmpty()) {
            throw new IllegalArgumentException(campo + " é obrigatório.");
        }
    }

    @Override
    public String toString() {
        return String.format("Fornecedor{id=%d, nome='%s', cnpj='%s', produtos=%d}",
                id, nome, cnpj, produtos.size());
    }
}
