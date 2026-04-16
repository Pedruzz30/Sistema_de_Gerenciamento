package service;

public record VendaItemInput(Integer produtoId, String cardapioItemId, int quantidade) {

    public VendaItemInput(int produtoId, int quantidade) {
        this(produtoId, null, quantidade);
    }

    public VendaItemInput(String cardapioItemId, int quantidade) {
        this(null, cardapioItemId, quantidade);
    }

    public boolean possuiProdutoEmEstoque() {
        return produtoId != null && produtoId > 0;
    }

    public boolean possuiItemSobDemanda() {
        return cardapioItemId != null && !cardapioItemId.isBlank();
    }

    public boolean possuiReferenciaValida() {
        return quantidade > 0 && possuiProdutoEmEstoque() != possuiItemSobDemanda();
    }
}
