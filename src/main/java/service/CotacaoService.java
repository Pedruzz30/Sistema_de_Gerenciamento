package service;

import model.CotacaoMensal;
import model.LogAcao;
import model.Permissao;
import model.Produto;
import model.ResultadoComparacao;
import model.Usuario;
import repository.CotacaoRepository;
import repository.InMemoryCotacaoRepository;
import repository.LogRepository;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Registra e analisa a variação de preços de produtos mês a mês.
 *
 * Fluxo principal:
 *   1. registrarCotacao()  → salva o preço do produto no mês
 *   2. compararMeses()     → compara dois meses e retorna o resultado
 *   3. compararComMesAnterior() → atalho para comparar mês atual com o anterior
 *   4. gerarRelatorioMensal()   → gera comparação de todos os produtos de um mês
 */
public class CotacaoService {

    private final CotacaoRepository cotacaoRepository;
    private final LogRepository logRepository;

    public CotacaoService(CotacaoRepository cotacaoRepository,
                          LogRepository logRepository) {
        this.cotacaoRepository = cotacaoRepository;
        this.logRepository = logRepository;
    }

    // ── Registro ───────────────────────────────────────────────

    /**
     * Registra o preço de compra de um produto em um mês.
     *
     * Se já existir cotação para esse produto+mês, ela é substituída.
     * Isso permite corrigir um preço registrado errado.
     */
    public CotacaoMensal registrarCotacao(Usuario usuarioLogado,
                                          Produto produto,
                                          YearMonth mes,
                                          double precoUnitario,
                                          int quantidadeComprada) {
        validarPermissao(usuarioLogado);

        long id = ((InMemoryCotacaoRepository) cotacaoRepository).proximoId();
        CotacaoMensal cotacao = new CotacaoMensal(
                id, produto, mes, precoUnitario, quantidadeComprada
        );
        cotacaoRepository.salvar(cotacao);

        logRepository.salvar(new LogAcao(
                0,
                usuarioLogado.getRu(),
                "COTACAO_REGISTRADA",
                String.format("Cotação registrada: %s — %s — R$%.2f",
                        produto.getNome(), mes, precoUnitario)
        ));

        return cotacao;
    }

    // ── Comparações ────────────────────────────────────────────

    /**
     * Compara o preço de um produto entre dois meses.
     *
     * Retorna Optional vazio se não houver cotação em um dos meses —
     * assim o chamador decide o que fazer (exibir aviso, pular, etc.)
     */
    public Optional<ResultadoComparacao> compararMeses(Produto produto,
                                                       YearMonth mesAnterior,
                                                       YearMonth mesAtual) {
        Optional<CotacaoMensal> anterior =
                cotacaoRepository.buscarPorProdutoEMes(produto.getId(), mesAnterior);
        Optional<CotacaoMensal> atual =
                cotacaoRepository.buscarPorProdutoEMes(produto.getId(), mesAtual);

        // Se faltar cotação em qualquer um dos meses, não dá para comparar
        if (anterior.isEmpty() || atual.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new ResultadoComparacao(
                produto,
                mesAnterior,
                mesAtual,
                anterior.get().getPrecoUnitario(),
                atual.get().getPrecoUnitario()
        ));
    }

    /**
     * Atalho: compara o mês informado com o mês imediatamente anterior.
     *
     * Exemplo: compararComMesAnterior(morango, fevereiro/2025)
     *   → compara janeiro/2025 com fevereiro/2025 automaticamente
     */
    public Optional<ResultadoComparacao> compararComMesAnterior(Produto produto,
                                                                YearMonth mesAtual) {
        YearMonth mesAnterior = mesAtual.minusMonths(1);
        return compararMeses(produto, mesAnterior, mesAtual);
    }

    // ── Relatório mensal ───────────────────────────────────────

    /**
     * Gera a comparação de todos os produtos de um mês com o mês anterior.
     *
     * Retorna apenas os produtos que têm cotação nos dois meses —
     * produtos sem histórico suficiente são ignorados silenciosamente.
     *
     * Ideal para exibir no dashboard: "o que ficou mais caro esse mês?"
     */
    public List<ResultadoComparacao> gerarRelatorioMensal(List<Produto> produtos,
                                                          YearMonth mes) {
        List<ResultadoComparacao> resultados = new ArrayList<>();

        for (Produto produto : produtos) {
            compararComMesAnterior(produto, mes)
                    .ifPresent(resultados::add);
        }

        // Ordena: os que subiram mais aparecem primeiro
        resultados.sort((a, b) ->
                Double.compare(b.getVariacaoPercentual(), a.getVariacaoPercentual())
        );

        return resultados;
    }

    // ── Histórico ──────────────────────────────────────────────

    /**
     * Retorna o histórico completo de cotações de um produto,
     * do mais antigo ao mais recente.
     */
    public List<CotacaoMensal> buscarHistorico(Produto produto) {
        return cotacaoRepository.buscarHistoricoPorProduto(produto.getId());
    }

    // ── Validação interna ──────────────────────────────────────

    private void validarPermissao(Usuario usuario) {
        if (usuario == null
                || usuario.getClasse() == null
                || !usuario.getClasse().possuiPermissao(Permissao.VER_FINANCAS)) {
            throw new SecurityException(
                    "Você não tem permissão para registrar cotações."
            );
        }
    }
}
