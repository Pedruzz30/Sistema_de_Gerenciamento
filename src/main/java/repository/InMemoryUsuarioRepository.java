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
    public synchronized Usuario salvar(Usuario usuario) {
        if (usuario == null) {
            throw new IllegalArgumentException(
                    "Usuário não pode ser nulo.");
        }

        Usuario persistido = usuario;

        // Gera um RU automaticamente quando necessario.
        if (usuario.getRu() <= 0) {
            persistido = usuario.comRu(sequenceRu.getAndIncrement());
        } else {
            sequenceRu.updateAndGet(atual ->
                    Math.max(atual, usuario.getRu() + 1));
        }

        final long ruPersistido = persistido.getRu();

        usuarios.removeIf(registro ->
                registro.getRu() == ruPersistido);

        usuarios.add(persistido);

        return persistido;
    }

    @Override
    public synchronized Optional<Usuario> buscarPorNomeSobrenomeESenha(
            String nome, String sobrenome, String senha) {

        return usuarios.stream()
                .filter(usuario -> usuario.isAtivo())
                .filter(usuario ->
                        usuario.autenticar(nome, sobrenome, senha))
                .findFirst();
    }

    @Override
    public synchronized Optional<Usuario> buscarPorRu(long ru) {
        return usuarios.stream()
                .filter(usuario -> usuario.getRu() == ru)
                .findFirst();
    }

    @Override
    public synchronized List<Usuario> listarTodos() {
        return new ArrayList<>(usuarios);
    }
}
