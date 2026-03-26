package model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ClasseFuncionario {

    private final long id;
    private final String nome;
    private final String descricao;
    private final Set<Permissao> permissoes;

    public ClasseFuncionario(long id, String nome, String descricao) {
        this.id = id;
        this.nome = nome;
        this.descricao = descricao;
        this.permissoes = new HashSet<>();
    }

    public void adicionarPermissao(Permissao permissao) {
        if (permissao == null) {
            throw new IllegalArgumentException("Permissão não pode ser nula.");
        }
        permissoes.add(permissao);
    }

    public boolean possuiPermissao(Permissao permissao) {
        return permissoes.contains(permissao);
    }

    public long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public Set<Permissao> getPermissoes() {
        return Collections.unmodifiableSet(permissoes);
    }
}
