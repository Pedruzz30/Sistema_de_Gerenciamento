package service;

import model.FormaPagamento;
import model.Usuario;
import model.Venda;
import model.VendaPagamento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import repository.VendaPagamentoRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Liga as vendas do PDV ({@code /api/vendas}) à sessão de caixa.
 *
 * <ul>
 *   <li>Na finalização: valida e grava os pagamentos da venda e lança no
 *       caixa uma movimentação por forma de pagamento. Só o dinheiro entra no
 *       saldo da gaveta.</li>
 *   <li>No cancelamento: lança {@code ESTORNO_VENDA} na forma em que o
 *       cliente pagou (antes era lançado como SANGRIA).</li>
 * </ul>
 */
public class IntegracaoCaixaLegadoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IntegracaoCaixaLegadoService.class);
    private static final int ESCALA = 2;

    private final CaixaService caixaService;
    private final VendaPagamentoRepository vendaPagamentoRepository;

    /** Dados de um pagamento informado pelo PDV. */
    public record PagamentoInput(FormaPagamento forma, BigDecimal valor, BigDecimal valorRecebido, String nsu) {}

    public IntegracaoCaixaLegadoService(CaixaService caixaService) {
        this(caixaService, null);
    }

    public IntegracaoCaixaLegadoService(CaixaService caixaService, VendaPagamentoRepository vendaPagamentoRepository) {
        this.caixaService = caixaService;
        this.vendaPagamentoRepository = vendaPagamentoRepository;
    }

    /**
     * Valida os pagamentos contra o total, grava-os e lança a venda no caixa.
     *
     * <p>Sem pagamentos informados (clientes antigos da API), a venda inteira
     * é tratada como dinheiro, como era antes.
     *
     * @return os pagamentos validados (valor de cada um já sem o troco)
     */
    @Transactional
    public List<PagamentoInput> registrarPagamentosEVenda(Usuario usuario,
                                                          Venda venda,
                                                          BigDecimal valorTotal,
                                                          Integer numeroCaixa,
                                                          List<PagamentoInput> pagamentosInformados) {
        BigDecimal total = valorTotal.setScale(ESCALA, RoundingMode.HALF_UP);
        List<PagamentoInput> pagamentos = validarPagamentos(total, pagamentosInformados);

        if (vendaPagamentoRepository != null) {
            for (PagamentoInput pagamento : pagamentos) {
                vendaPagamentoRepository.salvar(new VendaPagamento(
                        venda, pagamento.forma(), pagamento.valor(), pagamento.valorRecebido(), pagamento.nsu()));
            }
        }

        if (numeroCaixa != null && total.compareTo(BigDecimal.ZERO) > 0) {
            caixaService.registrarVendaPdv(usuario, numeroCaixa, venda.getId(), somarPorForma(pagamentos));
        }
        return pagamentos;
    }

    /** Compatibilidade: venda inteira em dinheiro. */
    @Transactional
    public void registrarVendaConsolidada(Usuario usuario,
                                          Integer numeroCaixa,
                                          long vendaId,
                                          BigDecimal valorTotal) {
        if (numeroCaixa == null) {
            return;
        }
        Map<FormaPagamento, BigDecimal> dinheiro = new EnumMap<>(FormaPagamento.class);
        dinheiro.put(FormaPagamento.DINHEIRO, valorTotal);
        caixaService.registrarVendaPdv(usuario, numeroCaixa, vendaId, dinheiro);
    }

    @Transactional
    public void registrarCancelamentoConsolidado(Usuario usuario,
                                                 Integer numeroCaixa,
                                                 long vendaId,
                                                 BigDecimal valorTotal) {
        registrarCancelamentoConsolidado(usuario, numeroCaixa, vendaId, valorTotal, null, null);
    }

    /**
     * Lança o estorno de uma venda cancelada (total ou parcial) no caixa.
     *
     * <p>Com {@code formaEstorno} informada, tudo é devolvido nela. Sem ela, o
     * valor é distribuído pelas formas em que a venda foi paga: primeiro as
     * eletrônicas (Pix, cartões), depois o dinheiro, até o valor pago em cada
     * uma. Vendas sem pagamentos gravados são tratadas como dinheiro.
     *
     * <p>Se o caixa da venda já foi encerrado, estornos eletrônicos não mexem
     * em caixa nenhum (o dinheiro não passa pela gaveta); estorno em dinheiro
     * exige um caixa aberto com o mesmo número.
     */
    @Transactional
    public void registrarCancelamentoConsolidado(Usuario usuario,
                                                 Integer numeroCaixa,
                                                 long vendaId,
                                                 BigDecimal valorTotal,
                                                 FormaPagamento formaEstorno,
                                                 String motivo) {
        if (numeroCaixa == null || valorTotal == null || valorTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Map<FormaPagamento, BigDecimal> alocacao = alocarEstorno(vendaId, valorTotal, formaEstorno);
        boolean caixaAberto = caixaEstaAberto(numeroCaixa);
        for (Map.Entry<FormaPagamento, BigDecimal> entry : alocacao.entrySet()) {
            if (!caixaAberto) {
                if (entry.getKey().afetaGaveta()) {
                    throw new IllegalStateException("Caixa " + numeroCaixa
                            + " nao esta aberto. Abra o caixa para devolver R$ "
                            + entry.getValue().toPlainString() + " em dinheiro ao cliente.");
                }
                LOGGER.info("Estorno eletronico sem caixa aberto. venda={} forma={} valor={}",
                        vendaId, entry.getKey(), entry.getValue());
                continue;
            }
            caixaService.registrarEstornoVenda(usuario, numeroCaixa, vendaId, entry.getKey(), entry.getValue(), motivo);
        }
    }

    // ── Regras internas ───────────────────────────────────────────────────────

    static List<PagamentoInput> validarPagamentos(BigDecimal total, List<PagamentoInput> informados) {
        if (informados == null || informados.isEmpty()) {
            return List.of(new PagamentoInput(FormaPagamento.DINHEIRO, total, null, null));
        }
        List<PagamentoInput> normalizados = new ArrayList<>();
        BigDecimal soma = BigDecimal.ZERO.setScale(ESCALA, RoundingMode.HALF_UP);
        for (PagamentoInput p : informados) {
            if (p == null || p.forma() == null) {
                throw new ErroNegocioException("PAGAMENTO_INVALIDO", "Informe a forma de cada pagamento.");
            }
            if (p.valor() == null || p.valor().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ErroNegocioException("PAGAMENTO_INVALIDO", "Valor do pagamento deve ser maior que zero.");
            }
            BigDecimal valor = p.valor().setScale(ESCALA, RoundingMode.HALF_UP);
            BigDecimal recebido = p.valorRecebido() == null ? null : p.valorRecebido().setScale(ESCALA, RoundingMode.HALF_UP);
            if (recebido != null && recebido.compareTo(valor) < 0) {
                throw new ErroNegocioException("PAGAMENTO_INVALIDO",
                        "Valor recebido em " + p.forma().getLabel() + " e menor que o valor do pagamento.");
            }
            if (recebido != null && recebido.compareTo(valor) > 0 && p.forma() != FormaPagamento.DINHEIRO) {
                throw new ErroNegocioException("PAGAMENTO_INVALIDO", "Troco so e permitido em dinheiro.");
            }
            normalizados.add(new PagamentoInput(p.forma(), valor, recebido, p.nsu()));
            soma = soma.add(valor);
        }
        if (soma.compareTo(total) != 0) {
            throw new ErroNegocioException(
                    "PAGAMENTO_DIVERGENTE",
                    "A soma dos pagamentos (R$ " + soma.toPlainString()
                            + ") precisa ser igual ao total da venda (R$ " + total.toPlainString() + ").",
                    Map.<String, Object>of("totalVenda", total, "totalPagamentos", soma)
            );
        }
        return normalizados;
    }

    private static Map<FormaPagamento, BigDecimal> somarPorForma(List<PagamentoInput> pagamentos) {
        Map<FormaPagamento, BigDecimal> totais = new EnumMap<>(FormaPagamento.class);
        for (PagamentoInput p : pagamentos) {
            totais.merge(p.forma(), p.valor(), BigDecimal::add);
        }
        return totais;
    }

    private Map<FormaPagamento, BigDecimal> alocarEstorno(long vendaId, BigDecimal valor, FormaPagamento formaEstorno) {
        BigDecimal restante = valor.setScale(ESCALA, RoundingMode.HALF_UP);
        Map<FormaPagamento, BigDecimal> alocacao = new EnumMap<>(FormaPagamento.class);
        if (formaEstorno != null) {
            alocacao.put(formaEstorno, restante);
            return alocacao;
        }
        List<VendaPagamento> pagos = vendaPagamentoRepository == null
                ? List.of()
                : vendaPagamentoRepository.listarPorVenda(vendaId);
        if (pagos.isEmpty()) {
            alocacao.put(FormaPagamento.DINHEIRO, restante);
            return alocacao;
        }
        Map<FormaPagamento, BigDecimal> pagoPorForma = new EnumMap<>(FormaPagamento.class);
        for (VendaPagamento p : pagos) {
            pagoPorForma.merge(p.getForma(), p.getValor(), BigDecimal::add);
        }
        // Eletrônicas primeiro, dinheiro por último.
        List<FormaPagamento> ordem = new ArrayList<>(pagoPorForma.keySet());
        ordem.sort((a, b) -> Boolean.compare(a.afetaGaveta(), b.afetaGaveta()));
        for (FormaPagamento forma : ordem) {
            if (restante.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal parte = restante.min(pagoPorForma.get(forma));
            alocacao.merge(forma, parte, BigDecimal::add);
            restante = restante.subtract(parte);
        }
        if (restante.compareTo(BigDecimal.ZERO) > 0) {
            // Mais estorno que pagamento registrado (não deveria acontecer): vai na última forma.
            FormaPagamento ultima = ordem.get(ordem.size() - 1);
            alocacao.merge(ultima, restante, BigDecimal::add);
        }
        return alocacao;
    }

    private boolean caixaEstaAberto(int numeroCaixa) {
        try {
            caixaService.buscarCaixaAberto(numeroCaixa);
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }
}
