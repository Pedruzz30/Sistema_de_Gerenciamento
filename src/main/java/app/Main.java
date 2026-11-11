package app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"app", "controller", "service", "repository"})
@EntityScan(basePackages = {"model"})
@EnableJpaRepositories(basePackages = {"repository"})
public class Main {

    public static void main(String[] args) {
        // Toda hora do sistema no fuso da loja, não no do Windows: senão, perto da meia-noite,
        // o caixa e as comandas caem em dias diferentes no relatório.
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(fusoDaLoja(args, System.getenv("STOCKOS_TIMEZONE"))));
        SpringApplication.run(Main.class, args);
    }

    /** O mesmo fuso do relógio do sistema (stockos.timezone), lido antes de o Spring subir. */
    static java.time.ZoneId fusoDaLoja(String[] args, String variavel) {
        String fuso = null;
        for (String arg : args == null ? new String[0] : args) {
            if (arg != null && arg.startsWith("--stockos.timezone=")) fuso = arg.substring("--stockos.timezone=".length());
        }
        if (fuso == null || fuso.isBlank()) fuso = System.getProperty("stockos.timezone", variavel);
        try {
            return java.time.ZoneId.of(fuso == null || fuso.isBlank() ? "America/Sao_Paulo" : fuso.trim());
        } catch (java.time.DateTimeException e) {
            return java.time.ZoneId.of("America/Sao_Paulo");
        }
    }
}
