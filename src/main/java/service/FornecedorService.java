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
import java.util.Optional;

/**
 * Gerencia o ciclo de vida dos fornecedores.
 */
@Transactional(readOnly = true)
public class FornecedorService {

    private final FornecedorRepository fornecedorRepository;
    private final LogRepository logRepository;

    public FornecedorService(FornecedorRepository fornecedorRepository,
                             LogRepository logRepository) {
        this.fornecedorRepository = fornecedorRepository;
        this.logRepository = logRepository;
    }

    @Transactional
    public Fornecedor cadastrarFornecedor(Usuario usuarioLogado,
                                          String nome,
                                          String cnpj,
                                          String telefone) {
        validarPermissao(usuarioLogado);

        Optional<Fornecedor> existente = fornecedorRepository.buscarPorCnpj(cnpj);
        if (existente.isPresent()) {
            throw new IllegalArgumentException(
                    "Ja existe um fornecedor cadastrado com o CNPJ: " + cnpj
            );
        }

        Fornecedor novoFornecedor = fornecedorRepository.salvar(
                new Fornecedor(0, nome, cnpj, telefone)
        );

        logRepository.salvar(new LogAcao(
                0,
                usuarioLogado.getRu(),
                "FORNECEDOR_CADASTRADO",
                "Fornecedor cadastrado: " + novoFornecedor.getNome() + " (CNPJ: " + cnpj + ")"
        ));

        return novoFornecedor;
    }

    @Transactional
    public void vincularProduto(Usuario usuarioLogado,
                                long fornecedorId,
                                Produto produto) {
        validarPermissao(usuarioLogado);
        garantirProdutoVinculadoInterno(fornecedorId, produto, usuarioLogado.getRu());
    }

    /**
     * Garante que o produto esteja vinculado ao fornecedor.
     *
     * Mantem a mesma permissao do fluxo administrativo.
     */
    @Transactional
    public boolean garantirProdutoVinculado(Usuario usuarioLogado,
                                            long fornecedorId,
                                            Produto produto) {
        validarPermissao(usuarioLogado);
        return garantirProdutoVinculadoInterno(fornecedorId, produto, usuarioLogado.getRu());
    }

    /**
     * Variante interna usada pelo fluxo de nota fiscal.
     *
     * Nao exige a permissao administrativa de gerenciar fornecedores porque o
     * vinculo e apenas um efeito colateral do recebimento da mercadoria.
     */
    @Transactional
    public boolean garantirProdutoVinculadoDuranteNota(long fornecedorId,
                                                       Produto produto,
                                                       Long usuarioRu) {
        return garantirProdutoVinculadoInterno(fornecedorId, produto, usuarioRu);
    }

    @Transactional
    public void desativarFornecedor(Usuario usuarioLogado, long fornecedorId) {
        validarPermissao(usuarioLogado);

        Fornecedor fornecedor = buscarOuLancarExcecao(fornecedorId);
        if (!fornecedor.isAtivo()) {
            throw new IllegalStateException(
                    "Fornecedor '" + fornecedor.getNome() + "' ja esta inativo."
            );
        }

        fornecedor.desativar();
        fornecedorRepository.salvar(fornecedor);

        logRepository.salvar(new LogAcao(
                0,
                usuarioLogado.getRu(),
                "FORNECEDOR_DESATIVADO",
                "Fornecedor desativado: " + fornecedor.getNome() + " (ID: " + fornecedorId + ")"
        ));
    }

    @Transactional
    public void reativarFornecedor(Usuario usuarioLogado, long fornecedorId) {
        validarPermissao(usuarioLogado);

        Fornecedor fornecedor = buscarOuLancarExcecao(fornecedorId);
        if (fornecedor.isAtivo()) {
            throw new IllegalStateException(
                    "Fornecedor '" + fornecedor.getNome() + "' ja esta ativo."
            );
        }

        fornecedor.reativar();
        fornecedorRepository.salvar(fornecedor);

        logRepository.salvar(new LogAcao(
                0,
                usuarioLogado.getRu(),
                "FORNECEDOR_REATIVADO",
                "Fornecedor reativado: " + fornecedor.getNome()
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
                        "Fornecedor nao encontrado com ID: " + id
                ));
    }

    private void validarPermissao(Usuario usuario) {
        if (usuario == null
                || usuario.getClasse() == null
                || !usuario.getClasse().possuiPermissao(Permissao.GERENCIAR_FUNCIONARIOS)) {
            throw new SecurityException("Voce nao tem permissao para gerenciar fornecedores.");
        }
    }

    private boolean garantirProdutoVinculadoInterno(long fornecedorId,
                                                    Produto produto,
                                                    Long usuarioRu) {
        if (produto == null) {
            throw new IllegalArgumentException("Produto nao pode ser nulo.");
        }

        Fornecedor fornecedor = buscarOuLancarExcecao(fornecedorId);
        if (fornecedor.possuiProduto(produto.getId())) {
            return false;
        }

        fornecedor.adicionarProduto(produto);
        fornecedorRepository.salvar(fornecedor);

        logRepository.salvar(new LogAcao(
                0,
                usuarioRu,
                "PRODUTO_VINCULADO",
                "Produto '" + produto.getNome() + "' vinculado ao fornecedor '" + fornecedor.getNome() + "'"
        ));

        return true;
    }
}
