package model;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * Registra o preço de compra de um produto em um determinado mês.
 *
 * Exemplo real:
 *   Janeiro/2025  → Morango → R$ 4,99/unidade
 *   Fevereiro/2025 → Morango → R$ 5,15/unidade   ← ficou mais caro
 *
 * YearMonth é uma classe do Java que representa apenas ano + mês,
 * sem dia nem hora — perfeito para esse caso.
 */
public class CotacaoMensal {
    private final long id;
    private final Produto produto;
    private final YearMonth mesReferencia;   // ex: 2025-01
    private final double precoUnitario;      // preço médio no mês
    private final int quantidadeComprada;    // total comprado no mês

        public CotacaoMensal(long id,
                             Produto produto,
                             YearMonth mesReferencia,
                             double precoUnitario,
                             int quantidadeComprada) {
            if (produto == null) {
                throw new IllegalArgumentException("Produto é obrigatório.");
            }
            if (mesReferencia == null) {
                throw new IllegalArgumentException("Mês de referência é obrigatório.");
            }
            if (precoUnitario < 0) {
                throw new IllegalArgumentException("Preço não pode ser negativo.");
            }
            if (quantidadeComprada <= 0) {
                throw new IllegalArgumentException("Quantidade deve ser maior que zero.");
            }

            this.id = id;
            this.produto = produto;
            this.mesReferencia = mesReferencia;
            this.precoUnitario = precoUnitario;
            this.quantidadeComprada = quantidadeComprada;
         }

        public long getId()                    { return id; }
        public Produto getProduto()            { return produto; }
        public YearMonth getMesReferencia()    { return mesReferencia; }
        public double getPrecoUnitario()       { return precoUnitario; }
        public int getQuantidadeComprada()     { return quantidadeComprada; }

        /** Gasto total do mês com esse produto: quantidade × preço */
        public double calcularGastoTotal() {
            return quantidadeComprada * precoUnitario;
        }

        @Override
        public String toString() {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/yyyy");
            return String.format("CotacaoMensal{produto='%s', mes=%s, preco=R$%.2f, qtd=%d, total=R$%.2f}",
                    produto.getNome(),
                    mesReferencia.format(fmt),
                    precoUnitario,
                    quantidadeComprada,
                    calcularGastoTotal());
        }
}

