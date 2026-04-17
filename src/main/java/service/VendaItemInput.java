package service;

public record VendaItemInput(Integer produtoId, Integer itemCardapioId, int quantidade) {

    public VendaItemInput(int produtoId, int quantidade) {
        this(produtoId, null, quantidade);
    }

    public static VendaItemInput itemCardapio(int itemCardapioId, int quantidade) {
        return new VendaItemInput(null, itemCardapioId, quantidade);
    }

    public boolean possuiProdutoEmEstoque() {
        return produtoId != null && produtoId > 0;
    }

    public boolean possuiItemCardapio() {
        return itemCardapioId != null && itemCardapioId > 0;
    }

    public boolean possuiReferenciaValida() {
        return quantidade > 0 && possuiProdutoEmEstoque() != possuiItemCardapio();
    }
}
