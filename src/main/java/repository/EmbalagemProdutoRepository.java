package repository;

import model.EmbalagemProduto;

import java.util.List;
import java.util.Optional;

public interface EmbalagemProdutoRepository {

    EmbalagemProduto salvar(EmbalagemProduto embalagem);

    Optional<EmbalagemProduto> buscarPorId(long id);

    List<EmbalagemProduto> listarPorProduto(int produtoId);
}
