package repository;

import model.Usuario;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository {

    Usuario salvar(Usuario usuario);

    Optional<Usuario> buscarPorNomeSobrenomeESenha(String nome, String sobrenome, String senha);

    Optional<Usuario> buscarPorRu(long ru);

    Optional<Usuario> buscarPorAuthUserId(UUID authUserId);

    List<Usuario> listarTodos();
}
