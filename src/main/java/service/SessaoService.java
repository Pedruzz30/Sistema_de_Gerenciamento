package service;

import model.LogAcao;
import model.Permissao;
import model.SessaoUsuario;
import model.Usuario;
import org.springframework.transaction.annotation.Transactional;
import repository.LogRepository;
import repository.SessaoUsuarioRepository;
import repository.UsuarioRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Sessoes de login no servidor. Apenas o hash do token fica no banco.
 * A identidade e obtida da sessao, nao de cabecalhos informados pelo cliente.
 */
public class SessaoService {

    private static final int TOKEN_BYTES = 32;
    private static final int TOKEN_CARACTERES = 43;
    private static final Pattern FORMATO_TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final SessaoUsuarioRepository sessaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AutenticacaoService autenticacaoService;
    private final LogRepository logRepository;
    private final Duration duracao;
    private final Clock relogio;
    private final SecureRandom aleatorio = new SecureRandom();

    public SessaoService(SessaoUsuarioRepository sessaoRepository,
                         UsuarioRepository usuarioRepository,
                         AutenticacaoService autenticacaoService,
                         LogRepository logRepository,
                         Duration duracao,
                         Clock relogio) {
        this.sessaoRepository = Objects.requireNonNull(sessaoRepository, "sessaoRepository");
        this.usuarioRepository = Objects.requireNonNull(usuarioRepository, "usuarioRepository");
        this.autenticacaoService = Objects.requireNonNull(autenticacaoService, "autenticacaoService");
        this.logRepository = Objects.requireNonNull(logRepository, "logRepository");
        this.duracao = Objects.requireNonNull(duracao, "duracao");
        this.relogio = Objects.requireNonNull(relogio, "relogio");
        if (duracao.isZero() || duracao.isNegative()) {
            throw new IllegalArgumentException("A duracao da sessao deve ser positiva.");
        }
    }

    public Duration getDuracao() {
        return duracao;
    }

    /** @return o token que vai para o cookie; so o hash dele fica no banco */
    @Transactional
    public String criar(Usuario usuario) {
        Objects.requireNonNull(usuario, "usuario");
        LocalDateTime agora = LocalDateTime.now(relogio);
        LocalDateTime expiraEm = agora.plus(duracao);
        sessaoRepository.removerExpiradas(agora);
        String token = gerarToken();
        sessaoRepository.salvar(new SessaoUsuario(hash(token), usuario, agora, expiraEm));
        return token;
    }

    @Transactional(readOnly = true)
    public Optional<SessaoUsuario> resolver(String token) {
        if (!tokenValido(token)) {
            return Optional.empty();
        }
        LocalDateTime agora = LocalDateTime.now(relogio);
        return sessaoRepository.buscarPorTokenHash(hash(token))
                .filter(sessao -> !sessao.expirada(agora))
                .filter(sessao -> {
                    Usuario usuario = sessao.getUsuario();
                    return usuario != null && usuario.isAtivo();
                });
    }

    @Transactional
    public void encerrar(String token) {
        if (!tokenValido(token)) {
            return;
        }
        sessaoRepository.buscarPorTokenHash(hash(token)).ifPresent(sessao -> {
            sessao.encerrar(LocalDateTime.now(relogio));
            sessaoRepository.salvar(sessao);
        });
    }

    /** Depois de redefinir a senha: quem estava logado como essa pessoa sai. */
    @Transactional
    public void encerrarDoUsuario(long ru) {
        sessaoRepository.encerrarDoUsuario(ru, LocalDateTime.now(relogio));
    }

    /** Quem pode assumir o caixa por PIN. */
    @Transactional(readOnly = true)
    public List<Usuario> listarOperadores() {
        return usuarioRepository.listarTodos().stream()
                .filter(usuario -> usuario.isAtivo())
                .filter(usuario -> usuario.getPinHash() != null)
                .filter(usuario -> operaCaixa(usuario))
                .toList();
    }

    /**
     * Troca quem esta operando sem sair do PDV. A restricao da sessao
     * a area de caixa depende tambem do SessaoFilter.
     */
    @Transactional
    public SessaoUsuario trocarOperador(String token, long ru, String pin) {
        SessaoUsuario sessao = resolver(token)
                .orElseThrow(() -> new SecurityException("Sessao encerrada. Entre de novo."));
        Usuario anterior = Objects.requireNonNull(sessao.getUsuario(), "Usuario da sessao");
        Usuario operador = usuarioRepository.buscarPorRu(ru)
                .filter(usuario -> usuario.isAtivo())
                .filter(usuario -> operaCaixa(usuario))
                .orElseThrow(() -> new IllegalArgumentException("Operador nao encontrado."));
        if (!autenticacaoService.conferirPin(operador, pin)) {
            throw new IllegalArgumentException("PIN incorreto.");
        }
        sessao.trocarOperador(operador);
        SessaoUsuario salva = sessaoRepository.salvar(sessao);
        logRepository.salvar(new LogAcao(0, operador.getRu(), "OPERADOR_TROCADO",
                "Caixa passou de " + anterior.getNomeCompleto() + " para " + operador.getNomeCompleto() + " por PIN"));
        return salva;
    }

    /** Entrada pelo celular do atendente: nome e PIN, sem senha. */
    // O PIN errado fica no log mesmo com a entrada recusada, conforme a regra transacional existente.
    @Transactional(noRollbackFor = SecurityException.class)
    public Map.Entry<String, SessaoUsuario> entrarPorPin(long ru, String pin) {
        Usuario operador = usuarioRepository.buscarPorRu(ru)
                .filter(usuario -> usuario.isAtivo())
                .filter(usuario -> usuario.getPinHash() != null)
                .filter(usuario -> operaCaixa(usuario))
                .orElseThrow(() -> new IllegalArgumentException("Atendente nao encontrado."));
        if (!autenticacaoService.conferirPin(operador, pin)) {
            logRepository.salvar(new LogAcao(0, operador.getRu(), "LOGIN_CELULAR_FALHOU",
                    "PIN errado no celular para " + operador.getNomeCompleto()));
            throw new SecurityException("PIN incorreto.");
        }
        LocalDateTime agora = LocalDateTime.now(relogio);
        LocalDateTime expiraEm = agora.plus(duracao);
        sessaoRepository.removerExpiradas(agora);
        String token = gerarToken();
        SessaoUsuario sessao = new SessaoUsuario(hash(token), operador, agora, expiraEm);
        sessao.trocarOperador(operador);
        SessaoUsuario salva = sessaoRepository.salvar(sessao);
        logRepository.salvar(new LogAcao(0, operador.getRu(), "LOGIN_CELULAR",
                operador.getNomeCompleto() + " entrou pelo celular"));
        return Map.entry(token, Objects.requireNonNull(salva, "Sessao persistida"));
    }

    private static boolean operaCaixa(Usuario usuario) {
        var classe = usuario.getClasse();
        return classe != null
                && (classe.possuiPermissao(Permissao.VER_VENDAS)
                || classe.possuiPermissao(Permissao.VER_FINANCAS));
    }

    private String gerarToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        aleatorio.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean tokenValido(String token) {
        return token != null && token.length() == TOKEN_CARACTERES
                && FORMATO_TOKEN.matcher(token).matches();
    }

    static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
