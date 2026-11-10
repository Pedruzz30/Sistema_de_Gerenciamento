package service;

import model.LogAcao;
import model.Usuario;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import repository.LogRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Teclas rápidas do caixa, para o que não tem código de barras: gelo, copo, caldo, porção.
 * São no máximo oito, uma variação em cada, e valem para todos os caixas da loja.
 */
public class TeclasRapidasService {

    public static final int TOTAL = 8;

    /** Mesmo formato da leitura de código, para o caixa lançar do mesmo jeito. */
    public record Tecla(int posicao, long variacaoId, String variacaoNome, int itemCardapioId, String itemNome,
                        BigDecimal precoVenda) {}

    private static final String CAMPOS = "v.id, v.nome, v.preco_venda, i.id as item_id, i.nome as item_nome";

    private final JdbcTemplate jdbc;
    private final LogRepository logRepository;

    public TeclasRapidasService(JdbcTemplate jdbc, LogRepository logRepository) {
        this.jdbc = jdbc;
        this.logRepository = logRepository;
    }

    @Transactional(readOnly = true)
    public List<Tecla> listar() {
        return jdbc.query("select t.posicao, " + CAMPOS + " from pdv_teclas_rapidas t"
                        + " join variacoes_item_cardapio v on v.id = t.variacao_id"
                        + " join itens_cardapio i on i.id = v.item_cardapio_id"
                        + " where v.ativa and i.ativo order by t.posicao",
                (rs, n) -> new Tecla(rs.getInt("posicao"), rs.getLong("id"), rs.getString("nome"),
                        rs.getInt("item_id"), rs.getString("item_nome"), rs.getBigDecimal("preco_venda")));
    }

    /** O que pode ir numa tecla: tudo o que está ativo no cardápio. */
    @Transactional(readOnly = true)
    public List<Tecla> opcoes() {
        return jdbc.query("select " + CAMPOS + " from variacoes_item_cardapio v"
                        + " join itens_cardapio i on i.id = v.item_cardapio_id"
                        + " where v.ativa and i.ativo order by i.nome, v.ordem_exibicao, v.nome",
                (rs, n) -> new Tecla(0, rs.getLong("id"), rs.getString("nome"),
                        rs.getInt("item_id"), rs.getString("item_nome"), rs.getBigDecimal("preco_venda")));
    }

    /** Troca todas as teclas de uma vez. Posição sem variação fica vazia. */
    @Transactional
    public List<Tecla> salvar(Usuario responsavel, Map<Integer, Long> teclas) {
        Map<Integer, Long> ordenadas = new TreeMap<>(teclas == null ? Map.of() : teclas);
        for (Map.Entry<Integer, Long> tecla : ordenadas.entrySet()) {
            if (tecla.getKey() == null || tecla.getKey() < 1 || tecla.getKey() > TOTAL) {
                throw new ErroNegocioException("TECLA_INVALIDA", "As teclas vão de 1 a " + TOTAL + ".");
            }
            Integer existe = jdbc.queryForObject(
                    "select count(*) from variacoes_item_cardapio v join itens_cardapio i on i.id = v.item_cardapio_id"
                            + " where v.id = ? and v.ativa and i.ativo", Integer.class, tecla.getValue());
            if (existe == null || existe == 0) {
                throw new ErroNegocioException("TECLA_PRODUTO_INVALIDO", "O produto da tecla " + tecla.getKey() + " não está ativo no cardápio.");
            }
        }
        jdbc.update("delete from pdv_teclas_rapidas");
        ordenadas.forEach((posicao, variacao) ->
                jdbc.update("insert into pdv_teclas_rapidas (posicao, variacao_id) values (?, ?)", posicao, variacao));
        List<Tecla> resultado = listar();
        String descricao = resultado.isEmpty() ? "Teclas rápidas do caixa apagadas."
                : "Teclas rápidas do caixa: " + resultado.stream()
                        .map(t -> "Alt+" + t.posicao() + " " + t.itemNome() + " " + t.variacaoNome())
                        .collect(Collectors.joining(", ")) + ".";
        logRepository.salvar(new LogAcao(0, responsavel == null ? null : responsavel.getRu(), "PDV_TECLAS_RAPIDAS", descricao));
        return resultado;
    }
}
