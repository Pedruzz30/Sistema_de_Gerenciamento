package model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Registro de auditoria de uma movimentação de estoque.
 *
 * <p>O projeto já usa {@code Pedido} como histórico de entradas e saídas.
 * Para a migração incremental ao PostgreSQL, o contrato do domínio foi
 * preservado e a entidade passou a armazenar snapshots do responsável e
 * dos saldos antes/depois da movimentação.
 */
@Entity
@Table(name = "movimentacoes_estoque")
public class Pedido {

    private static final String FORMATO_DATA = "dd/MM/yyyy HH:mm:ss";
    private static final int LIMITE_DESCRICAO = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(nullable = false)
    private int quantidade;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_movimentacao", nullable = false, length = 20)
    private TipoMovimentacao tipo;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;

    @Column(name = "usuario_responsavel_ru")
    private Long usuarioResponsavelRu;

    @Column(name = "usuario_responsavel_nome", length = 160)
    private String usuarioResponsavelNome;

    @Column(nullable = false, length = LIMITE_DESCRICAO)
    private String descricao;

    @Column(name = "saldo_anterior", nullable = false)
    private int saldoAnterior;

    @Column(name = "saldo_posterior", nullable = false)
    private int saldoPosterior;

    @Transient
    private Usuario usuario;

    protected Pedido() {
        // Construtor exigido pelo JPA.
    }

    /**
     * Construtor compatível com o fluxo legado.
     *
     * <p>Quando o código existente chama este construtor, assume-se que o
     * produto já foi atualizado para o saldo posterior da movimentação.
     */
    public Pedido(int id, Produto produto, int quantidade, TipoMovimentacao tipo, Usuario usuario) {
        this(
                id,
                produto,
                quantidade,
                tipo,
                usuario,
                null,
                inferirSaldoAnterior(produto, quantidade, tipo),
                inferirSaldoPosterior(produto)
        );
    }

    public Pedido(int id,
                  Produto produto,
                  int quantidade,
                  TipoMovimentacao tipo,
                  Usuario usuario,
                  String descricao,
                  int saldoAnterior,
                  int saldoPosterior) {
        this(
                id,
                produto,
                quantidade,
                tipo,
                usuario,
                LocalDateTime.now(),
                descricao,
                saldoAnterior,
                saldoPosterior,
                usuario != null ? usuario.getRu() : null,
                usuario != null ? usuario.getNomeCompleto() : null
        );
    }

    private Pedido(int id,
                   Produto produto,
                   int quantidade,
                   TipoMovimentacao tipo,
                   Usuario usuario,
                   LocalDateTime dataHora,
                   String descricao,
                   int saldoAnterior,
                   int saldoPosterior,
                   Long usuarioResponsavelRu,
                   String usuarioResponsavelNome) {
        validar(id >= 0, "ID do pedido não pode ser negativo.");
        validar(produto != null, "Produto é obrigatório.");
        validar(quantidade > 0, "Quantidade deve ser maior que zero.");
        validar(tipo != null, "Tipo de movimentação é obrigatório.");
        validar(dataHora != null, "Data/hora da movimentação é obrigatória.");
        validar(saldoAnterior >= 0, "Saldo anterior não pode ser negativo.");
        validar(saldoPosterior >= 0, "Saldo posterior não pode ser negativo.");
        validar(tipo.aplicarAoEstoque(saldoAnterior, quantidade) == saldoPosterior,
                "Saldos da movimentação são inconsistentes com o tipo informado.");

        this.id = id;
        this.produto = produto;
        this.quantidade = quantidade;
        this.tipo = tipo;
        this.usuario = usuario;
        this.dataHora = dataHora;
        this.descricao = normalizarDescricao(descricao);
        this.saldoAnterior = saldoAnterior;
        this.saldoPosterior = saldoPosterior;
        this.usuarioResponsavelRu = usuarioResponsavelRu;
        this.usuarioResponsavelNome = normalizarNomeResponsavel(usuarioResponsavelNome);
    }

