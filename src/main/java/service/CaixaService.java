package service;

import model.Caixa;
import model.FechamentoCaixa;
import model.LogAcao;
import model.MovimentacaoCaixa;
import model.Permissao;
import model.TipoMovimentacaoCaixa;
import model.Usuario;
import repository.CaixaRepository;
import repository.LogRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Gerencia o ciclo de vida dos caixas PDV.
 *
 * Fluxo diário:
 *   1. abrirCaixa()           → operador abre com saldo inicial
 *   2. registrarVenda()       → a cada venda realizada
 *   3. registrarSangria()     → quando precisa retirar dinheiro
 *   4. encerrarCaixa()        → fim do turno, gera FechamentoCaixa
 *   5. consolidarDia()        → soma todos os caixas do dia (mensal)
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

    // ── Abertura ───────────────────────────────────────────────

    public Caixa abrirCaixa(Usuario operador, int numeroCaixa, double saldoInicial) {
        validarPermissao(operador, Permissao.VER_VENDAS);

        // Garante que não tem outro caixa aberto com o mesmo número
        caixaRepository.buscarAbertoporNumero(numeroCaixa).ifPresent(c -> {
            throw new IllegalStateException(
                    "Caixa " + numeroCaixa + " já está aberto pelo operador: "
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
                        + String.format("%.2f", saldoInicial)
        ));

        return caixa;
    }

    // ── Movimentações ──────────────────────────────────────────

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

    // Método interno que faz o trabalho real de todas as movimentações
    private void registrarMovimentacao(Usuario operador, int numeroCaixa,
                                       TipoMovimentacaoCaixa tipo,
                                       double valor, String descricao) {
        Caixa caixa = buscarCaixaAbertoOuLancar(numeroCaixa);

        MovimentacaoCaixa mov = new MovimentacaoCaixa(
                proximoIdMovimentacao.getAndIncrement(), tipo, valor, descricao, operador
        );
        caixa.registrarMovimentacao(mov);
        caixaRepository.salvar(caixa);

        logRepository.salvar(new LogAcao(
                0,
                operador.getRu(),
                "MOVIMENTACAO_CAIXA",
                String.format(
                        "Caixa %d: %s de R$%.2f (%s)",
                        numeroCaixa,
                        tipo.name(),
                        valor,
                        (descricao == null || descricao.isBlank()) ? "sem descrição" : descricao
                )
        ));
    }

    // ── Encerramento ───────────────────────────────────────────

    public FechamentoCaixa encerrarCaixa(Usuario operador, int numeroCaixa) {
        validarPermissao(operador, Permissao.VER_FINANCAS);

        Caixa caixa = buscarCaixaAbertoOuLancar(numeroCaixa);
        caixa.encerrar();
        caixaRepository.salvar(caixa);

        FechamentoCaixa fechamento = new FechamentoCaixa(caixa);

        logRepository.salvar(new LogAcao(
                0, operador.getRu(),
                "CAIXA_ENCERRADO",
                String.format("Caixa %d encerrado. Vendas: R$%.2f | Saldo final: R$%.2f",
                        numeroCaixa,
                        fechamento.getTotalVendas(),
                        fechamento.getSaldoFinal())
        ));

        return fechamento;
    }

    // ── Consolidação mensal ────────────────────────────────────

    /**
     * Consolida todos os caixas encerrados em uma data.
     *
     * Retorna um resumo com a soma de vendas, entradas e saídas
     * de todos os PDVs juntos — para o relatório mensal.
     */
    public String consolidarDia(LocalDate data) {
        List<Caixa> encerrados = caixaRepository.buscarEncerradosPorData(data);

        if (encerrados.isEmpty()) {
            return "Nenhum caixa encerrado em " + data + ".";
        }

        double totalVendas   = encerrados.stream().mapToDouble(Caixa::calcularTotalVendas).sum();
        double totalEntradas = encerrados.stream().mapToDouble(Caixa::calcularTotalEntradas).sum();
        double totalSaidas   = encerrados.stream().mapToDouble(Caixa::calcularTotalSaidas).sum();
        double saldoTotal    = encerrados.stream().mapToDouble(Caixa::getSaldoAtual).sum();

        String detalhesCaixas = encerrados.stream()
                .map(c -> String.format("  Caixa %d: vendas R$%.2f | saldo R$%.2f",
                        c.getNumeroCaixa(),
                        c.calcularTotalVendas(),
                        c.getSaldoAtual()))
                .collect(Collectors.joining("\n"));

        return String.format("""
                ══ CONSOLIDADO DO DIA %s ══
                %s
                ──────────────────────────────
                Total vendas:    R$%.2f
                Total entradas:  R$%.2f
                Total saídas:    R$%.2f
                Saldo total:     R$%.2f
                Caixas fechados: %d
                """,
                data, detalhesCaixas,
                totalVendas, totalEntradas, totalSaidas, saldoTotal,
                encerrados.size());
    }

    // ── Consultas ──────────────────────────────────────────────

    public Caixa buscarCaixaAberto(int numeroCaixa) {
        return buscarCaixaAbertoOuLancar(numeroCaixa);
    }

    // ── Utilitários internos ───────────────────────────────────

    private Caixa buscarCaixaAbertoOuLancar(int numeroCaixa) {
        return caixaRepository.buscarAbertoporNumero(numeroCaixa)
                .orElseThrow(() -> new IllegalStateException(
                        "Caixa " + numeroCaixa + " não está aberto."
                ));
    }

    private void validarPermissao(Usuario usuario, Permissao permissao) {
        if (usuario == null
                || usuario.getClasse() == null
                || !usuario.getClasse().possuiPermissao(permissao)) {
            throw new SecurityException(
                    "Você não tem permissão para esta operação."
            );
        }
    }
}
