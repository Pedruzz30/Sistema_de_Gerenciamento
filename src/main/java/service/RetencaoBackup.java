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
import java.util.Set;

/**
 * Quais backups antigos podem sair: guarda tudo de 2 dias, o último de cada dia por 30 dias
 * e o último de cada mês por um ano. O mais novo nunca sai.
 */
public final class RetencaoBackup {

    public record Arquivo(String nome, Instant quando) {}

    private static final Duration TUDO = Duration.ofDays(2);
    private static final Duration UM_POR_DIA = Duration.ofDays(30);
    private static final Duration UM_POR_MES = Duration.ofDays(366);

    private RetencaoBackup() {}

    public static List<String> paraApagar(List<Arquivo> arquivos, Instant agora, ZoneId zona) {
        List<Arquivo> ordenados = new ArrayList<>(arquivos);
        ordenados.sort(Comparator.comparing(Arquivo::quando).reversed());

        Set<LocalDate> diasVistos = new HashSet<>();
        Set<YearMonth> mesesVistos = new HashSet<>();
        List<String> apagar = new ArrayList<>();
        boolean maisNovo = true;

        for (Arquivo arquivo : ordenados) {
            Duration idade = Duration.between(arquivo.quando(), agora);
            LocalDate dia = arquivo.quando().atZone(zona).toLocalDate();
            boolean primeiroDoDia = diasVistos.add(dia);
            boolean primeiroDoMes = mesesVistos.add(YearMonth.from(dia));

            boolean manter = maisNovo
                    || idade.compareTo(TUDO) <= 0
                    || (idade.compareTo(UM_POR_DIA) <= 0 && primeiroDoDia)
                    || (idade.compareTo(UM_POR_MES) <= 0 && primeiroDoMes);
            if (!manter) apagar.add(arquivo.nome());
            maisNovo = false;
        }
        return apagar;
    }
}
