package service;

import model.LogAcao;
import model.Usuario;
import org.springframework.transaction.annotation.Transactional;
import repository.LogRepository;
import repository.UsuarioRepository;

import java.util.Optional;

@Transactional(readOnly = true)
public class AutenticacaoService {

    private final UsuarioRepository usuarioRepository;
    private final LogRepository logRepository;

    public AutenticacaoService(UsuarioRepository usuarioRepository, LogRepository logRepository) {
        this.usuarioRepository = usuarioRepository;
        this.logRepository = logRepository;
    }

    @Transactional
    public Optional<Usuario> autenticar(String nome, String sobrenome, String senha) {
        Optional<Usuario> usuarioOpt = usuarioRepository.buscarPorNomeSobrenomeESenha(nome, sobrenome, senha);

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
}
