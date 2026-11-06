package service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupNovoTest {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");

    @TempDir Path temp;

    private Path pasta;
    private Path pen;
    private RelogioFixo relogio;
    private final List<List<String>> comandos = new ArrayList<>();
    private int saidaDump;
    private int saidaConferencia;
    private final List<Path> destinos = new ArrayList<>();

    @BeforeEach
    void preparar() throws IOException {
        pasta = temp.resolve("backups");
        pen = temp.resolve("E").resolve(BackupService.PASTA_EXTERNA);
        relogio = new RelogioFixo(Instant.parse("2026-11-06T15:00:00Z"));
    }

    private BackupService servico() {
        return new BackupService(pasta, Path.of("pg", "bin"),
                "jdbc:postgresql://127.0.0.1:5544/stockos", "stockos", "segredo",
                Duration.ofHours(3), () -> destinos,
                (comando, ambiente, registro) -> {
                    comandos.add(comando);
                    if (comando.get(0).endsWith("pg_dump")) {
                        assertEquals("segredo", ambiente.get("PGPASSWORD"));
                        assertFalse(comando.contains("segredo"), "Senha não pode ir na linha de comando");
                        Files.writeString(Path.of(comando.get(comando.size() - 1)), "dump");
                        Files.writeString(registro, "falhou de verdade\n");
                        return saidaDump;
                    }
                    return saidaConferencia;
                },
                relogio);
    }

    private List<String> arquivos(Path diretorio) throws IOException {
        if (!Files.isDirectory(diretorio)) return List.of();
        try (Stream<Path> itens = Files.list(diretorio)) {
            return itens.map(p -> p.getFileName().toString()).filter(n -> n.contains(".dump")).sorted().toList();
        }
    }

    @Test
    void desligadoQuandoSemPasta() {
        BackupService servico = new BackupService(null, null, "", "", "", Duration.ofHours(3), List::of,
                (c, a, r) -> { throw new AssertionError("Não pode rodar nada"); }, relogio);
        assertFalse(servico.ativo());
        assertFalse(servico.executar("teste"));
        servico.verificar();
        assertFalse(servico.situacao().monitorado());
    }

    @Test
    void backupBomFicaComNomeDefinitivoEConferido() throws IOException {
        BackupService servico = servico();
        assertTrue(servico.executar("teste"));

        assertEquals(List.of("stockos-20261106-120000.dump"), arquivos(pasta));
        assertTrue(comandos.get(0).containsAll(List.of("-h", "127.0.0.1", "-p", "5544", "-U", "stockos", "-d", "stockos", "-Fc")));
        assertEquals("--list", comandos.get(1).get(1));

        BackupService.Situacao situacao = servico.situacao();
        assertTrue(situacao.monitorado());
        assertEquals(relogio.instant(), situacao.ultimoBackup());
        assertEquals(1, situacao.quantidade());
        assertFalse(situacao.atrasado());
        assertNull(situacao.ultimoErro());
    }

    @Test
    void falhaNoDumpNaoDeixaArquivoEApareceNaTela() throws IOException {
        saidaDump = 1;
        BackupService servico = servico();
        assertTrue(servico.executar("teste"));

        assertEquals(List.of(), arquivos(pasta));
        BackupService.Situacao situacao = servico.situacao();
        assertTrue(situacao.atrasado());
        assertTrue(situacao.ultimoErro().contains("falhou de verdade"), situacao.ultimoErro());
    }

    @Test
    void arquivoQueNaoPassaNaConferenciaEDescartadoSemApagarOAnterior() throws IOException {
        BackupService servico = servico();
        servico.executar("bom");
        relogio.avancar(Duration.ofHours(3));
        saidaConferencia = 1;
        servico.executar("ruim");

        assertEquals(List.of("stockos-20261106-120000.dump"), arquivos(pasta));
        assertTrue(servico.situacao().atrasado());
        assertNotNull(servico.situacao().ultimoErro());

        relogio.avancar(Duration.ofHours(1));
        saidaConferencia = 0;
        servico.executar("voltou");
        assertNull(servico.situacao().ultimoErro(), "Backup bom limpa o erro");
    }

    @Test
    void copiaParaPenDriveQuandoAPastaExiste() throws IOException {
        destinos.add(pen);
        BackupService servico = servico();

        servico.executar("sem pen");
        assertEquals(List.of(), arquivos(pen));
        assertTrue(servico.situacao().semCopiaExterna());

        Files.createDirectories(pen);
        servico.verificar();
        assertEquals(List.of("stockos-20261106-120000.dump"), arquivos(pen), "Pen conectado depois recebe o último");
        BackupService.Situacao situacao = servico.situacao();
        assertFalse(situacao.semCopiaExterna());
        assertEquals(pen.toString(), situacao.destinoExterno());

        relogio.avancar(Duration.ofDays(8));
        assertTrue(servico.situacao().semCopiaExterna(), "Pen esquecido há mais de 7 dias volta a avisar");
    }

    @Test
    void falhaNaCopiaNaoDerrubaOBackup() throws IOException {
        Path arquivoNoLugarDaPasta = temp.resolve("pen-cheio");
        Files.createDirectories(arquivoNoLugarDaPasta.resolve("stockos-20261106-120000.dump.parcial").resolve("x"));
        destinos.add(arquivoNoLugarDaPasta);
        BackupService servico = servico();

        servico.executar("teste");

        assertEquals(List.of("stockos-20261106-120000.dump"), arquivos(pasta));
        BackupService.Situacao situacao = servico.situacao();
        assertNull(situacao.ultimoErro());
        assertNotNull(situacao.erroCopia());
    }

    @Test
    void verificarFazBackupSoQuandoVence() throws IOException {
        BackupService servico = servico();
        servico.verificar();
        assertEquals(1, arquivos(pasta).size());

        relogio.avancar(Duration.ofHours(2));
        servico.verificar();
        assertEquals(1, arquivos(pasta).size());

        relogio.avancar(Duration.ofHours(1));
        servico.verificar();
        assertEquals(2, arquivos(pasta).size());
    }

    @Test
    void naoAvisaAtrasoLogoAoLigarONotebook() throws IOException {
        Files.createDirectories(pasta);
        Files.writeString(pasta.resolve("stockos-20261101-120000.dump"), "antigo");
        BackupService servico = servico();
        assertFalse(servico.situacao().atrasado(), "Acabou de ligar: o backup ainda vai rodar");

        relogio.avancar(Duration.ofMinutes(21));
        assertTrue(servico.situacao().atrasado());
    }

    @Test
    void arquivoEstranhoNaPastaEIgnoradoENuncaApagado() throws IOException {
        Files.createDirectories(pasta);
        Path meu = pasta.resolve("stockos-antes-da-atualizacao.dump");
        Files.writeString(meu, "guardado");
        Files.writeString(pasta.resolve("stockos-20200101-120000.dump"), "velho");
        BackupService servico = servico();

        servico.executar("teste");

        assertTrue(Files.exists(meu));
        assertFalse(Files.exists(pasta.resolve("stockos-20200101-120000.dump")), "Mais de um ano: sai");
        assertEquals(1, servico.situacao().quantidade());
    }

    @Test
    void retencaoGuardaDoisDiasDepoisUmPorDiaDepoisUmPorMes() {
        Instant agora = Instant.parse("2026-11-06T15:00:00Z");
        List<RetencaoBackup.Arquivo> arquivos = new ArrayList<>();
        // A cada 3 horas, por 400 dias.
        for (int i = 0; i < 400 * 8; i++) {
            Instant quando = agora.minus(Duration.ofHours(3L * i));
            arquivos.add(new RetencaoBackup.Arquivo("a" + i, quando));
        }
        List<String> apagar = RetencaoBackup.paraApagar(arquivos, agora, ZONA);
        List<RetencaoBackup.Arquivo> restantes = arquivos.stream().filter(a -> !apagar.contains(a.nome())).toList();

        assertEquals(17, restantes.stream().filter(a -> Duration.between(a.quando(), agora).compareTo(Duration.ofDays(2)) <= 0).count());
        long dias = restantes.stream().map(a -> a.quando().atZone(ZONA).toLocalDate()).distinct().count();
        assertTrue(dias >= 40 && dias <= 45, "Dias guardados: " + dias);
        assertTrue(restantes.size() < 70, "Sobram " + restantes.size());
        assertTrue(restantes.stream().noneMatch(a -> Duration.between(a.quando(), agora).compareTo(Duration.ofDays(366)) > 0));
    }

    @Test
    void retencaoNuncaApagaOMaisNovoMesmoAntigo() {
        Instant agora = Instant.parse("2026-11-06T15:00:00Z");
        List<RetencaoBackup.Arquivo> arquivos = List.of(
                new RetencaoBackup.Arquivo("antigo", agora.minus(Duration.ofDays(900))),
                new RetencaoBackup.Arquivo("mais-antigo", agora.minus(Duration.ofDays(901))));
        assertEquals(List.of("mais-antigo"), RetencaoBackup.paraApagar(arquivos, agora, ZONA));
    }

    @Test
    void enderecoDoBancoInvalidoViraErroNaTela() throws IOException {
        BackupService servico = new BackupService(pasta, null, "banco-errado", "u", "s", Duration.ofHours(3), List::of,
                (c, a, r) -> 0, relogio);
        servico.executar("teste");
        assertNotNull(servico.situacao().ultimoErro());
    }

    private static final class RelogioFixo extends Clock {
        private Instant agora;

        RelogioFixo(Instant inicio) {
            this.agora = inicio;
        }

        void avancar(Duration tempo) {
            agora = agora.plus(tempo);
        }

        @Override
        public ZoneId getZone() {
            return ZONA;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }
}
