package model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Conta de quem consome na loja e paga no fim. É um cartão numerado: o número volta a ser
 * usado depois que a comanda fecha. Os itens ficam numa venda em rascunho, que é finalizada
 * no caixa como qualquer outra.
 */
@Entity
@Table(name = "comandas")
public class Comanda {

    public static final int NUMERO_MAXIMO = 9999;

    public enum Status { ABERTA, FECHADA, CANCELADA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false)
    private int numero;

    @Column(name = "venda_id", nullable = false)
    private long vendaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "aberta_em", nullable = false)
    private LocalDateTime abertaEm;

    @Column(name = "aberta_por")
    private Long abertaPor;

    @Column(name = "fechada_em")
    private LocalDateTime fechadaEm;

    protected Comanda() {
    }

    public Comanda(int numero, long vendaId, Long abertaPor, LocalDateTime agora) {
        validarNumero(numero);
        if (vendaId <= 0) {
            throw new IllegalArgumentException("Venda da comanda e obrigatoria.");
        }
        this.numero = numero;
        this.vendaId = vendaId;
        this.abertaPor = abertaPor;
        this.abertaEm = agora;
        this.status = Status.ABERTA;
    }

    public static void validarNumero(int numero) {
        if (numero < 1 || numero > NUMERO_MAXIMO) {
            throw new IllegalArgumentException("Numero da comanda vai de 1 a " + NUMERO_MAXIMO + ".");
        }
    }

    public void fechar(LocalDateTime agora) {
        exigirAberta();
        this.status = Status.FECHADA;
        this.fechadaEm = agora;
    }

    public void cancelar(LocalDateTime agora) {
        exigirAberta();
        this.status = Status.CANCELADA;
        this.fechadaEm = agora;
    }

    private void exigirAberta() {
        if (status != Status.ABERTA) {
            throw new IllegalStateException("A comanda " + numero + " nao esta aberta.");
        }
    }

    public long getId() { return id; }
    public int getNumero() { return numero; }
    public long getVendaId() { return vendaId; }
    public Status getStatus() { return status; }
    public LocalDateTime getAbertaEm() { return abertaEm; }
    public Long getAbertaPor() { return abertaPor; }
    public LocalDateTime getFechadaEm() { return fechadaEm; }
    public boolean isAberta() { return status == Status.ABERTA; }
}