    public Pedido comId(int novoId) {
        validar(novoId > 0, "Novo ID deve ser maior que zero.");
        return new Pedido(
                novoId,
                produto,
                quantidade,
                tipo,
                usuario,
                dataHora,
                descricao,
                saldoAnterior,
                saldoPosterior,
                usuarioResponsavelRu,
                usuarioResponsavelNome
        );
    }

    public boolean ehEntrada() {
        return tipo == TipoMovimentacao.ENTRADA;
    }

    public boolean ehSaida() {
        return tipo == TipoMovimentacao.SAIDA;
    }

    public int getImpactoAssinado() {
        return switch (tipo) {
            case ENTRADA -> quantidade;
            case SAIDA -> -quantidade;
        };
    }

    public boolean possuiUsuario() {
        return usuarioResponsavelRu != null
                || (usuarioResponsavelNome != null && !usuarioResponsavelNome.isBlank())
                || usuario != null;
    }

    public boolean possuiDescricao() {
        return !descricao.isBlank();
    }

    public String gerarResumo() {
        String resumo = String.format(
                "%s de %d unidade(s) de '%s' em %s por %s (saldo %d -> %d)",
                tipo,
                quantidade,
                produto.getNome(),
                dataHora.format(DateTimeFormatter.ofPattern(FORMATO_DATA)),
                obterResponsavelExibicao(),
                saldoAnterior,
                saldoPosterior
        );

        if (!possuiDescricao()) {
            return resumo;
        }
        return resumo + " | " + descricao;
    }

    public int getId() { return id; }
    public Produto getProduto() { return produto; }
    public int getQuantidade() { return quantidade; }
    public TipoMovimentacao getTipo() { return tipo; }
    public LocalDateTime getDataHora() { return dataHora; }
    public Usuario getUsuario() { return usuario; }
    public Long getUsuarioResponsavelRu() { return usuarioResponsavelRu; }
    public String getUsuarioResponsavelNome() { return usuarioResponsavelNome; }
    public String getDescricao() { return descricao; }
    public int getSaldoAnterior() { return saldoAnterior; }
    public int getSaldoPosterior() { return saldoPosterior; }

    @Override
    public String toString() {
        return "Pedido{id=" + id + ", " + gerarResumo() + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Pedido other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    private String obterResponsavelExibicao() {
        if (usuario != null) {
            return usuario.getNomeCompleto();
        }
        if (usuarioResponsavelNome != null && !usuarioResponsavelNome.isBlank()) {
            return usuarioResponsavelNome;
        }
        if (usuarioResponsavelRu != null) {
            return "RU " + usuarioResponsavelRu;
        }
        return "sistema";
    }

    private static int inferirSaldoAnterior(Produto produto, int quantidade, TipoMovimentacao tipo) {
        validar(produto != null, "Produto é obrigatório.");
        validar(tipo != null, "Tipo de movimentação é obrigatório.");
        validar(quantidade > 0, "Quantidade deve ser maior que zero.");
        return produto.getQuantidadeAtual() - tipo.aplicarImpactoAssinado(quantidade);
    }

    private static int inferirSaldoPosterior(Produto produto) {
        validar(produto != null, "Produto é obrigatório.");
        return produto.getQuantidadeAtual();
    }

    private static String normalizarDescricao(String descricao) {
        if (descricao == null) {
            return "";
        }
        String texto = descricao.trim();
        validar(texto.length() <= LIMITE_DESCRICAO,
                "Descrição da movimentação deve ter no máximo " + LIMITE_DESCRICAO + " caracteres.");
        return texto;
    }

    private static String normalizarNomeResponsavel(String nome) {
        if (nome == null) {
            return null;
        }
        String texto = nome.trim();
        return texto.isEmpty() ? null : texto;
    }

    private static void validar(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalArgumentException(mensagem);
    }
}
