package service;

import model.LogAcao;
import model.Usuario;
import org.springframework.transaction.annotation.Transactional;
import repository.LogRepository;
import repository.UsuarioRepository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Confere senha e PIN. Senhas ficam como hash BCrypt; as antigas, em texto puro,
 * são convertidas na inicialização ({@link #migrarSenhasAntigas}) ou no primeiro
 * login, e passam a exigir troca, porque já ficaram expostas no banco.
 */
public class AutenticacaoService {

    private final UsuarioRepository usuarioRepository;
    private final LogRepository logRepository;
    private final SenhaService senhaService;
    private final LimiteTentativas limite;

    public AutenticacaoService(UsuarioRepository usuarioRepository, LogRepository logRepository) {
        this(usuarioRepository, logRepository, new SenhaService(), new LimiteTentativas());
    }

    public AutenticacaoService(UsuarioRepository usuarioRepository,
                               LogRepository logRepository,
                               SenhaService senhaService,
                               LimiteTentativas limite) {
        this.usuarioRepository = usuarioRepository;
        this.logRepository = logRepository;
        this.senhaService = senhaService;
        this.limite = limite;
    }

    @Transactional
    public Optional<Usuario> autenticar(String nome, String sobrenome, String senha) {
        Optional<Usuario> usuarioOpt = conferirCredenciais(nome, sobrenome, senha);

        if (usuarioOpt.isPresent()) {
            Usuario usuario = usuarioOpt.get();
            logRepository.salvar(new LogAcao(
                    0,
                    usuario.getRu(),
                    "LOGIN_SUCESSO",
                    "Login efetuado por " + usuario.getNomeCompleto()
            ));
            return usuarioOpt;
        }

        logRepository.salvar(new LogAcao(
                0,
                null,
                "LOGIN_FALHA",
                "Falha de login para nome=" + nome + " sobrenome=" + sobrenome
        ));
        return Optional.empty();
    }

    /**
     * Confere nome, sobrenome e senha, sem registrar login. Usado também quando o
     * gerente autoriza uma operação na hora. Respeita o limite de tentativas.
     */
    @Transactional
    public Optional<Usuario> conferirCredenciais(String nome, String sobrenome, String senha) {
        if (nome == null || sobrenome == null || senha == null) {
            return Optional.empty();
        }
        String chave = "login:" + nome.trim().toLowerCase(Locale.ROOT) + "|" + sobrenome.trim().toLowerCase(Locale.ROOT);
        limite.verificar(chave);

        List<Usuario> candidatos = usuarioRepository.listarAtivosPorNome(nome, sobrenome);
        for (Usuario candidato : candidatos) {
            if (senhaService.confere(senha, candidato.getSenhaInterna())) {
                limite.registrarSucesso(chave);
                if (!senhaService.ehHash(candidato.getSenhaInterna())) {
                    candidato.definirSenha(senhaService.gerarHash(senha), true);
                    candidato = usuarioRepository.salvar(candidato);
                }
                return Optional.of(candidato);
            }
        }
        limite.registrarFalha(chave);
        return Optional.empty();
    }

    @Transactional
    public Usuario trocarSenha(Usuario usuario, String senhaAtual, String novaSenha) {
        String chave = "senha:" + usuario.getRu();
        limite.verificar(chave);
        if (!senhaService.confere(senhaAtual, usuario.getSenhaInterna())) {
            limite.registrarFalha(chave);
            throw new IllegalArgumentException("A senha atual não confere.");
        }
        limite.registrarSucesso(chave);
        senhaService.validarNovaSenha(novaSenha);
        if (novaSenha.equals(senhaAtual)) {
            throw new IllegalArgumentException("A nova senha precisa ser diferente da atual.");
        }
        usuario.definirSenha(senhaService.gerarHash(novaSenha), false);
        Usuario salvo = usuarioRepository.salvar(usuario);
        logRepository.salvar(new LogAcao(0, salvo.getRu(), "SENHA_ALTERADA", "Senha alterada por " + salvo.getNomeCompleto()));
        return salvo;
    }

    /** Definir o PIN pede a senha: quem só sabe o PIN não pode trocá-lo. */
    @Transactional
    public Usuario definirPin(Usuario usuario, String senhaAtual, String pin) {
        String chave = "senha:" + usuario.getRu();
        limite.verificar(chave);
        if (!senhaService.confere(senhaAtual, usuario.getSenhaInterna())) {
            limite.registrarFalha(chave);
            throw new IllegalArgumentException("A senha não confere.");
        }
        limite.registrarSucesso(chave);
        senhaService.validarPin(pin);
        usuario.definirPinHash(senhaService.gerarHash(pin));
        Usuario salvo = usuarioRepository.salvar(usuario);
        logRepository.salvar(new LogAcao(0, salvo.getRu(), "PIN_ALTERADO", "PIN definido por " + salvo.getNomeCompleto()));
        return salvo;
    }

    /** Respeita o limite: 5 erros bloqueiam o PIN daquela pessoa por 5 minutos. */
    public boolean conferirPin(Usuario usuario, String pin) {
        String chave = "pin:" + usuario.getRu();
        limite.verificar(chave);
        if (usuario.getPinHash() != null && senhaService.confere(pin, usuario.getPinHash())) {
            limite.registrarSucesso(chave);
            return true;
        }
        limite.registrarFalha(chave);
        return false;
    }

    /** Converte senhas em texto puro. Retorna quantas foram convertidas. */
    @Transactional
    public int migrarSenhasAntigas() {
        int convertidas = 0;
        for (Usuario usuario : usuarioRepository.listarTodos()) {
            if (!senhaService.ehHash(usuario.getSenhaInterna())) {
                usuario.definirSenha(senhaService.gerarHash(usuario.getSenhaInterna()), true);
                usuarioRepository.salvar(usuario);
                convertidas++;
            }
        }
        return convertidas;
    }
}
