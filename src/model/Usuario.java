package model;

public class Usuario {

    public enum Perfil {
        ADMIN,
        OPERADOR
    }

    private int id;
    private String nome;
    private String login;
    private String senha; // em sistema real não seria assim, mas pra faculdade tá ok
    private Perfil perfil;

    public Usuario(int id, String nome, String login, String senha, Perfil perfil) {
        this.id = id;
        this.nome = nome;
        this.login = login;
        this.senha = senha;
        this.perfil = perfil;
    }

    public int getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getLogin() {
        return login;
    }

    public Perfil getPerfil() {
        return perfil;
    }

    public boolean autenticar(String login, String senha) {
        return this.login.equals(login) && this.senha.equals(senha);
    }
}