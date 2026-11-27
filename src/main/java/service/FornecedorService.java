package service;

import model.Fornecedor;
import model.LogAcao;
import model.Permissao;
import model.Produto;
import model.Usuario;
import org.springframework.transaction.annotation.Transactional;
import repository.FornecedorRepository;
import repository.LogRepository;

import java.util.List;
import java.util.Objects;

/**
 * Gerencia o ciclo de vida dos fornecedores
 * e seus vinculos com produtos.
 */
@Transactional(readOnly = true)
public class FornecedorService {

    private final FornecedorRepository fornecedorRepository;
    private final LogRepository logRepository;

    public FornecedorService(
            FornecedorRepository fornecedorRepository,
            LogRepository logRepository) {

        this.fornecedorRepository = Objects.requireNonNull(
                fornecedorRepository,
                "fornecedorRepository");

        this.logRepository = Objects.requireNonNull(
                logRepository,
                "logRepository");
    }

    @Transactional
    public Fornecedor cadastrarFornecedor(
            Usuario usuarioLogado,
            String nome,
            String cnpj,
            String telefone) {

        Long usuarioRu = exigirRuAutorizado(usuarioLogado);

        if (fornecedorRepository.buscarPorCnpj(cnpj).isPresent()) {
            throw new IllegalArgumentException(
                    "Ja existe um fornecedor cadastrado com o CNPJ: "
                            + cnpj);
        }

        Fornecedor novoFornecedor = fornecedorRepository.salvar(
                new Fornecedor(0, nome, cnpj, telefone));

        logRepository.salvar(new LogAcao(
                0,
                usuarioRu,
                "FORNECEDOR_CADASTRADO",
                "Fornecedor cadastrado: "
                        + novoFornecedor.getNome()
                        + " (CNPJ: " + cnpj + ")"
        ));

        return novoFornecedor;
    }

    @Transactional
    public void vincularProduto(
            Usuario usuarioLogado,
            long fornecedorId,
            Produto produto) {

        Long usuarioRu = exigirRuAutorizado(usuarioLogado);

        garantirProdutoVinculadoInterno(
                fornecedorId,
                produto,
                usuarioRu);
    }

    /**
     * Garante que o produto esteja vinculado ao fornecedor.
     * Mantem a permissao do fluxo administrativo.
     */
    @Transactional
    public boolean garantirProdutoVinculado(
            Usuario usuarioLogado,
            long fornecedorId,
            Produto produto) {

        Long usuarioRu = exigirRuAutorizado(usuarioLogado);

        return garantirProdutoVinculadoInterno(
                fornecedorId,
                produto,
                usuarioRu);
    }

    /**
     * Variante interna utilizada pelo fluxo de nota fiscal.
     *
     * A autorizacao deve ser garantida pelo servico
     * responsavel pelo recebimento da mercadoria.
     */
    @Transactional
    public boolean garantirProdutoVinculadoDuranteNota(
            long fornecedorId,
            Produto produto,
            Long usuarioRu) {

        return garantirProdutoVinculadoInterno(
                fornecedorId,
                produto,
                usuarioRu);
    }

    @Transactional
    public void desativarFornecedor(
            Usuario usuarioLogado,
            long fornecedorId) {

        Long usuarioRu = exigirRuAutorizado(usuarioLogado);

        Fornecedor fornecedor =
                buscarOuLancarExcecao(fornecedorId);

        if (!fornecedor.isAtivo()) {
            throw new IllegalStateException(
                    "Fornecedor '" + fornecedor.getNome()
                            + "' ja esta inativo.");
        }

        fornecedor.desativar();
        fornecedorRepository.salvar(fornecedor);

        logRepository.salvar(new LogAcao(
                0,
                usuarioRu,
                "FORNECEDOR_DESATIVADO",
                "Fornecedor desativado: "
                        + fornecedor.getNome()
                        + " (ID: " + fornecedorId + ")"
        ));
    }

    @Transactional
    public void reativarFornecedor(
            Usuario usuarioLogado,
            long fornecedorId) {

        Long usuarioRu = exigirRuAutorizado(usuarioLogado);

        Fornecedor fornecedor =
                buscarOuLancarExcecao(fornecedorId);

        if (fornecedor.isAtivo()) {
            throw new IllegalStateException(
                    "Fornecedor '" + fornecedor.getNome()
                            + "' ja esta ativo.");
        }

        fornecedor.reativar();
        fornecedorRepository.salvar(fornecedor);

        logRepository.salvar(new LogAcao(
                0,
                usuarioRu,
                "FORNECEDOR_REATIVADO",
                "Fornecedor reativado: "
                        + fornecedor.getNome()
        ));
    }

    public Fornecedor buscarPorId(long id) {
        return buscarOuLancarExcecao(id);
    }

    public List<Fornecedor> listarAtivos() {
        return fornecedorRepository.listarAtivos();
    }

    public List<Fornecedor> listarTodos() {
        return fornecedorRepository.listarTodos();
    }

    private Fornecedor buscarOuLancarExcecao(long id) {
        return fornecedorRepository.buscarPorId(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Fornecedor nao encontrado com ID: " + id));
    }

    private Long exigirRuAutorizado(Usuario usuario) {

        if (usuario == null) {
            throw new SecurityException(
                    "Voce nao tem permissao para gerenciar fornecedores.");
        }

        var classe = usuario.getClasse();

        if (classe == null
                || !classe.possuiPermissao(
                        Permissao.GERENCIAR_FUNCIONARIOS)) {

            throw new SecurityException(
                    "Voce nao tem permissao para gerenciar fornecedores.");
        }

        return usuario.getRu();
    }

    private boolean garantirProdutoVinculadoInterno(
            long fornecedorId,
            Produto produto,
            Long usuarioRu) {

        if (produto == null) {
            throw new IllegalArgumentException(
                    "Produto nao pode ser nulo.");
        }

        Fornecedor fornecedor =
                buscarOuLancarExcecao(fornecedorId);

        if (fornecedor.possuiProduto(produto.getId())) {
            return false;
        }

        fornecedor.adicionarProduto(produto);
        fornecedorRepository.salvar(fornecedor);

        logRepository.salvar(new LogAcao(
                0,
                usuarioRu,
                "PRODUTO_VINCULADO",
                "Produto '" + produto.getNome()
                        + "' vinculado ao fornecedor '"
                        + fornecedor.getNome() + "'"
        ));

        return true;
    }
}