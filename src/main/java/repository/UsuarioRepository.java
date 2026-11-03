package repository;

import model.Usuario;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository {

    Usuario salvar(Usuario usuario);

    Optional<Usuario> buscarPorNomeSobrenomeESenha(String nome, String sobrenome, String senha);

    Optional<Usuario> buscarPorRu(long ru);

    /**
     * Security-critical lookup: must be implemented explicitly by each adapter.
     * Production adapter uses the indexed supabase_auth_user_id column, never
     * an in-memory scan of every operational user.
     */
    Optional<Usuario> buscarPorSupabaseAuthUserId(UUID userId);

    List<Usuario> listarTodos();
}
