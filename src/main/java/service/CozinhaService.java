package service;

import model.LogAcao;
import model.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import repository.LogRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Pedido impresso para a cozinha.
 *
 * <p>Quando a comanda é lançada (pelo celular ou guardada no caixa) ou a venda é paga, o que é
 * da cozinha e ainda não foi mandado vira um pedido. Ele fica gravado e uma tarefa imprime em
 * seguida; se a impressora estiver desligada, tenta de novo sozinha. Quando um item já
 * mandado sai da comanda, vai um aviso de cancelamento.
 *
 * <p>O que é da cozinha: as categorias escolhidas na configuração. Sem escolha, os itens
 * preparados na hora (tipo do cardápio "preparado sob demanda").
 */
public class CozinhaService {

    private static final Logger log = LoggerFactory.getLogger(CozinhaService.class);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private static final Set<String> VARIACOES_GENERICAS = Set.of("padrao", "padrão", "unidade", "unica", "única", "normal");

    public record Config(String impressora, int colunas, List<String> categorias) {}

    public record Situacao(boolean ligada, String impressora, int aguardando, int comErro) {}

    public record Pedido(long id, String tipo, Integer comanda, Long vendaId, LocalDateTime criadoEm,
                         LocalDateTime impressoEm, int tentativas, String erro, String texto) {}

    /** Linha do pedido: o item, quanto vai (negativo no cancelamento) e os detalhes. */
    public record Linha(long itemId, String descricao, BigDecimal quantidade, List<String> detalhes) {}

    private record ItemVenda(long id, BigDecimal quantidade, String nome, String variacao, String observacao,
                             String categoria, String tipo) {}

    private final JdbcTemplate jdbc;
    private final LogRepository logRepository;
    private final Clock clock;
    private final ImpressoraCozinha impressora;
    private final Path pastaTeste;

    public CozinhaService(JdbcTemplate jdbc, LogRepository logRepository, Clock clock,
                          ImpressoraCozinha impressora, Path pastaTeste) {
        this.jdbc = jdbc;
        this.logRepository = logRepository;
        this.clock = clock;
        this.impressora = impressora;
        this.pastaTeste = pastaTeste;
    }

