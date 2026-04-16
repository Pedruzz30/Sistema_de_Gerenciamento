package service;

import model.ClasseFuncionario;
import model.LogAcao;
import model.Permissao;
import model.Usuario;
import org.springframework.transaction.annotation.Transactional;
import repository.LogRepository;
import repository.UsuarioRepository;

import java.util.List;

@Transactional(readOnly = true)
public class CadastroFuncionarioService {

    private final UsuarioRepository usuarioRepository;
    private final LogRepository logRepository;

    public CadastroFuncionarioService(UsuarioRepository usuarioRepository, LogRepository logRepository) {
        this.usuarioRepository = usuarioRepository;
        this.logRepository = logRepository;
    }

    @Transactional
    public Usuario cadastrarFuncionario(Usuario superiorLogado,
                                        String nome,
                                        String sobrenome,
                                        String cpf,
                                        String senhaInicial,
                                        ClasseFuncionario classeDestino) {
        validarPermissaoGerenciamento(superiorLogado);

        // O RU é gerado automaticamente pelo repositório quando informado como 0.
        Usuario novoUsuario = new Usuario(
                0,
                nome,
                sobrenome,
                cpf,
                senhaInicial,
                classeDestino,
                Usuario.Perfil.OPERADOR
        );
        boolean cpfJaCadastrado = usuarioRepository.listarTodos().stream()
                .anyMatch(usuario -> usuario.getCpfBruto().equals(novoUsuario.getCpfBruto()));
        if (cpfJaCadastrado) {
            throw new IllegalArgumentException("Já existe um funcionário cadastrado com este CPF.");
        }
        Usuario salvo = usuarioRepository.salvar(novoUsuario);

        // Registro de log para auditoria de cadastro de funcionário.
        logRepository.salvar(new LogAcao(
                0,
                superiorLogado.getRu(),
                "CADASTRO_FUNCIONARIO",
                "Funcionário cadastrado: " + salvo.getNomeCompleto() + " (RU " + salvo.getRu() + ")"
        ));

        return salvo;
    }

    public List<Usuario> listarFuncionarios(Usuario superiorLogado) {
        validarPermissaoGerenciamento(superiorLogado);
        return usuarioRepository.listarTodos();
    }

    @Transactional
    public Usuario mudarClasse(Usuario superiorLogado, long ruFuncionario, ClasseFuncionario novaClasse) {
        validarPermissaoGerenciamento(superiorLogado);

        Usuario usuario = usuarioRepository.buscarPorRu(ruFuncionario)
                .orElseThrow(() -> new IllegalArgumentException("Funcionário não encontrado."));

        // Mudança de classe para promoção/rebaixamento.
        usuario.alterarClasse(novaClasse);
        Usuario salvo = usuarioRepository.salvar(usuario);

        logRepository.salvar(new LogAcao(
                0,
                superiorLogado.getRu(),
                "MUDAR_CLASSE_FUNCIONARIO",
                "Classe alterada para " + novaClasse.getNome() + " do funcionário RU " + ruFuncionario
        ));

        return salvo;
    }

    @Transactional
    public void desativarFuncionario(Usuario superiorLogado, long ruFuncionario) {
        validarPermissaoGerenciamento(superiorLogado);

        Usuario usuario = usuarioRepository.buscarPorRu(ruFuncionario)
                .orElseThrow(() -> new IllegalArgumentException("Funcionário não encontrado."));

        // Desativação para representar demissão/bloqueio de acesso.
        usuario.desativar();
        usuarioRepository.salvar(usuario);

        logRepository.salvar(new LogAcao(
                0,
                superiorLogado.getRu(),
                "DESATIVAR_FUNCIONARIO",
                "Funcionário desativado: " + usuario.getNomeCompleto() + " (RU " + ruFuncionario + ")"
        ));
    }

    private void validarPermissaoGerenciamento(Usuario usuario) {
        if (usuario == null
                || usuario.getClasse() == null
                || !usuario.getClasse().possuiPermissao(Permissao.GERENCIAR_FUNCIONARIOS)) {
            throw new SecurityException("Você não tem permissão para executar esta ação.");
        }
    }
}
