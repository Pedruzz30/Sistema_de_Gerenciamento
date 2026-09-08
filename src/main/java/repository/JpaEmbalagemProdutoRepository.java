package repository;

import model.EmbalagemProduto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JpaEmbalagemProdutoRepository extends JpaRepository<EmbalagemProduto, Long> {

    List<EmbalagemProduto> findAllByProdutoIdOrderByNomeAsc(long produtoId);
}