    // ── Configuração ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Config config() {
        Map<String, String> valores = new HashMap<>();
        jdbc.query("select chave, valor from configuracoes where chave like 'cozinha.%'",
                rs -> { valores.put(rs.getString(1), rs.getString(2)); });
        int colunas = 48;
        try {
            colunas = Integer.parseInt(valores.getOrDefault("cozinha.colunas", "48"));
        } catch (NumberFormatException ignorada) {
            // Fica o padrão de 80 mm.
        }
        String categorias = valores.getOrDefault("cozinha.categorias", "");
        return new Config(valores.getOrDefault("cozinha.impressora", ""), colunas,
                categorias.isBlank() ? List.of() : Arrays.stream(categorias.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
    }

    @Transactional
    public Config salvarConfig(Usuario responsavel, Config nova) {
        int colunas = nova.colunas() <= 0 ? 48 : Math.max(24, Math.min(64, nova.colunas()));
        String impressoraEscolhida = nova.impressora() == null ? "" : nova.impressora().trim();
        String categorias = String.join(",", nova.categorias() == null ? List.of()
                : nova.categorias().stream().map(c -> c.trim().toLowerCase(Locale.ROOT)).filter(c -> !c.isEmpty()).distinct().toList());
        gravar("cozinha.impressora", impressoraEscolhida);
        gravar("cozinha.colunas", String.valueOf(colunas));
        gravar("cozinha.categorias", categorias);
        logRepository.salvar(new LogAcao(0, responsavel == null ? null : responsavel.getRu(), "COZINHA_CONFIGURADA",
                "Cozinha: impressora " + (impressoraEscolhida.isEmpty() ? "desligada" : "\"" + impressoraEscolhida + "\"")
                        + ", " + colunas + " colunas, categorias: " + (categorias.isEmpty() ? "itens preparados" : categorias) + "."));
        return config();
    }

    private void gravar(String chave, String valor) {
        jdbc.update("insert into configuracoes (chave, valor) values (?, ?) on conflict (chave) do update set valor = excluded.valor",
                chave, valor);
    }

    public boolean ligada() {
        return pastaTeste != null || !config().impressora().isBlank();
    }

    public List<String> impressoras() {
        try {
            return impressora.impressoras();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    // ── Mandar para a cozinha ───────────────────────────────────────────

    /** Manda o que é da cozinha e ainda não foi mandado. Sem nada novo, não imprime. */
    @Transactional
    public Optional<Long> enviar(Usuario usuario, long vendaId) {
        if (!ligada()) return Optional.empty();
        Config config = config();
        List<ItemVenda> itens = itensDaVenda(vendaId).stream()
                .filter(i -> vaiParaCozinha(i.categoria(), i.tipo(), config.categorias()))
                .toList();
        if (itens.isEmpty()) return Optional.empty();
        Map<Long, List<String>> adicionais = adicionais(itens.stream().map(ItemVenda::id).toList());
        List<Linha> linhas = new ArrayList<>();
        for (ItemVenda item : itens) {
            BigDecimal falta = item.quantidade().subtract(enviado(item.id()));
            if (falta.signum() <= 0) continue;
            List<String> detalhes = new ArrayList<>(adicionais.getOrDefault(item.id(), List.of()));
            if (item.observacao() != null && !item.observacao().isBlank()) detalhes.add("obs: " + item.observacao().trim());
            linhas.add(new Linha(item.id(), descricao(item.nome(), item.variacao()), falta, detalhes));
        }
        if (linhas.isEmpty()) return Optional.empty();
        return Optional.of(gravarPedido("PEDIDO", vendaId, usuario, linhas, config.colunas()));
    }

    /** Depois de tirar itens da comanda: o que já tinha ido e saiu vira aviso de cancelamento. */
    @Transactional
    public Optional<Long> cancelarExcedente(Usuario usuario, long vendaId) {
        if (!ligada()) return Optional.empty();
        Set<Long> candidatos = new java.util.LinkedHashSet<>();
        jdbc.query("select id from venda_itens where venda_id = ?", rs -> { candidatos.add(rs.getLong(1)); }, vendaId);
        jdbc.query("select distinct pi.venda_item_id from pedidos_cozinha_itens pi join pedidos_cozinha p on p.id = pi.pedido_id where p.venda_id = ?",
                rs -> { candidatos.add(rs.getLong(1)); }, vendaId);
        List<Linha> linhas = new ArrayList<>();
        for (long itemId : candidatos) {
            BigDecimal enviado = enviado(itemId);
            if (enviado.signum() <= 0) continue;
            BigDecimal atual = jdbc.query("select quantidade from venda_itens where id = ? and status = 'ATIVO'",
                    rs -> rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO, itemId);
            BigDecimal sobra = enviado.subtract(atual == null ? BigDecimal.ZERO : atual);
            if (sobra.signum() <= 0) continue;
            String descricao = jdbc.query("select descricao from pedidos_cozinha_itens where venda_item_id = ? order by id desc limit 1",
                    rs -> rs.next() ? rs.getString(1) : "Item", itemId);
            linhas.add(new Linha(itemId, descricao, sobra.negate(), List.of()));
        }
        if (linhas.isEmpty()) return Optional.empty();
        return Optional.of(gravarPedido("CANCELAMENTO", vendaId, usuario, linhas, config().colunas()));
    }

    /** Parte de um item mudou de comanda: o que já tinha sido impresso vai junto. */
    @Transactional
    public void moverEnviado(long deItemId, long paraItemId, BigDecimal quantidade) {
        BigDecimal enviado = enviado(deItemId);
        if (enviado.signum() <= 0 || quantidade == null || quantidade.signum() <= 0) return;
        BigDecimal mover = enviado.min(quantidade);
        String descricao = jdbc.query("select descricao from pedidos_cozinha_itens where venda_item_id = ? order by id desc limit 1",
                rs -> rs.next() ? rs.getString(1) : "Item", deItemId);
        jdbc.update("insert into pedidos_cozinha_itens (pedido_id, venda_item_id, descricao, quantidade) values (null, ?, ?, ?)",
                deItemId, descricao, mover.negate());
        jdbc.update("insert into pedidos_cozinha_itens (pedido_id, venda_item_id, descricao, quantidade) values (null, ?, ?, ?)",
                paraItemId, descricao, mover);
    }

    @Transactional
    public long testar(Usuario usuario) {
        String texto = "## TESTE\nImpressora da cozinha funcionando.\n" + LocalDateTime.now(clock).format(HORA)
                + " - " + nomeDe(usuario) + "\n";
        return inserirPedido("TESTE", null, null, texto, usuario);
    }

    private long gravarPedido(String tipo, long vendaId, Usuario usuario, List<Linha> linhas, int colunas) {
        Integer comanda = jdbc.query("select numero from comandas where venda_id = ? order by id desc limit 1",
                rs -> rs.next() ? rs.getInt(1) : null, vendaId);
        String texto = montarTexto(tipo, comanda, vendaId, LocalDateTime.now(clock), nomeDe(usuario), linhas, colunas);
        long id = inserirPedido(tipo, vendaId, comanda, texto, usuario);
        for (Linha linha : linhas) {
            jdbc.update("insert into pedidos_cozinha_itens (pedido_id, venda_item_id, descricao, quantidade) values (?, ?, ?, ?)",
                    id, linha.itemId(), cortar(linha.descricao(), 300), linha.quantidade());
        }
        return id;
    }

    private long inserirPedido(String tipo, Long vendaId, Integer comanda, String texto, Usuario usuario) {
        return jdbc.queryForObject("insert into pedidos_cozinha (venda_id, comanda_numero, tipo, texto, criado_em, criado_por)"
                        + " values (?, ?, ?, ?, ?, ?) returning id", Long.class,
                vendaId, comanda, tipo, texto, Timestamp.valueOf(LocalDateTime.now(clock)), usuario == null ? null : usuario.getRu());
    }

    // ── Imprimir ────────────────────────────────────────────────────────

    /** Imprime o que está esperando. Impressora desligada: tenta de novo, cada vez mais espaçado. */
    @Scheduled(initialDelayString = "PT5S", fixedDelayString = "PT2S")
    public void imprimirPendentes() {
        if (!ligada()) return;
        Config config = config();
        LocalDateTime agora = LocalDateTime.now(clock);
        List<Object[]> pendentes = jdbc.query(
                "select id, texto, tentativas from pedidos_cozinha where impresso_em is null"
                        + " and (proxima_tentativa is null or proxima_tentativa <= ?) order by id limit 10",
                (rs, n) -> new Object[]{rs.getLong(1), rs.getString(2), rs.getInt(3)}, Timestamp.valueOf(agora));
        for (Object[] pendente : pendentes) {
            long id = (Long) pendente[0];
            int tentativas = (Integer) pendente[2] + 1;
            try {
                imprimir(id, (String) pendente[1], config, tentativas);
                jdbc.update("update pedidos_cozinha set impresso_em = ?, tentativas = ?, erro = null, proxima_tentativa = null where id = ?",
                        Timestamp.valueOf(LocalDateTime.now(clock)), tentativas, id);
            } catch (Exception e) {
                String erro = cortar(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), 300);
                long espera = Math.min(60, 5L * tentativas);
                jdbc.update("update pedidos_cozinha set tentativas = ?, erro = ?, proxima_tentativa = ? where id = ?",
                        tentativas, erro, Timestamp.valueOf(LocalDateTime.now(clock).plusSeconds(espera)), id);
                if (tentativas == 1) log.warn("Pedido {} da cozinha não imprimiu: {}", id, erro);
            }
        }
    }

    private void imprimir(long id, String texto, Config config, int tentativa) throws Exception {
        if (pastaTeste != null) {
            Files.createDirectories(pastaTeste);
            Files.writeString(pastaTeste.resolve("pedido-" + id + "-" + tentativa + ".txt"), texto, StandardCharsets.UTF_8);
            return;
        }
        impressora.imprimir(config.impressora(), texto, config.colunas());
    }

    @Transactional
    public void reimprimir(Usuario usuario, long id) {
        int alterados = jdbc.update("update pedidos_cozinha set impresso_em = null, proxima_tentativa = null, erro = null where id = ?", id);
        if (alterados == 0) throw new ErroNegocioException("PEDIDO_COZINHA_NAO_ENCONTRADO", "Pedido da cozinha não encontrado.");
        logRepository.salvar(new LogAcao(0, usuario == null ? null : usuario.getRu(), "COZINHA_REIMPRESSAO",
                "Pedido " + id + " da cozinha mandado de novo por " + nomeDe(usuario) + "."));
    }

    // ── Consultas ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Pedido> recentes() {
        return jdbc.query("select id, tipo, comanda_numero, venda_id, criado_em, impresso_em, tentativas, erro, texto"
                        + " from pedidos_cozinha order by id desc limit 40",
                (rs, n) -> new Pedido(rs.getLong("id"), rs.getString("tipo"), (Integer) rs.getObject("comanda_numero"),
                        rs.getObject("venda_id") == null ? null : rs.getLong("venda_id"),
                        rs.getTimestamp("criado_em").toLocalDateTime(),
                        rs.getTimestamp("impresso_em") == null ? null : rs.getTimestamp("impresso_em").toLocalDateTime(),
                        rs.getInt("tentativas"), rs.getString("erro"), rs.getString("texto")));
    }

    @Transactional(readOnly = true)
    public Situacao situacao() {
        Config config = config();
        Integer aguardando = jdbc.queryForObject("select count(*) from pedidos_cozinha where impresso_em is null and tentativas = 0", Integer.class);
        Integer comErro = jdbc.queryForObject("select count(*) from pedidos_cozinha where impresso_em is null and tentativas > 0", Integer.class);
        return new Situacao(ligada(), pastaTeste != null && config.impressora().isBlank() ? "(arquivo de teste)" : config.impressora(),
                aguardando == null ? 0 : aguardando, comErro == null ? 0 : comErro);
    }

    @Transactional(readOnly = true)
    public List<Map<String, String>> categorias() {
        return jdbc.query("select codigo, nome_exibicao from categorias_cardapio where ativo order by ordem_exibicao, nome_exibicao",
                (rs, n) -> {
                    Map<String, String> categoria = new LinkedHashMap<>();
                    categoria.put("codigo", rs.getString(1));
                    categoria.put("nome", rs.getString(2));
                    return categoria;
                });
    }

    // ── Apoio ───────────────────────────────────────────────────────────

    private List<ItemVenda> itensDaVenda(long vendaId) {
        return jdbc.query("select vi.id, vi.quantidade, vi.nome_item_snapshot, vi.nome_variacao_snapshot, vi.observacao,"
                        + " c.codigo, i.tipo_item from venda_itens vi"
                        + " join itens_cardapio i on i.id = vi.item_cardapio_id"
                        + " join categorias_cardapio c on c.id = i.categoria_cardapio_id"
                        + " where vi.venda_id = ? and vi.status = 'ATIVO' order by vi.id",
                (rs, n) -> new ItemVenda(rs.getLong(1), rs.getBigDecimal(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7)), vendaId);
    }

    private Map<Long, List<String>> adicionais(List<Long> itens) {
        Map<Long, List<String>> porItem = new HashMap<>();
        if (itens.isEmpty()) return porItem;
        String marcas = String.join(",", itens.stream().map(i -> "?").toList());
        jdbc.query("select venda_item_id, nome_opcao_snapshot, quantidade_selecionada from venda_item_modificadores"
                        + " where venda_item_id in (" + marcas + ") order by id",
                rs -> {
                    int quantidade = rs.getInt(3);
                    porItem.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>())
                            .add("+ " + (quantidade > 1 ? quantidade + "x " : "") + rs.getString(2));
                }, itens.toArray());
        return porItem;
    }

