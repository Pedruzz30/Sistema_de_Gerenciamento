package repository;

import model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JpaUsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByNomeIgnoreCaseAndSobrenomeIgnoreCaseAndSenhaAndAtivoTrue(
            String nome,
            String sobrenome,
            String senha
    );

    List<Usuario> findAllByOrderByRuAsc();
}
