package service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Guarda todos os backups de ate 2 dias, o ultimo de cada dia por 30 dias
 * e o ultimo de cada mes por 366 dias. O mais novo nunca sai.
 */
public final class RetencaoBackup {

    public record Arquivo(String nome, Instant quando) {}

    private static final Duration TUDO = Duration.ofDays(2);
    private static final Duration UM_POR_DIA = Duration.ofDays(30);
    private static final Duration UM_POR_MES = Duration.ofDays(366);

    private RetencaoBackup() {}

    public static List<String> paraApagar(
            List<Arquivo> arquivos,
            Instant agora,
            ZoneId zona) {

        Objects.requireNonNull(arquivos, "arquivos");
        Objects.requireNonNull(agora, "agora");
        Objects.requireNonNull(zona, "zona");

        List<Arquivo> ordenados = new ArrayList<>(arquivos);
        Set<String> nomesVistos = new HashSet<>();

        for (Arquivo arquivo : ordenados) {
            Objects.requireNonNull(arquivo, "Backup nulo.");

            String nome = arquivo.nome();

            if (nome == null || nome.isBlank()) {
                throw new IllegalArgumentException(
                        "Backup sem nome valido.");
            }

            Objects.requireNonNull(
                    arquivo.quando(),
                    "Data do backup: " + nome);

            if (!nomesVistos.add(nome)) {
                throw new IllegalArgumentException(
                        "Nome de backup duplicado: " + nome);
            }
        }

        ordenados.sort(Comparator
                .comparing((Arquivo arquivo) -> arquivo.quando())
                .reversed());

        Set<LocalDate> diasVistos = new HashSet<>();
        Set<YearMonth> mesesVistos = new HashSet<>();
        List<String> apagar = new ArrayList<>();

        boolean maisNovo = true;

        for (Arquivo arquivo : ordenados) {

            Instant quando = arquivo.quando();
            Duration idade = Duration.between(quando, agora);
            LocalDate dia = quando.atZone(zona).toLocalDate();

            boolean primeiroDoDia = diasVistos.add(dia);
            boolean primeiroDoMes = mesesVistos.add(YearMonth.from(dia));

            boolean manter = maisNovo
                    || idade.compareTo(TUDO) <= 0
                    || (idade.compareTo(UM_POR_DIA) <= 0
                        && primeiroDoDia)
                    || (idade.compareTo(UM_POR_MES) <= 0
                        && primeiroDoMes);

            if (!manter) {
                apagar.add(arquivo.nome());
            }

            maisNovo = false;
        }

        return apagar;
    }
}