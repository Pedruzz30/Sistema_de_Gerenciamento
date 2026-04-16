package repository;

import model.Usuario;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UsuarioRepositoryAdapter implements UsuarioRepository {

    private final JpaUsuarioRepository delegate;

    public UsuarioRepositoryAdapter(JpaUsuarioRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Usuario salvar(Usuario usuario) {
        return delegate.save(usuario);
    }

    @Override
    public Optional<Usuario> buscarPorNomeSobrenomeESenha(String nome, String sobrenome, String senha) {
        if (nome == null || sobrenome == null || senha == null) {
            return Optional.empty();
        }
        return delegate.findByNomeIgnoreCaseAndSobrenomeIgnoreCaseAndSenhaAndAtivoTrue(
                nome.trim(),
                sobrenome.trim(),
                senha
        );
    }

    @Override
    public Optional<Usuario> buscarPorRu(long ru) {
        return delegate.findById(ru);
    }

    @Override
    public List<Usuario> listarTodos() {
        return delegate.findAllByOrderByRuAsc();
    }
}
