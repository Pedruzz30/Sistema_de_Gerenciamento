package service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Backup automático do banco da loja.
 *
 * Roda dentro do próprio sistema: só há o que proteger enquanto ele está no ar, e assim
 * nenhuma janela abre por cima do caixa no meio de uma venda. Fica desligado quando
 * stockos.backup.pasta está vazio (testes e desenvolvimento).
 *
 * Um arquivo só ganha o nome definitivo depois de conferido com pg_restore, então um
 * backup interrompido nunca é confundido com um backup bom.
 */
public class BackupService {

    /** Pasta com este nome em pen drive, HD externo ou nuvem recebe uma cópia sozinha. */
    public static final String PASTA_EXTERNA = "StockOS-Backups";

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter FORMATO_NOME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);
    private static final String PREFIXO = "stockos-";
    private static final String EXTENSAO = ".dump";
    private static final String ARQUIVO_SITUACAO = "situacao-backup.json";
    private static final Duration ATRASO_ALERTA = Duration.ofHours(12);
    private static final Duration COPIA_EXTERNA_MAXIMA = Duration.ofDays(7);
    private static final Duration TOLERANCIA_AO_LIGAR = Duration.ofMinutes(20);

    public record Situacao(boolean monitorado,
                           Instant ultimoBackup,
                           Long tamanhoBytes,
                           int quantidade,
                           String pasta,
                           Instant ultimaCopiaExterna,
                           String destinoExterno,
                           String ultimoErro,
                           String erroCopia,
                           boolean atrasado,
                           boolean semCopiaExterna,
                           boolean emAndamento) {}

    /** Roda um programa externo e devolve o código de saída. Substituível nos testes. */
    public interface ExecutorComando {
        int executar(List<String> comando, Map<String, String> ambiente, Path registro) throws IOException, InterruptedException;
    }

    private final Path pasta;
    private final Path pgBin;
    private final String url;
    private final String usuario;
    private final String senha;
    private final Duration intervalo;
    private final Supplier<List<Path>> destinos;
    private final ExecutorComando executor;
    private final Clock clock;
    private final Instant iniciadoEm;
    private final ReentrantLock trava = new ReentrantLock();
    private final ObjectMapper json = new ObjectMapper();

    public BackupService(Path pasta,
                         Path pgBin,
                         String url,
                         String usuario,
                         String senha,
                         Duration intervalo,
                         Supplier<List<Path>> destinos,
                         ExecutorComando executor,
                         Clock clock) {
        this.pasta = pasta;
        this.pgBin = pgBin;
        this.url = url;
        this.usuario = usuario;
        this.senha = senha;
        this.intervalo = intervalo;
        this.destinos = destinos;
        this.executor = executor;
        this.clock = clock;
        this.iniciadoEm = clock.instant();
    }

    public boolean ativo() {
        return pasta != null;
    }

    /** Verificação periódica: faz o backup vencido e leva o último ao pen drive recém-conectado. */
    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT15M")
    public void verificar() {
        if (!ativo()) return;
        Instant ultimo = ultimoBackup().map(Arquivo::quando).orElse(null);
        if (ultimo == null || Duration.between(ultimo, clock.instant()).compareTo(intervalo) >= 0) {
            executar("automático");
        } else if (trava.tryLock()) {
            try {
                copiarParaDestinos();
            } finally {
                trava.unlock();
            }
        }
    }

    /** Depois de um fechamento de caixa: guarda o dia sem fazer ninguém esperar. */
    public void executarEmSegundoPlano(String motivo) {
        if (!ativo()) return;
        Thread tarefa = new Thread(() -> executar(motivo), "stockos-backup");
        tarefa.setDaemon(true);
        tarefa.start();
    }

    /** Devolve false só quando já há um backup em andamento; erros ficam na situação. */
    public boolean executar(String motivo) {
        if (!ativo()) return false;
        if (!trava.tryLock()) return false;
        try {
            log.info("Iniciando backup ({}).", motivo);
            Files.createDirectories(pasta);
            Path definitivo = gerarBackup();
            log.info("Backup concluído: {} ({} bytes).", definitivo.getFileName(), Files.size(definitivo));
            gravarErro(null);
            aplicarRetencao(pasta);
            copiarParaDestinos();
        } catch (IOException | RuntimeException e) {
            registrarFalha(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            registrarFalha("Backup interrompido.");
        } finally {
            trava.unlock();
        }
        return true;
    }

    private Path gerarBackup() throws IOException, InterruptedException {
        String nome = PREFIXO + LocalDateTime.now(clock).format(FORMATO_NOME) + EXTENSAO;
        Path parcial = pasta.resolve(nome + ".parcial");
        Path registro = pasta.resolve("ultimo-backup.log");
        try {
            Conexao conexao = Conexao.de(url);
            List<String> dump = List.of(programa("pg_dump"), "-h", conexao.host(), "-p", conexao.porta(),
                    "-U", usuario, "-d", conexao.banco(), "-w", "-Fc", "--file", parcial.toString());
            int saida = executor.executar(dump, Map.of("PGPASSWORD", senha == null ? "" : senha), registro);
            if (saida != 0) {
                throw new IOException("O programa de backup terminou com erro (código " + saida + "). " + ultimaLinha(registro));
            }
            if (!Files.isRegularFile(parcial) || Files.size(parcial) == 0) {
                throw new IOException("O programa de backup não gerou o arquivo.");
            }
            int conferencia = executor.executar(List.of(programa("pg_restore"), "--list", parcial.toString()), Map.of(), registro);
            if (conferencia != 0) {
                throw new IOException("O arquivo gerado não passou na conferência.");
            }
            return Files.move(parcial, pasta.resolve(nome), StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(parcial);
        }
    }

    private String programa(String nome) {
        return pgBin == null ? nome : pgBin.resolve(nome).toString();
    }

    private void copiarParaDestinos() {
        Arquivo ultimo = ultimoBackup().orElse(null);
        if (ultimo == null) return;
        String erro = null;
        for (Path destino : destinos.get()) {
            if (destino.toAbsolutePath().normalize().equals(pasta.toAbsolutePath().normalize())) continue;
            try {
                if (!Files.isDirectory(destino)) continue;
                Path alvo = destino.resolve(ultimo.caminho().getFileName());
                if (!Files.exists(alvo)) {
                    Path parcial = destino.resolve(alvo.getFileName() + ".parcial");
                    try {
                        Files.copy(ultimo.caminho(), parcial, StandardCopyOption.REPLACE_EXISTING);
                        Files.move(parcial, alvo, StandardCopyOption.ATOMIC_MOVE);
                    } finally {
                        Files.deleteIfExists(parcial);
                    }
                    aplicarRetencao(destino);
                    log.info("Cópia do backup gravada em {}.", destino);
                }
                gravarCopiaExterna(destino);
            } catch (IOException | RuntimeException e) {
                log.warn("Não foi possível copiar o backup para {}: {}", destino, e.getMessage());
                erro = "Não foi possível copiar para " + destino + ". Confira se há espaço livre.";
            }
        }
        gravarErroCopia(erro);
    }

    private void aplicarRetencao(Path diretorio) throws IOException {
        List<Arquivo> arquivos = listar(diretorio);
        List<RetencaoBackup.Arquivo> entrada = arquivos.stream()
                .map(a -> new RetencaoBackup.Arquivo(a.caminho().getFileName().toString(), a.quando()))
                .toList();
        for (String nome : RetencaoBackup.paraApagar(entrada, clock.instant(), clock.getZone())) {
            Files.deleteIfExists(diretorio.resolve(nome));
        }
    }

    public Situacao situacao() {
        if (!ativo()) {
            return new Situacao(false, null, null, 0, null, null, null, null, null, false, false, false);
        }
        List<Arquivo> arquivos = listar(pasta);
        Arquivo ultimo = arquivos.isEmpty() ? null : arquivos.get(0);
        JsonNode registro = lerRegistro();
        Instant copia = instante(registro.path("ultimaCopiaExterna").asText(null));
        Instant agora = clock.instant();
        String erro = registro.path("ultimoErro").asText(null);

        boolean jaDeuTempo = Duration.between(iniciadoEm, agora).compareTo(TOLERANCIA_AO_LIGAR) >= 0;
        boolean atrasado = erro != null
                || (jaDeuTempo && (ultimo == null || Duration.between(ultimo.quando(), agora).compareTo(ATRASO_ALERTA) > 0));
        boolean semCopia = copia == null || Duration.between(copia, agora).compareTo(COPIA_EXTERNA_MAXIMA) > 0;

        Long tamanho = null;
        if (ultimo != null) {
            try {
                tamanho = Files.size(ultimo.caminho());
            } catch (IOException ignorado) {
                // O arquivo pode ter sido removido à mão; a data já foi lida.
            }
        }
        return new Situacao(true,
                ultimo == null ? null : ultimo.quando(),
                tamanho,
                arquivos.size(),
                pasta.toString(),
                copia,
                registro.path("destinoExterno").asText(null),
                erro,
                registro.path("erroCopia").asText(null),
                atrasado,
                semCopia,
                trava.isLocked());
    }

    private java.util.Optional<Arquivo> ultimoBackup() {
        return listar(pasta).stream().findFirst();
    }

    /** Backups da pasta, do mais novo para o mais antigo. Só conta arquivo com o nome do sistema. */
    private List<Arquivo> listar(Path diretorio) {
        List<Arquivo> arquivos = new ArrayList<>();
        if (!Files.isDirectory(diretorio)) return arquivos;
        try (DirectoryStream<Path> itens = Files.newDirectoryStream(diretorio, PREFIXO + "*" + EXTENSAO)) {
            for (Path item : itens) {
                Instant quando = instanteDoNome(item.getFileName().toString());
                if (quando != null) arquivos.add(new Arquivo(item, quando));
            }
        } catch (IOException e) {
            log.warn("Não foi possível listar {}: {}", diretorio, e.getMessage());
        }
        arquivos.sort(Comparator.comparing(Arquivo::quando).reversed());
        return arquivos;
    }

    private Instant instanteDoNome(String nome) {
        String miolo = nome.substring(PREFIXO.length(), nome.length() - EXTENSAO.length());
        try {
            return LocalDateTime.parse(miolo, FORMATO_NOME).atZone(clock.getZone()).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private void registrarFalha(String mensagem) {
        String texto = mensagem == null || mensagem.isBlank() ? "Falha desconhecida no backup." : mensagem;
        log.error("Falha no backup: {}", texto);
        gravarErro(texto);
    }

    private void gravarErro(String erro) {
        atualizarRegistro(r -> definir(r, "ultimoErro", erro));
    }

    private void gravarErroCopia(String erro) {
        atualizarRegistro(r -> definir(r, "erroCopia", erro));
    }

    private void gravarCopiaExterna(Path destino) {
        atualizarRegistro(r -> {
            r.put("ultimaCopiaExterna", clock.instant().toString());
            r.put("destinoExterno", destino.toString());
        });
    }

    private static void definir(ObjectNode registro, String campo, String valor) {
        if (valor == null) registro.remove(campo);
        else registro.put(campo, valor);
    }

    private JsonNode lerRegistro() {
        Path arquivo = pasta.resolve(ARQUIVO_SITUACAO);
        try {
            if (Files.isRegularFile(arquivo)) return json.readTree(arquivo.toFile());
        } catch (IOException e) {
            log.warn("Registro de backup ilegível: {}", e.getMessage());
        }
        return json.createObjectNode();
    }

    private void atualizarRegistro(java.util.function.Consumer<ObjectNode> alteracao) {
        try {
            Files.createDirectories(pasta);
            JsonNode atual = lerRegistro();
            ObjectNode registro = atual instanceof ObjectNode objeto ? objeto : json.createObjectNode();
            alteracao.accept(registro);
            Path temporario = pasta.resolve(ARQUIVO_SITUACAO + ".tmp");
            Files.writeString(temporario, json.writeValueAsString(registro), StandardCharsets.UTF_8);
            Files.move(temporario, pasta.resolve(ARQUIVO_SITUACAO), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warn("Não foi possível gravar o registro de backup: {}", e.getMessage());
        }
    }

    private static Instant instante(String texto) {
        if (texto == null) return null;
        try {
            return Instant.parse(texto);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String ultimaLinha(Path registro) {
        try {
            List<String> linhas = Files.readAllLines(registro, StandardCharsets.ISO_8859_1);
            return linhas.isEmpty() ? "" : linhas.get(linhas.size() - 1);
        } catch (IOException e) {
            return "";
        }
    }

    /** Raízes dos discos (pen drive incluso) e pastas de nuvem na pasta do usuário. */
    public static List<Path> destinosPadrao() {
        List<Path> destinos = new ArrayList<>();
        File[] raizes = File.listRoots();
        if (raizes != null) {
            for (File raiz : raizes) destinos.add(raiz.toPath().resolve(PASTA_EXTERNA));
        }
        Path casa = Path.of(System.getProperty("user.home", "."));
        try (DirectoryStream<Path> nuvens = Files.newDirectoryStream(casa, "{OneDrive*,Google Drive*,Dropbox*}")) {
            for (Path nuvem : nuvens) destinos.add(nuvem.resolve(PASTA_EXTERNA));
        } catch (IOException | RuntimeException e) {
            log.debug("Sem pastas de nuvem em {}.", casa);
        }
        return destinos;
    }

    /** Executor real: saída do programa no registro, com limite de tempo. */
    public static int executarProcesso(List<String> comando, Map<String, String> ambiente, Path registro)
            throws IOException, InterruptedException {
        ProcessBuilder construtor = new ProcessBuilder(comando)
                .redirectErrorStream(true)
                .redirectOutput(registro.toFile());
        construtor.environment().putAll(ambiente);
        Process processo = construtor.start();
        if (!processo.waitFor(15, TimeUnit.MINUTES)) {
            processo.destroyForcibly();
            throw new IOException("O programa de backup demorou demais e foi interrompido.");
        }
        return processo.exitValue();
    }

    private record Arquivo(Path caminho, Instant quando) {}

    record Conexao(String host, String porta, String banco) {
        static Conexao de(String jdbcUrl) {
            URI uri = URI.create(jdbcUrl.replaceFirst("^jdbc:", ""));
            String banco = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");
            if (uri.getHost() == null || banco.isBlank()) {
                throw new IllegalArgumentException("Endereço do banco inválido para o backup.");
            }
            return new Conexao(uri.getHost(), String.valueOf(uri.getPort() > 0 ? uri.getPort() : 5432), banco);
        }
    }
}
