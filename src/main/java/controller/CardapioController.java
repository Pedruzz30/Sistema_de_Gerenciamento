package controller;

import model.Permissao;
import model.Usuario;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import repository.UsuarioRepository;
import service.PizzaMenuCatalogService;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/cardapio")
public class CardapioController {

    private final PizzaMenuCatalogService pizzaMenuCatalogService;
    private final UsuarioRepository usuarioRepository;

    public CardapioController(PizzaMenuCatalogService pizzaMenuCatalogService,
                              UsuarioRepository usuarioRepository) {
        this.pizzaMenuCatalogService = pizzaMenuCatalogService;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/pizzas")
    public ResponseEntity<List<CardapioPizzaResponse>> listarPizzas(
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = ControllerUtils.resolveUser(ruHeader, usuarioRepository);
        ControllerUtils.requireAnyPermission(
                usuario,
                "Voce nao tem permissao para visualizar o cardapio do PDV.",
                Permissao.VER_VENDAS,
                Permissao.VER_ESTOQUE,
                Permissao.VER_FINANCAS
        );

        List<CardapioPizzaResponse> resposta = pizzaMenuCatalogService.listarPizzas().stream()
                .map(CardapioPizzaResponse::from)
                .toList();
        return ResponseEntity.ok(resposta);
    }

    public record CardapioPizzaResponse(
            int id,
            String cardapioItemId,
            String nome,
            Integer quantidadeAtual,
            Integer quantidadeMinima,
            BigDecimal precoUnitario,
            String nivelEstoque,
            String categoriaEstoque,
            String categoriaCardapio,
            String grupoCardapio,
            String grupoTitulo,
            String varianteCardapio,
            String varianteTitulo,
            int ordemExibicao,
            int ordemVariante,
            boolean ativoNoCardapio,
            boolean controladoPorEstoque,
            boolean disponivel
    ) {
        public static CardapioPizzaResponse from(PizzaMenuCatalogService.PizzaMenuItem item) {
            return new CardapioPizzaResponse(
                    item.id(),
                    item.cardapioItemId(),
                    item.nome(),
                    null,
                    null,
                    item.precoUnitario(),
                    null,
                    null,
                    "pizza",
                    item.grupoCardapio(),
                    item.grupoTitulo(),
                    item.varianteCardapio(),
                    item.varianteTitulo(),
                    item.ordemExibicao(),
                    item.ordemVariante(),
                    true,
                    false,
                    true
            );
        }
    }
}
