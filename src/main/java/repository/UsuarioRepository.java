package repository;

import model.Usuario;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository {

    Usuario salvar(Usuario usuario);

    Optional<Usuario> buscarPorNomeSobrenomeESenha(String nome, String sobrenome, String senha);

    Optional<Usuario> buscarPorRu(long ru);

    /** Ativos com este nome e sobrenome. A senha é conferida depois, contra o hash. */
    default List<Usuario> listarAtivosPorNome(String nome, String sobrenome) {
        if (nome == null || sobrenome == null) {
            return List.of();
        }
        String nomeLimpo = nome.trim();
        String sobrenomeLimpo = sobrenome.trim();
        return listarTodos().stream()
                .filter(Usuario::isAtivo)
                .filter(u -> u.getNome().equalsIgnoreCase(nomeLimpo) && u.getSobrenome().equalsIgnoreCase(sobrenomeLimpo))
                .toList();
    }

    List<Usuario> listarTodos();
}
