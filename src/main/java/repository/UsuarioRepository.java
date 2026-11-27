package repository;

import model.Usuario;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository {

    Usuario salvar(Usuario usuario);

    Optional<Usuario> buscarPorNomeSobrenomeESenha(
            String nome, String sobrenome, String senha);

    Optional<Usuario> buscarPorRu(long ru);

    /**
     * Lista usuarios ativos pelo nome e sobrenome.
     * A senha e conferida posteriormente contra o hash.
     */
    default List<Usuario> listarAtivosPorNome(
            String nome, String sobrenome) {

        if (nome == null || sobrenome == null) {
            return List.of();
        }

        String nomeLimpo = nome.trim();
        String sobrenomeLimpo = sobrenome.trim();

        return listarTodos().stream()
                .filter(usuario -> usuario.isAtivo())
                .filter(usuario ->
                        usuario.getNome().equalsIgnoreCase(nomeLimpo)
                                && usuario.getSobrenome()
                                        .equalsIgnoreCase(sobrenomeLimpo))
                .toList();
    }

    List<Usuario> listarTodos();
}
