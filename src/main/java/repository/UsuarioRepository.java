package repository;

import model.Usuario;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository {

    Usuario salvar(Usuario usuario);

    Optional<Usuario> buscarPorNomeSobrenomeESenha(String nome, String sobrenome, String senha);

    Optional<Usuario> buscarPorRu(long ru);

    /** Auth identity is an explicit administrator-provisioned UUID link, never an email match. */
    default Optional<Usuario> buscarPorSupabaseAuthUserId(UUID userId) {
        if (userId == null) return Optional.empty();
        return listarTodos().stream()
                .filter(u -> userId.equals(u.getSupabaseAuthUserId()))
                .findFirst();
    }

    List<Usuario> listarTodos();
}
