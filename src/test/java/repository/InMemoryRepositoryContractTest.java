package repository;

import model.CategoriaCardapio;
import model.CodigoVenda;
import model.Fornecedor;
import model.ItemCardapio;
import model.Produto;
import model.TipoComposicaoVariacao;
import model.TipoItemCardapio;
import model.VariacaoItemCardapio;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class InMemoryRepositoryContractTest {

    @Test
    void codigoDeVendaComIdExistenteNaoEhSobrescritoPelaSequencia() {
        var categoria = new CategoriaCardapio(1, "TESTE", "Teste", 1, true);
        var item = new ItemCardapio(1, "TESTE", "Teste", null, BigDecimal.ONE, categoria,
                TipoItemCardapio.PREPARADO_SOB_DEMANDA, true, true, 1, null);
        var variacao = new VariacaoItemCardapio(1, item, "Grande", "GRANDE", BigDecimal.ONE,
                TipoComposicaoVariacao.PREPARADO, null, true, true, 1);
        var repository = new InMemoryCodigoVendaRepository();
        var existente = new CodigoVenda("100", CodigoVenda.Tipo.INTERNO, variacao, false);
        existente.atribuirIdGerado(1);
        repository.salvar(existente);
        var novo = repository.salvar(new CodigoVenda("100", CodigoVenda.Tipo.INTERNO, variacao, true));

        assertThat(novo.getId()).isEqualTo(2);
        assertThat(repository.buscarPorId(1)).containsSame(existente);
        assertThat(repository.listarAtivosPorCodigo("100")).containsExactly(novo, existente);
    }

    @Test
    void codigoDeVendaEMovimentoRejeitamValorNulo() {
        assertThatIllegalArgumentException().isThrownBy(() -> new InMemoryCodigoVendaRepository().salvar(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new InMemoryMovimentoEstoqueRepository().salvar(null));
    }

    @Test
    void produtoAtualizadoMantemOrdemPorIdDoAdapter() {
        ProdutoRepository repository = new InMemoryProdutoRepository();
        assertThat(repository.listarTodos()).isEmpty();
        assertThat(repository.buscarPorId(-1)).isEmpty();

        repository.salvar(new Produto(1, "Primeiro", 1, 0, BigDecimal.ONE));
        repository.salvar(new Produto(2, "Segundo", 1, 0, BigDecimal.ONE));
        repository.salvar(new Produto(1, "Atualizado", 2, 0, BigDecimal.ONE));

        assertThat(repository.listarTodos()).satisfiesExactly(
                produto -> assertThat(produto.getId()).isEqualTo(1),
                produto -> assertThat(produto.getId()).isEqualTo(2));
        assertThat(repository.buscarPorId(1)).hasValueSatisfying(p -> assertThat(p.getNome()).isEqualTo("Atualizado"));
    }

    @Test
    void fornecedorAtualizadoMantemOrdemPorIdDoAdapter() {
        FornecedorRepository repository = new InMemoryFornecedorRepository();
        assertThat(repository.buscarPorCnpj(null)).isEmpty();
        assertThat(repository.buscarPorCnpj(" ")).isEmpty();
        repository.salvar(new Fornecedor(1, "Primeiro", "11.222.333/0001-81", null));
        repository.salvar(new Fornecedor(2, "Segundo", "04.252.011/0001-10", null));
        repository.salvar(new Fornecedor(1, "Atualizado", "11.222.333/0001-81", null));

        assertThat(repository.listarTodos()).satisfiesExactly(
                fornecedor -> assertThat(fornecedor.getId()).isEqualTo(1),
                fornecedor -> assertThat(fornecedor.getId()).isEqualTo(2));
        assertThat(repository.listarAtivos()).isEqualTo(repository.listarTodos());
        assertThat(repository.buscarPorCnpj("11222333000181")).hasValueSatisfying(f ->
                assertThat(f.getNome()).isEqualTo("Atualizado"));
    }
}
