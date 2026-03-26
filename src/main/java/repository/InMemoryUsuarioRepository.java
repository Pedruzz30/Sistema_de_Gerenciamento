package repository;

import model.Usuario;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryUsuarioRepository implements UsuarioRepository {

    private final List<Usuario> usuarios = new ArrayList<>();
    private final AtomicLong sequenceRu = new AtomicLong(1);

    @Override
    public Usuario salvar(Usuario usuario) {
        if (usuario == null) {
            throw new IllegalArgumentException("Usuário não pode ser nulo.");
        }

        // Quando RU é <= 0, gera um RU automático preservando os demais dados.
        Usuario persistido = usuario;
        if (usuario.getRu() <= 0) {
            persistido = new Usuario(
                    sequenceRu.getAndIncrement(),
                    usuario.getNome(),
                    usuario.getSobrenome(),
                    usuario.getCpf(),
                    usuario.getSenhaInterna(),
                    usuario.getClasse(),
                    usuario.getPerfil()
            );
            if (!usuario.isAtivo()) {
                persistido.desativar();
            }
        } else {
            sequenceRu.updateAndGet(atual -> Math.max(atual, usuario.getRu() + 1));
        }

        Optional<Usuario> existente = buscarPorRu(persistido.getRu());
        existente.ifPresent(usuarios::remove);
        usuarios.add(persistido);
        return persistido;
    }

    @Override
    public Optional<Usuario> buscarPorNomeSobrenomeESenha(String nome, String sobrenome, String senha) {
        return usuarios.stream()
                .filter(Usuario::isAtivo)
                .filter(usuario -> usuario.autenticar(nome, sobrenome, senha))
                .findFirst();
    }

    @Override
    public Optional<Usuario> buscarPorRu(long ru) {
        return usuarios.stream().filter(usuario -> usuario.getRu() == ru).findFirst();
    }

    @Override
    public List<Usuario> listarTodos() {
        return new ArrayList<>(usuarios);
    }
}

