package service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PizzaMenuCatalogService {

    private static final BigDecimal PRECO_BROTO = new BigDecimal("29.90");
    private static final BigDecimal PRECO_GRANDE = new BigDecimal("49.90");

    private final List<PizzaMenuItem> itens;
    private final Map<String, PizzaMenuItem> itensPorCodigo;

    public PizzaMenuCatalogService() {
        this.itens = List.of(
                pizza(-1, "pizza_calabresa_broto", "Pizza Calabresa Broto", "pizza_calabresa", "Calabresa", "broto", "Broto", 10, 20, PRECO_BROTO),
                pizza(-2, "pizza_calabresa_grande", "Pizza Calabresa Grande", "pizza_calabresa", "Calabresa", "grande", "Grande", 10, 40, PRECO_GRANDE),
                pizza(-3, "pizza_mussarela_broto", "Pizza Mussarela Broto", "pizza_mussarela", "Mussarela", "broto", "Broto", 20, 20, PRECO_BROTO),
                pizza(-4, "pizza_mussarela_grande", "Pizza Mussarela Grande", "pizza_mussarela", "Mussarela", "grande", "Grande", 20, 40, PRECO_GRANDE),
                pizza(-5, "pizza_portuguesa_broto", "Pizza Portuguesa Broto", "pizza_portuguesa", "Portuguesa", "broto", "Broto", 30, 20, PRECO_BROTO),
                pizza(-6, "pizza_portuguesa_grande", "Pizza Portuguesa Grande", "pizza_portuguesa", "Portuguesa", "grande", "Grande", 30, 40, PRECO_GRANDE),
                pizza(-7, "pizza_frango_catupiry_broto", "Pizza Frango Catupiry Broto", "pizza_frango_catupiry", "Frango c/ Catupiry", "broto", "Broto", 40, 20, PRECO_BROTO),
                pizza(-8, "pizza_frango_catupiry_grande", "Pizza Frango Catupiry Grande", "pizza_frango_catupiry", "Frango c/ Catupiry", "grande", "Grande", 40, 40, PRECO_GRANDE),
                pizza(-9, "pizza_quatro_queijos_broto", "Pizza Quatro Queijos Broto", "pizza_quatro_queijos", "Quatro Queijos", "broto", "Broto", 50, 20, PRECO_BROTO),
                pizza(-10, "pizza_quatro_queijos_grande", "Pizza Quatro Queijos Grande", "pizza_quatro_queijos", "Quatro Queijos", "grande", "Grande", 50, 40, PRECO_GRANDE),
                pizza(-11, "pizza_pepperoni_broto", "Pizza Pepperoni Broto", "pizza_pepperoni", "Pepperoni", "broto", "Broto", 60, 20, PRECO_BROTO),
                pizza(-12, "pizza_pepperoni_grande", "Pizza Pepperoni Grande", "pizza_pepperoni", "Pepperoni", "grande", "Grande", 60, 40, PRECO_GRANDE)
        );
        this.itensPorCodigo = this.itens.stream()
                .collect(Collectors.toUnmodifiableMap(PizzaMenuItem::cardapioItemId, Function.identity()));
    }

    public List<PizzaMenuItem> listarPizzas() {
        return itens;
    }

    public PizzaMenuItem buscarPizzaPorCodigo(String cardapioItemId) {
        if (cardapioItemId == null || cardapioItemId.isBlank()) {
            throw new IllegalArgumentException("Item de cardapio invalido.");
        }

        PizzaMenuItem item = itensPorCodigo.get(cardapioItemId.trim());
        if (item == null) {
            throw new IllegalArgumentException("Item de cardapio nao encontrado: " + cardapioItemId);
        }
        return item;
    }

    private static PizzaMenuItem pizza(int id,
                                       String cardapioItemId,
                                       String nome,
                                       String grupoCardapio,
                                       String grupoTitulo,
                                       String varianteCardapio,
                                       String varianteTitulo,
                                       int ordemExibicao,
                                       int ordemVariante,
                                       BigDecimal precoUnitario) {
        return new PizzaMenuItem(
                id,
                cardapioItemId,
                nome,
                precoUnitario,
                grupoCardapio,
                grupoTitulo,
                varianteCardapio,
                varianteTitulo,
                ordemExibicao,
                ordemVariante
        );
    }

    public record PizzaMenuItem(
            int id,
            String cardapioItemId,
            String nome,
            BigDecimal precoUnitario,
            String grupoCardapio,
            String grupoTitulo,
            String varianteCardapio,
            String varianteTitulo,
            int ordemExibicao,
            int ordemVariante
    ) {
    }
}
