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
import java.util.Optional;

/**
 * Sessões de login no servidor. O navegador guarda um token aleatório num cookie
 * que o JavaScript não lê (HttpOnly). A identidade vem daqui, e não mais do
 * cabeçalho X-User-RU, que qualquer um podia forjar.
 */
public class SessaoService {

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
        this.sessaoRepository = sessaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.autenticacaoService = autenticacaoService;
        this.logRepository = logRepository;
        this.duracao = duracao;
        this.relogio = relogio;
    }

    public Duration getDuracao() {
        return duracao;
    }

    /** @return o token que vai para o cookie; só o hash dele fica no banco */
    @Transactional
    public String criar(Usuario usuario) {
        LocalDateTime agora = LocalDateTime.now(relogio);
        sessaoRepository.removerExpiradas(agora);
        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessaoRepository.salvar(new SessaoUsuario(hash(token), usuario, agora, agora.plus(duracao)));
        return token;
    }

    @Transactional(readOnly = true)
    public Optional<SessaoUsuario> resolver(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        LocalDateTime agora = LocalDateTime.now(relogio);
        return sessaoRepository.buscarPorTokenHash(hash(token))
                .filter(sessao -> !sessao.expirada(agora))
                .filter(sessao -> sessao.getUsuario().isAtivo());
    }

    @Transactional
    public void encerrar(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        sessaoRepository.buscarPorTokenHash(hash(token)).ifPresent(sessao -> {
            sessao.encerrar(LocalDateTime.now(relogio));
            sessaoRepository.salvar(sessao);
        });
    }

    /** Quem pode assumir o caixa por PIN. */
    @Transactional(readOnly = true)
    public List<Usuario> listarOperadores() {
        return usuarioRepository.listarTodos().stream()
                .filter(Usuario::isAtivo)
                .filter(usuario -> usuario.getPinHash() != null)
                .filter(SessaoService::operaCaixa)
                .toList();
    }

    /**
     * Troca quem está operando sem sair do PDV. A partir daí, e até um novo login
     * com senha, a sessão só alcança a área de caixa (ver {@code SessaoFilter}).
     */
    @Transactional
    public SessaoUsuario trocarOperador(String token, long ru, String pin) {
        SessaoUsuario sessao = resolver(token)
                .orElseThrow(() -> new SecurityException("Sessao encerrada. Entre de novo."));
        Usuario anterior = sessao.getUsuario();
        Usuario operador = usuarioRepository.buscarPorRu(ru)
                .filter(Usuario::isAtivo)
                .filter(SessaoService::operaCaixa)
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

    private static boolean operaCaixa(Usuario usuario) {
        return usuario.getClasse() != null
                && (usuario.getClasse().possuiPermissao(Permissao.VER_VENDAS)
                || usuario.getClasse().possuiPermissao(Permissao.VER_FINANCAS));
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
