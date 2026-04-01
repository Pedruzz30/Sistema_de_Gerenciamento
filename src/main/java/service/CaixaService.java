package service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import model.Caixa;
import model.FechamentoCaixa;
import model.LogAcao;
import model.MovimentacaoCaixa;
import model.Permissao;
import model.TipoMovimentacaoCaixa;
import model.Usuario;
import repository.CaixaRepository;
import repository.LogRepository;

/**
 * Gerencia o ciclo de vida dos caixas PDV.
 */
public class CaixaService {

    private final CaixaRepository caixaRepository;
    private final LogRepository logRepository;
    private final AtomicLong proximoIdMovimentacao = new AtomicLong(1);

    public CaixaService(CaixaRepository caixaRepository,
                        LogRepository logRepository) {
        this.caixaRepository = caixaRepository;
        this.logRepository = logRepository;
    }

    public Caixa abrirCaixa(Usuario operador, int numeroCaixa, double saldoInicial) {
        validarPermissao(operador, Permissao.VER_VENDAS);

        caixaRepository.buscarAbertoporNumero(numeroCaixa).ifPresent(c -> {
            throw new IllegalStateException(
                    "Caixa " + numeroCaixa + " ja esta aberto pelo operador: "
                            + c.getOperadorAtual().getNomeCompleto()
            );
        });

        long id = caixaRepository.proximoId();
        Caixa caixa = new Caixa(id, numeroCaixa);
        caixa.abrir(operador, saldoInicial);
        caixaRepository.salvar(caixa);

        logRepository.salvar(new LogAcao(
                0, operador.getRu(),
                "CAIXA_ABERTO",
                "Caixa " + numeroCaixa + " aberto com saldo inicial R$"
                        + formatarMoeda(BigDecimal.valueOf(saldoInicial))
        ));

        return caixa;
    }

    public void registrarVenda(Usuario operador, int numeroCaixa,
                               double valor, String descricao) {
        validarPermissao(operador, Permissao.VER_VENDAS);
        registrarMovimentacao(operador, numeroCaixa,
                TipoMovimentacaoCaixa.VENDA, valor, descricao);
    }

    public void registrarSangria(Usuario operador, int numeroCaixa,
                                 double valor, String descricao) {
        validarPermissao(operador, Permissao.VER_FINANCAS);
        registrarMovimentacao(operador, numeroCaixa,
                TipoMovimentacaoCaixa.SANGRIA, valor, descricao);
    }

    public void registrarSuprimento(Usuario operador, int numeroCaixa,
                                    double valor, String descricao) {
        validarPermissao(operador, Permissao.VER_FINANCAS);
        registrarMovimentacao(operador, numeroCaixa,
                TipoMovimentacaoCaixa.SUPRIMENTO, valor, descricao);
    }

    private void registrarMovimentacao(Usuario operador, int numeroCaixa,
                                       TipoMovimentacaoCaixa tipo,
                                       double valor, String descricao) {
        Caixa caixa = buscarCaixaAbertoOuLancar(numeroCaixa);

        MovimentacaoCaixa mov = new MovimentacaoCaixa(
                proximoIdMovimentacao.getAndIncrement(),
                tipo,
                BigDecimal.valueOf(valor),
                descricao,
                operador
        );
        caixa.registrarMovimentacao(mov);
        caixaRepository.salvar(caixa);

        logRepository.salvar(new LogAcao(
                0,
                operador.getRu(),
                "MOVIMENTACAO_CAIXA",
                String.format(
                        "Caixa %d: %s de R$%s (%s)",
                        numeroCaixa,
                        tipo.name(),
                        formatarMoeda(BigDecimal.valueOf(valor)),
                        (descricao == null || descricao.isBlank()) ? "sem descricao" : descricao
                )
        ));
    }

    public FechamentoCaixa encerrarCaixa(Usuario operador, int numeroCaixa) {
        validarPermissao(operador, Permissao.VER_FINANCAS);

        Caixa caixa = buscarCaixaAbertoOuLancar(numeroCaixa);
        caixa.encerrar();
        caixaRepository.salvar(caixa);

        FechamentoCaixa fechamento = new FechamentoCaixa(caixa);

        logRepository.salvar(new LogAcao(
                0, operador.getRu(),
                "CAIXA_ENCERRADO",
                String.format("Caixa %d encerrado. Vendas: R$%s | Saldo final: R$%s",
                        numeroCaixa,
                        formatarMoeda(fechamento.getTotalVendas()),
                        formatarMoeda(fechamento.getSaldoFinal()))
        ));

        return fechamento;
    }

    public String consolidarDia(LocalDate data) {
        List<Caixa> encerrados = caixaRepository.buscarEncerradosPorData(data);

        if (encerrados.isEmpty()) {
            return "Nenhum caixa encerrado em " + data + ".";
        }

        BigDecimal totalVendas = somar(encerrados.stream().map(Caixa::calcularTotalVendas).toList());
        BigDecimal totalEntradas = somar(encerrados.stream().map(Caixa::calcularTotalEntradas).toList());
        BigDecimal totalSaidas = somar(encerrados.stream().map(Caixa::calcularTotalSaidas).toList());
        BigDecimal saldoTotal = somar(encerrados.stream().map(Caixa::getSaldoAtual).toList());

        String detalhesCaixas = encerrados.stream()
                .map(c -> String.format("  Caixa %d: vendas R$%s | saldo R$%s",
                        c.getNumeroCaixa(),
                        formatarMoeda(c.calcularTotalVendas()),
                        formatarMoeda(c.getSaldoAtual())))
                .collect(Collectors.joining("\n"));

        return String.format("""
                CONSOLIDADO DO DIA %s
                %s
                ------------------------------
                Total vendas:    R$%s
                Total entradas:  R$%s
                Total saidas:    R$%s
                Saldo total:     R$%s
                Caixas fechados: %d
                """,
                data,
                detalhesCaixas,
                formatarMoeda(totalVendas),
                formatarMoeda(totalEntradas),
                formatarMoeda(totalSaidas),
                formatarMoeda(saldoTotal),
                encerrados.size());
    }

    public Caixa buscarCaixaAberto(int numeroCaixa) {
        return buscarCaixaAbertoOuLancar(numeroCaixa);
    }

    private Caixa buscarCaixaAbertoOuLancar(int numeroCaixa) {
        return caixaRepository.buscarAbertoporNumero(numeroCaixa)
                .orElseThrow(() -> new IllegalStateException(
                        "Caixa " + numeroCaixa + " nao esta aberto."
                ));
    }

    private void validarPermissao(Usuario usuario, Permissao permissao) {
        if (usuario == null
                || usuario.getClasse() == null
                || !usuario.getClasse().possuiPermissao(permissao)) {
            throw new SecurityException(
                    "Voce nao tem permissao para esta operacao."
            );
        }
    }

    private BigDecimal somar(List<BigDecimal> valores) {
        return valores.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String formatarMoeda(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