    private BigDecimal enviado(long itemId) {
        BigDecimal total = jdbc.queryForObject("select coalesce(sum(quantidade), 0) from pedidos_cozinha_itens where venda_item_id = ?",
                BigDecimal.class, itemId);
        return total == null ? BigDecimal.ZERO : total;
    }

    static boolean vaiParaCozinha(String categoria, String tipo, List<String> categorias) {
        if (categorias == null || categorias.isEmpty()) return "PREPARADO_SOB_DEMANDA".equals(tipo);
        return categoria != null && categorias.contains(categoria.toLowerCase(Locale.ROOT));
    }

    static String descricao(String nome, String variacao) {
        if (variacao == null || variacao.isBlank() || VARIACOES_GENERICAS.contains(variacao.trim().toLowerCase(Locale.ROOT))) {
            return nome;
        }
        return nome + " (" + variacao.trim() + ")";
    }

    /** O papel da cozinha: tipo e comanda em letra grande, hora, quem pediu e os itens. */
    static String montarTexto(String tipo, Integer comanda, long vendaId, LocalDateTime quando, String quem,
                              List<Linha> linhas, int colunas) {
        String traco = "-".repeat(Math.max(16, colunas));
        StringBuilder texto = new StringBuilder();
        texto.append("## ").append("CANCELAMENTO".equals(tipo) ? "*** CANCELAR ***" : "COZINHA").append('\n');
        texto.append("## ").append(comanda != null ? "COMANDA " + comanda : "BALCAO - VENDA " + vendaId).append('\n');
        texto.append(quando.format(HORA)).append(" - ").append(quem).append('\n');
        texto.append(traco).append('\n');
        for (Linha linha : linhas) {
            BigDecimal quantidade = linha.quantidade().abs().setScale(3, RoundingMode.HALF_UP).stripTrailingZeros();
            texto.append(quantidade.toPlainString().replace('.', ',')).append(" x ").append(linha.descricao()).append('\n');
            for (String detalhe : linha.detalhes()) texto.append("    ").append(detalhe).append('\n');
        }
        texto.append(traco).append('\n');
        return texto.toString();
    }

    private static String nomeDe(Usuario usuario) {
        return usuario == null ? "sistema" : usuario.getNomeCompleto();
    }

    private static String cortar(String texto, int limite) {
        return texto.length() <= limite ? texto : texto.substring(0, limite);
    }
}
