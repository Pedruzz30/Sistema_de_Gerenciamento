package service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import model.CotacaoMensal;
import model.LogAcao;
import model.Permissao;
import model.Produto;
import model.ResultadoComparacao;
import model.Usuario;
import repository.CotacaoRepository;
import repository.LogRepository;

/**
 * Registra e analisa a variacao de precos de produtos mes a mes.
 */
public class CotacaoService {

    private final CotacaoRepository cotacaoRepository;
    private final LogRepository logRepository;

    public CotacaoService(CotacaoRepository cotacaoRepository,
                          LogRepository logRepository) {
        this.cotacaoRepository = cotacaoRepository;
        this.logRepository = logRepository;
    }

    public CotacaoMensal registrarCotacao(Usuario usuarioLogado,
                                          Produto produto,
                                          YearMonth mes,
                                          BigDecimal precoUnitario,
                                          int quantidadeComprada) {
        validarPermissao(usuarioLogado);

        long id = cotacaoRepository.proximoId();
        CotacaoMensal cotacao = new CotacaoMensal(
                id, produto, mes, precoUnitario, quantidadeComprada
        );
        cotacaoRepository.salvar(cotacao);

        logRepository.salvar(new LogAcao(
                0,
                usuarioLogado.getRu(),
                "COTACAO_REGISTRADA",
                String.format("Cotacao registrada: %s - %s - R$%s",
                        produto.getNome(), mes, precoUnitario.toPlainString())
        ));

        return cotacao;
    }

    public Optional<ResultadoComparacao> compararMeses(Produto produto,
                                                       YearMonth mesAnterior,
                                                       YearMonth mesAtual) {
        Optional<CotacaoMensal> anterior =
                cotacaoRepository.buscarPorProdutoEMes(produto.getId(), mesAnterior);
        Optional<CotacaoMensal> atual =
                cotacaoRepository.buscarPorProdutoEMes(produto.getId(), mesAtual);

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

    public Optional<ResultadoComparacao> compararComMesAnterior(Produto produto,
                                                                YearMonth mesAtual) {
        YearMonth mesAnterior = mesAtual.minusMonths(1);
        return compararMeses(produto, mesAnterior, mesAtual);
    }

    public List<ResultadoComparacao> gerarRelatorioMensal(List<Produto> produtos,
                                                          YearMonth mes) {
        List<ResultadoComparacao> resultados = new ArrayList<>();

        for (Produto produto : produtos) {
            compararComMesAnterior(produto, mes).ifPresent(resultados::add);
        }

        resultados.sort((a, b) -> b.getVariacaoPercentual().compareTo(a.getVariacaoPercentual()));
        return resultados;
    }

    public List<CotacaoMensal> buscarHistorico(Produto produto) {
        return cotacaoRepository.buscarHistoricoPorProduto(produto.getId());
    }

    private void validarPermissao(Usuario usuario) {
        if (usuario == null
                || usuario.getClasse() == null
                || !usuario.getClasse().possuiPermissao(Permissao.VER_FINANCAS)) {
            throw new SecurityException(
                    "Voce nao tem permissao para registrar cotacoes."
            );
        }
    }
}
