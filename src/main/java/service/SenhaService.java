package service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * Senhas e PINs guardados como hash BCrypt.
 *
 * <p>A compatibilidade com credenciais legadas em texto puro e temporaria.
 * A migracao deve ocorrer no AutenticacaoService, sem converter hashes
 * invalidos em credenciais legadas.
 */
public class SenhaService {

    private static final Pattern BCRYPT = Pattern.compile(
            "^\\$2[aby]\\$(?:0[4-9]|[12][0-9]|3[01])\\$[./A-Za-z0-9]{53}$");
    private static final Pattern PIN = Pattern.compile("^[0-9]{4,6}$");
    private static final int TAMANHO_MINIMO_SENHA = 6;
    private static final int MAXIMO_BYTES_BCRYPT = 72;

    private static final String ALFABETO_TEMPORARIA = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int TAMANHO_TEMPORARIA = 8;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** A obrigatoriedade da troca e a validade sao controladas pelo fluxo de autenticacao. */
    public static String gerarTemporaria() {
        StringBuilder senha = new StringBuilder(TAMANHO_TEMPORARIA);
        for (int i = 0; i < TAMANHO_TEMPORARIA; i++) {
            senha.append(ALFABETO_TEMPORARIA.charAt(
                    ALEATORIO.nextInt(ALFABETO_TEMPORARIA.length())));
        }
        return senha.toString();
    }

    public String gerarHash(String texto) {
        if (texto == null || texto.isEmpty()) {
            throw new IllegalArgumentException("A credencial nao pode ser vazia.");
        }
        validarLimiteBcrypt(texto);
        return encoder.encode(texto);
    }

    /** Verifica o formato, nao a autenticidade nem a integridade criptografica. */
    public boolean ehHash(String armazenada) {
        return armazenada != null && BCRYPT.matcher(armazenada).matches();
    }

    /** Distingue texto legado de representacoes reservadas a hashes BCrypt. */
    public boolean ehCredencialLegada(String armazenada) {
        return armazenada != null && !armazenada.isEmpty()
                && !armazenada.startsWith("$2")
                && !armazenada.startsWith("{bcrypt}");
    }

    public boolean confere(String informada, String armazenada) {
        if (informada == null || informada.isEmpty()
                || armazenada == null || armazenada.isEmpty()
                || !entradaBcryptValida(informada)) {
            return false;
        }
        if (ehHash(armazenada)) {
            try {
                return encoder.matches(informada, armazenada);
            } catch (IllegalArgumentException e) {
                // Hash invalido: nunca utilizar a comparacao em texto puro como fallback.
                return false;
            }
        }
        if (!ehCredencialLegada(armazenada)) {
            return false;
        }
        return MessageDigest.isEqual(
                informada.getBytes(StandardCharsets.UTF_8),
                armazenada.getBytes(StandardCharsets.UTF_8));
    }

    public void validarNovaSenha(String senha) {
        if (senha == null || senha.length() < TAMANHO_MINIMO_SENHA) {
            throw new IllegalArgumentException(
                    "A senha precisa ter pelo menos " + TAMANHO_MINIMO_SENHA + " caracteres.");
        }
        validarLimiteBcrypt(senha);
        if (senha.isBlank() || senha.chars().distinct().count() == 1 || "123456".equals(senha)) {
            throw new IllegalArgumentException("Escolha uma senha menos fácil de adivinhar.");
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

    private static boolean entradaBcryptValida(String texto) {
        return texto.length() <= MAXIMO_BYTES_BCRYPT
                && StandardCharsets.UTF_8.newEncoder().canEncode(texto)
                && texto.getBytes(StandardCharsets.UTF_8).length <= MAXIMO_BYTES_BCRYPT;
    }

    private static void validarLimiteBcrypt(String texto) {
        if (!entradaBcryptValida(texto)) {
            throw new IllegalArgumentException(
                    "A credencial deve ser UTF-8 valida e ter no maximo "
                            + MAXIMO_BYTES_BCRYPT + " bytes.");
        }
    }
}
