package model;

import java.time.LocalDateTime;

public class LogAcao {

    private final long id;
    private final Long usuarioRu;
    private final String acao;
    private final String descricao;
    private final LocalDateTime dataHora;

    public LogAcao(long id, Long usuarioRu, String acao, String descricao) {
        this.id = id;
        this.usuarioRu = usuarioRu;
        this.acao = acao;
        this.descricao = descricao;
        this.dataHora = LocalDateTime.now();
    }

    public long getId() {
        return id;
    }

    public Long getUsuarioRu() {
        return usuarioRu;
    }

    public String getAcao() {
        return acao;
    }

    public String getDescricao() {
        return descricao;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }
}

