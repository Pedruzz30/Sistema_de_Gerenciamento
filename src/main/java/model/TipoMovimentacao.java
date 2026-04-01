package model;

public enum TipoMovimentacao {
    ENTRADA {
        @Override
        public int aplicarAoEstoque(int quantidadeAtual, int quantidadeMovimentada) {
            validarParametros(quantidadeAtual, quantidadeMovimentada);
            return quantidadeAtual + quantidadeMovimentada;
        }

        @Override
        public int getSinal() {
            return 1;
        }
    },
    SAIDA {
        @Override
        public int aplicarAoEstoque(int quantidadeAtual, int quantidadeMovimentada) {
            validarParametros(quantidadeAtual, quantidadeMovimentada);
            if (quantidadeMovimentada > quantidadeAtual) {
                throw new IllegalArgumentException("Não é possível realizar saída maior que o estoque atual.");
            }
            return quantidadeAtual - quantidadeMovimentada;
        }

        @Override
        public int getSinal() {
            return -1;
        }
    };

    public abstract int aplicarAoEstoque(int quantidadeAtual, int quantidadeMovimentada);

    public abstract int getSinal();

    public boolean ehEntrada() {
        return this == ENTRADA;
    }

    public boolean ehSaida() {
        return this == SAIDA;
    }

    public int aplicarImpactoAssinado(int quantidadeMovimentada) {
        if (quantidadeMovimentada <= 0) {
            throw new IllegalArgumentException("A quantidade movimentada deve ser maior que zero.");
        }
        return quantidadeMovimentada * getSinal();
    }

    protected void validarParametros(int quantidadeAtual, int quantidadeMovimentada) {
        if (quantidadeAtual < 0) {
            throw new IllegalArgumentException("A quantidade atual não pode ser negativa.");
        }
        if (quantidadeMovimentada <= 0) {
            throw new IllegalArgumentException("A quantidade movimentada deve ser maior que zero.");
        }
    }
}
