package service;

import model.LogAcao;
import model.Permissao;
import model.Usuario;
import model.Venda;
import model.VendaItem;
import org.springframework.transaction.annotation.Transactional;
import repository.LogRepository;
import repository.VendaItemRepository;
import repository.VendaRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Desconto no total da venda, dado na hora do pagamento.
 *
 * <p>O valor é repartido entre os itens, na proporção de cada um,
 * e gravado como desconto por unidade. Assim o total pago,
 * o cancelamento e a margem por produto continuam coerentes.
 * Um novo desconto substitui o anterior; zero retira.
 *
 * <p>Quem não cuida do financeiro precisa do gerente na hora,
 * como na sangria.
 */
public class DescontoVendaService {

    private static final int ESCALA = VendaItem.ESCALA_DINHEIRO;
    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    /** Item visto pela repartição. */
    public record Linha(
            long id,
            BigDecimal precoUnitario,
            BigDecimal quantidade
    ) {}

    private final VendaRepository vendaRepository;
    private final VendaItemRepository itemRepository;
    private final LogRepository logRepository;

    public DescontoVendaService(VendaRepository vendaRepository,
                                VendaItemRepository itemRepository,
                                LogRepository logRepository) {
        this.vendaRepository = vendaRepository;
        this.itemRepository = itemRepository;
        this.logRepository = logRepository;
    }

    @Transactional
    public Venda aplicar(Usuario operador,
                          Usuario autorizador,
                          long vendaId,
                          BigDecimal valor,
                          BigDecimal percentual) {

        if (valor != null && percentual != null) {
            throw new ErroNegocioException(
                    "DESCONTO_INVALIDO",
                    "Informe o desconto em reais ou em porcentagem, não os dois.");
        }

        Venda venda = vendaRepository.buscarPorIdComLock(vendaId)
                .orElseThrow(() -> new ErroNegocioException(
                        "VENDA_NAO_ENCONTRADA",
                        "Venda nao encontrada."));

        try {
            venda.exigirRascunho();
        } catch (IllegalStateException e) {
            throw new ErroNegocioException(
                    "VENDA_NAO_ESTA_EM_RASCUNHO",
                    "O desconto só vale antes de finalizar a venda.");
        }

        List<VendaItem> itens =
                itemRepository.listarAtivosPorVenda(vendaId);

        if (itens.isEmpty()) {
            throw new ErroNegocioException(
                    "DESCONTO_INVALIDO",
                    "A venda não tem itens.");
        }

        List<Linha> linhas = itens.stream()
                .map(item -> new Linha(
                        item.getId(),
                        item.getPrecoUnitarioBase()
                                .add(item.getAcrescimoUnitario()),
                        item.getQuantidade()
                ))
                .toList();

        BigDecimal subtotal = subtotal(linhas);
        BigDecimal desconto = calcularDesconto(
                subtotal, valor, percentual);

        Usuario responsavel = responsavel(
                operador, autorizador, desconto);

        Map<Long, BigDecimal> porUnidade =
                distribuir(linhas, desconto);

        for (VendaItem item : itens) {
            BigDecimal descontoUnitario = Objects.requireNonNull(
                    porUnidade.get(item.getId()),
                    "Desconto unitario nao calculado para o item."
            );

            item.aplicarDescontoUnitario(descontoUnitario);
            itemRepository.salvar(item);
        }

        String texto = desconto.signum() == 0
                ? "Desconto retirado da venda " + vendaId + "."
                : "Desconto de R$ "
                        + desconto.setScale(2, RoundingMode.HALF_UP)
                                .toPlainString()
                        + " (de R$ "
                        + subtotal.setScale(2, RoundingMode.HALF_UP)
                                .toPlainString()
                        + ") na venda " + vendaId
                        + " no caixa de " + operador.getNomeCompleto()
                        + (responsavel != operador
                                ? ", autorizado por "
                                        + responsavel.getNomeCompleto()
                                : "")
                        + ".";

        logRepository.salvar(new LogAcao(
                0,
                responsavel.getRu(),
                "VENDA_DESCONTO",
                texto
        ));

        return vendaRepository.buscarPorId(vendaId).orElseThrow();
    }

