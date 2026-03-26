package repository;

import model.Usuario;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository {

    Usuario salvar(Usuario usuario);

    Optional<Usuario> buscarPorNomeSobrenomeESenha(String nome, String sobrenome, String senha);

    Optional<Usuario> buscarPorRu(long ru);

    List<Usuario> listarTodos();
}
