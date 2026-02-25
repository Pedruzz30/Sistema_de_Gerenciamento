package model;

public class Usuario {

    public enum Perfil {
        ADMIN,
        OPERADOR
    }

    private final long ru;
    private final String nome;
    private final String sobrenome;
    private final String cpf;
    private final String senha;
    private ClasseFuncionario classe;
    private final Perfil perfil;
    private boolean ativo;

    public Usuario(long ru,
                   String nome,
                   String sobrenome,
                   String cpf,
                   String senha,
                   ClasseFuncionario classe,
                   Perfil perfilOpcional) {
        validarTexto(nome, "Nome");
        validarTexto(sobrenome, "Sobrenome");
        validarTexto(cpf, "CPF");
        validarTexto(senha, "Senha");

        this.ru = ru;
        this.nome = nome.trim();
        this.sobrenome = sobrenome.trim();
        this.cpf = cpf.trim();
        this.senha = senha;
        this.classe = classe;
        this.perfil = perfilOpcional;
        this.ativo = true;
    }

    public long getRu() {
        return ru;
    }

    public String getNome() {
        return nome;
    }

    public String getSobrenome() {
        return sobrenome;
    }

    public String getCpf() {
        return cpf;
    }

    public ClasseFuncionario getClasse() {
        return classe;
    }

    public Perfil getPerfil() {
        return perfil;
    }

    public String getNomeCompleto() {
        return nome + " " + sobrenome;
    }

    public String getSenhaInterna() {
        return senha;
    }

    public boolean autenticar(String nome, String sobrenome, String senha) {
        return this.nome.equalsIgnoreCase(nome)
                && this.sobrenome.equalsIgnoreCase(sobrenome)
                && this.senha.equals(senha);
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void desativar() {
        this.ativo = false;
    }

    public void alterarClasse(ClasseFuncionario novaClasse) {
        if (novaClasse == null) {
            throw new IllegalArgumentException("Classe do funcionário é obrigatória.");
        }
        this.classe = novaClasse;
    }

    private void validarTexto(String valor, String campo) {
        if (valor == null || valor.trim().isEmpty()) {
            throw new IllegalArgumentException(campo + " é obrigatório.");
        }
    }
}