package model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Evento auditável de encerramento de caixa.
 *
 * <p>Ao contrário do snapshot em memória anterior, este registro é persistido
 * com todos os dados relevantes do fechamento: quem abriu, quem fechou,
 * valor calculado pelo sistema, valor contado fisicamente e eventual
 * divergência.
 */
@Entity
@Table(name = "fechamentos_caixa")
public class FechamentoCaixa {

    private static final int ESCALA_MONETARIA = 2;
    private static final int LIMITE_NOME = 120;
    private static final int LIMITE_OBSERVACAO = 500;
    private static final String FORMATO_DATA = "dd/MM/yyyy HH:mm";
    private static final int LARGURA_NOME = 18;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "caixa_id", nullable = false)
    private long caixaId;

    @Column(name = "numero_caixa", nullable = false)
    private int numeroCaixa;

    @Column(name = "aberto_por", nullable = false, length = LIMITE_NOME)
    private String abertoPor;

    @Column(name = "fechado_por", nullable = false, length = LIMITE_NOME)
    private String fechadoPor;

    @Column(name = "quantidade_movimentacoes", nullable = false)
    private int quantidadeMovimentacoes;

    @Column(name = "saldo_inicial", nullable = false, precision = 19, scale = 2)
    private BigDecimal saldoInicial;

    @Column(name = "total_entradas", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalEntradas;

    @Column(name = "total_saidas", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalSaidas;

    @Column(name = "total_vendas", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalVendas;

    @Column(name = "valor_sistema", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorSistema;

    @Column(name = "valor_contado", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorContado;

    @Column(name = "divergencia", nullable = false, precision = 19, scale = 2)
    private BigDecimal divergencia;

    @Column(name = "observacao", length = LIMITE_OBSERVACAO)
    private String observacao;

    @Column(name = "timestamp_fechamento", nullable = false)
    private LocalDateTime timestamp;

    protected FechamentoCaixa() {
        // Construtor exigido pelo JPA.
    }

    private FechamentoCaixa(long id,
                            long caixaId,
                            int numeroCaixa,
                            String abertoPor,
                            String fechadoPor,
                            int quantidadeMovimentacoes,
                            BigDecimal saldoInicial,
                            BigDecimal totalEntradas,
                            BigDecimal totalSaidas,
                            BigDecimal totalVendas,
                            BigDecimal valorSistema,
                            BigDecimal valorContado,
                            BigDecimal divergencia,
                            String observacao,
                            LocalDateTime timestamp) {
        validar(id >= 0, "ID do fechamento nao pode ser negativo.");
        validar(caixaId > 0, "Fechamento deve referenciar um caixa persistido.");
        validar(numeroCaixa > 0, "Numero do caixa deve ser maior que zero.");
        validar(quantidadeMovimentacoes >= 0, "Quantidade de movimentacoes nao pode ser negativa.");
        validar(timestamp != null, "Timestamp do fechamento e obrigatorio.");

        this.id = id;
        this.caixaId = caixaId;
        this.numeroCaixa = numeroCaixa;
        this.abertoPor = normalizarTextoObrigatorio(abertoPor, LIMITE_NOME, "Operador de abertura e obrigatorio.");
        this.fechadoPor = normalizarTextoObrigatorio(fechadoPor, LIMITE_NOME, "Operador de fechamento e obrigatorio.");
        this.quantidadeMovimentacoes = quantidadeMovimentacoes;
        this.saldoInicial = normalizarValor(saldoInicial);
        this.totalEntradas = normalizarValor(totalEntradas);
        this.totalSaidas = normalizarValor(totalSaidas);
        this.totalVendas = normalizarValor(totalVendas);
        this.valorSistema = normalizarValor(valorSistema);
        this.valorContado = normalizarValor(valorContado);
        this.divergencia = normalizarValor(divergencia);
        this.observacao = normalizarTextoOpcional(observacao, LIMITE_OBSERVACAO);
        this.timestamp = timestamp;
    }

    public static FechamentoCaixa gerar(Caixa caixa,
                                        Usuario atorFechamento,
                                        BigDecimal valorSistema,
                                        BigDecimal valorContado,
                                        BigDecimal divergencia,
                                        String observacao) {
        validar(caixa != null, "Caixa nao pode ser nulo.");
        validar(caixa.getStatus() == Caixa.Status.ENCERRADO,
                "So e possivel gerar fechamento de caixa ENCERRADO. Status atual: " + caixa.getStatus());
        validar(caixa.getDataEncerramento() != null,
                "Data de encerramento ausente; o caixa pode nao ter sido encerrado corretamente.");
        validar(caixa.getOperadorAtual() != null,
                "Operador de abertura ausente no caixa encerrado.");
        validar(atorFechamento != null, "Operador de fechamento e obrigatorio.");

        return new FechamentoCaixa(
                0,
                caixa.getId(),
                caixa.getNumeroCaixa(),
                caixa.getOperadorAtual().getNomeCompleto(),
                atorFechamento.getNomeCompleto(),
                caixa.getMovimentacoes().size(),
                caixa.getSaldoInicial(),
                caixa.calcularTotalEntradas(),
                caixa.calcularTotalSaidas(),
                caixa.calcularTotalVendas(),
                valorSistema,
                valorContado,
                divergencia,
                observacao,
                caixa.getDataEncerramento()
        );
    }

    public FechamentoCaixa comId(long novoId) {
        validar(novoId > 0, "Novo ID do fechamento deve ser maior que zero.");
        return new FechamentoCaixa(
                novoId,
                caixaId,
                numeroCaixa,
                abertoPor,
                fechadoPor,
                quantidadeMovimentacoes,
                saldoInicial,
                totalEntradas,
                totalSaidas,
                totalVendas,
                valorSistema,
                valorContado,
                divergencia,
                observacao,
                timestamp
        );
    }

    public BigDecimal calcularSaldoEsperado() {
        return saldoInicial
                .add(totalEntradas)
                .subtract(totalSaidas)
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }

    public BigDecimal calcularDivergencia() {
        return divergencia;
    }

    public boolean estaBalanceado() {
        return divergencia.compareTo(BigDecimal.ZERO) == 0;
    }

    public String gerarResumo() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern(FORMATO_DATA);
        String abertoPorExibido = truncar(abertoPor, LARGURA_NOME);
        String fechadoPorExibido = truncar(fechadoPor, LARGURA_NOME);
        String statusBalanco = estaBalanceado() ? "Balanceado" : "Divergencia";

        return String.format(
                "┌──────────────────────────────────────────┐%n" +
                        "│  FECHAMENTO CAIXA %-2d  %-17s│%n" +
                        "├──────────────────────────────────────────┤%n" +
                        "│  Aberto por     %-26s│%n" +
                        "│  Fechado por    %-26s│%n" +
                        "│  Data/Hora      %-26s│%n" +
                        "│  Movimentacoes  %-26d│%n" +
                        "├──────────────────────────────────────────┤%n" +
                        "│  Saldo inicial  %26s│%n" +
                        "│  Total entradas %26s│%n" +
                        "│  Total saidas   %26s│%n" +
                        "│  Total vendas   %26s│%n" +
                        "├──────────────────────────────────────────┤%n" +
                        "│  Valor sistema  %26s│%n" +
                        "│  Valor contado  %26s│%n" +
                        "│  Divergencia    %26s│%n" +
                        "│  Status         %-26s│%n" +
                        "└──────────────────────────────────────────┘",
                numeroCaixa,
                timestamp.format(fmt),
                abertoPorExibido,
                fechadoPorExibido,
                timestamp.format(fmt),
                quantidadeMovimentacoes,
                formatarMoeda(saldoInicial),
                formatarMoeda(totalEntradas),
                formatarMoeda(totalSaidas),
                formatarMoeda(totalVendas),
                formatarMoeda(valorSistema),
                formatarMoeda(valorContado),
                formatarMoeda(divergencia),
                statusBalanco
        );
    }

    public long getId() {
        return id;
    }

    public long getCaixaId() {
        return caixaId;
    }

    public int getNumeroCaixa() {
        return numeroCaixa;
    }

    public LocalDate getData() {
        return timestamp.toLocalDate();
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getNomeOperador() {
        return abertoPor;
    }

    public String getAbertoPor() {
        return abertoPor;
    }

    public String getFechadoPor() {
        return fechadoPor;
    }

    public int getQuantidadeMovimentacoes() {
        return quantidadeMovimentacoes;
    }

    public BigDecimal getSaldoInicial() {
        return saldoInicial;
    }

    public BigDecimal getTotalEntradas() {
        return totalEntradas;
    }

    public BigDecimal getTotalSaidas() {
        return totalSaidas;
    }

    public BigDecimal getTotalVendas() {
        return totalVendas;
    }

    public BigDecimal getSaldoFinal() {
        return valorSistema;
    }

    public BigDecimal getValorSistema() {
        return valorSistema;
    }

    public BigDecimal getValorContado() {
        return valorContado;
    }

    public BigDecimal getDivergencia() {
        return divergencia;
    }

    public String getObservacao() {
        return observacao;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FechamentoCaixa other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return gerarResumo();
    }

    private static BigDecimal normalizarValor(BigDecimal valor) {
        validar(valor != null, "Valor monetario nao pode ser nulo.");
        return valor.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }

    private static String normalizarTextoObrigatorio(String texto, int limite, String mensagem) {
        validar(texto != null && !texto.isBlank(), mensagem);
        String textoNormalizado = texto.trim();
        validar(textoNormalizado.length() <= limite,
                "Texto excede o limite de " + limite + " caracteres.");
        return textoNormalizado;
    }

    private static String normalizarTextoOpcional(String texto, int limite) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        String textoNormalizado = texto.trim();
        validar(textoNormalizado.length() <= limite,
                "Texto excede o limite de " + limite + " caracteres.");
        return textoNormalizado;
    }

    private static String formatarMoeda(BigDecimal valor) {
        String sinal = valor.signum() < 0 ? "-" : " ";
        BigDecimal abs = valor.abs().setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        return sinal + "R$ " + abs.toPlainString();
    }

    private static String truncar(String texto, int limite) {
        if (texto == null) return "";
        if (texto.length() <= limite) return texto;
        return texto.substring(0, limite - 1) + "...";
    }

    private static void validar(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalArgumentException(mensagem);
    }
}
