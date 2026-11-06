package service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

/**
 * Senhas e PINs guardados como hash BCrypt.
 *
 * <p>Bancos antigos têm senhas em texto puro: {@link #confere} ainda as aceita
 * para o login não quebrar, e {@code AutenticacaoService} troca pelo hash assim
 * que possível (na inicialização e no primeiro login).
 */
public class SenhaService {

    private static final Pattern BCRYPT = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$.{53}$");
    private static final Pattern PIN = Pattern.compile("^\\d{4,6}$");
    private static final int TAMANHO_MINIMO_SENHA = 6;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public String gerarHash(String texto) {
        return encoder.encode(texto);
    }

    public boolean ehHash(String armazenada) {
        return armazenada != null && BCRYPT.matcher(armazenada).matches();
    }

    public boolean confere(String informada, String armazenada) {
        if (informada == null || informada.isEmpty() || armazenada == null) {
            return false;
        }
        if (ehHash(armazenada)) {
            return encoder.matches(informada, armazenada);
        }
        return MessageDigest.isEqual(
                informada.getBytes(StandardCharsets.UTF_8),
                armazenada.getBytes(StandardCharsets.UTF_8));
    }

    public void validarNovaSenha(String senha) {
        if (senha == null || senha.length() < TAMANHO_MINIMO_SENHA) {
            throw new IllegalArgumentException("A senha precisa ter pelo menos " + TAMANHO_MINIMO_SENHA + " caracteres.");
        }
        if (senha.isBlank() || senha.chars().distinct().count() == 1 || "123456".equals(senha)) {
            throw new IllegalArgumentException("Escolha uma senha menos fácil de adivinhar.");
        }
        // BCrypt ignora o que passa de 72 bytes: duas senhas longas diferentes valeriam como iguais.
        if (senha.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("A senha pode ter no máximo 72 caracteres.");
        }
    }

    public void validarPin(String pin) {
        if (pin == null || !PIN.matcher(pin).matches()) {
            throw new IllegalArgumentException("O PIN precisa ter de 4 a 6 números.");
        }
        if (pin.chars().distinct().count() == 1 || "1234".equals(pin) || "123456".equals(pin)) {
            throw new IllegalArgumentException("Escolha um PIN menos fácil de adivinhar.");
        }
    }
}