    static BigDecimal subtotal(List<Linha> linhas) {
        return linhas.stream()
                .map(linha -> linha.precoUnitario()
                        .multiply(linha.quantidade()))
                .reduce(BigDecimal.ZERO,
                        (total, valor) -> total.add(valor))
                .setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal calcularDesconto(BigDecimal subtotal,
                                        BigDecimal valor,
                                        BigDecimal percentual) {

        BigDecimal desconto;

        if (percentual != null) {
            if (percentual.signum() < 0
                    || percentual.compareTo(CEM) >= 0) {
                throw new ErroNegocioException(
                        "DESCONTO_INVALIDO",
                        "A porcentagem precisa ficar entre 0 e 99.");
            }

            desconto = subtotal.multiply(percentual)
                    .divide(CEM, 2, RoundingMode.HALF_UP);
        } else {
            desconto = (valor == null ? BigDecimal.ZERO : valor)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        if (desconto.signum() < 0) {
            throw new ErroNegocioException(
                    "DESCONTO_INVALIDO",
                    "O desconto não pode ser negativo.");
        }

        if (desconto.compareTo(subtotal) >= 0) {
            throw new ErroNegocioException(
                    "DESCONTO_INVALIDO",
                    "O desconto precisa ser menor que o total da venda.");
        }

        return desconto;
    }

    /**
     * Sem desconto, ninguém precisa autorizar.
     * Com desconto, quem cuida do financeiro dá sozinho.
     */
    static Usuario responsavel(Usuario operador,
                               Usuario autorizador,
                               BigDecimal desconto) {

        if (desconto.signum() == 0 || podeDar(operador)) {
            return operador;
        }

        if (autorizador != null
                && autorizador.isAtivo()
                && podeDar(autorizador)) {
            return autorizador;
        }

        throw new SecurityException(autorizador == null
                ? "O desconto precisa da autorização do gerente."
                : "Quem autorizou não pode dar desconto.");
    }

    private static boolean podeDar(Usuario usuario) {
        var classe = usuario == null
                ? null
                : usuario.getClasse();

        return classe != null
                && classe.possuiPermissao(Permissao.VER_FINANCAS);
    }

    /**
     * Distribui o desconto proporcionalmente entre os itens.
     * A sobra do arredondamento é ajustada preferencialmente
     * no item de menor quantidade.
     */
    public static Map<Long, BigDecimal> distribuir(
            List<Linha> linhas,
            BigDecimal desconto) {

        Map<Long, BigDecimal> porUnidade = new LinkedHashMap<>();

        BigDecimal subtotal = linhas.stream()
                .map(linha -> linha.precoUnitario()
                        .multiply(linha.quantidade()))
                .reduce(BigDecimal.ZERO,
                        (total, valor) -> total.add(valor));

        if (desconto.signum() == 0 || subtotal.signum() == 0) {
            linhas.forEach(linha -> porUnidade.put(
                    linha.id(),
                    BigDecimal.ZERO.setScale(ESCALA)
            ));

            return porUnidade;
        }

        BigDecimal repartido = BigDecimal.ZERO;

        for (Linha linha : linhas) {
            BigDecimal parte = desconto
                    .multiply(linha.precoUnitario())
                    .multiply(linha.quantidade())
                    .divide(subtotal, 10, RoundingMode.HALF_UP);

            BigDecimal unidade = parte
                    .divide(
                            linha.quantidade(),
                            ESCALA,
                            RoundingMode.HALF_UP)
                    .min(linha.precoUnitario());

            porUnidade.put(linha.id(), unidade);

            repartido = repartido.add(
                    unidade.multiply(linha.quantidade()));
        }

        BigDecimal sobra = desconto.subtract(repartido);

        if (sobra.signum() != 0) {
            Linha ajuste = linhas.stream()
                    .filter(linha -> {
                        BigDecimal descontoAtual =
                                Objects.requireNonNull(
                                        porUnidade.get(linha.id()),
                                        "Desconto unitario nao calculado.");

                        BigDecimal complemento = sobra.divide(
                                linha.quantidade(),
                                ESCALA,
                                RoundingMode.HALF_UP);

                        return descontoAtual.add(complemento)
                                .compareTo(linha.precoUnitario()) <= 0;
                    })
                    .min(Comparator.comparing(
                            (Linha linha) -> linha.quantidade()))
                    .orElseGet(() -> linhas.get(0));

            BigDecimal descontoAtual = Objects.requireNonNull(
                    porUnidade.get(ajuste.id()),
                    "Desconto unitario nao calculado para ajuste.");

            BigDecimal ajustado = descontoAtual
                    .add(sobra.divide(
                            ajuste.quantidade(),
                            ESCALA,
                            RoundingMode.HALF_UP))
                    .max(BigDecimal.ZERO)
                    .min(ajuste.precoUnitario());

            porUnidade.put(ajuste.id(), ajustado);
        }

        return porUnidade;
    }
}
