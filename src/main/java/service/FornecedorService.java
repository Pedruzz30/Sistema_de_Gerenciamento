package service;

import model.Fornecedor;
import model.LogAcao;
import model.Permissao;
import model.Produto;
import model.Usuario;
import repository.FornecedorRepository;
import repository.LogRepository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Gerencia o ciclo de vida dos fornecedores.
 */
public class FornecedorService {

    private final FornecedorRepository fornecedorRepository;
    private final LogRepository logRepository;
    private final AtomicLong proximoId = new AtomicLong(1);

    public FornecedorService(FornecedorRepository fornecedorRepository,
                             LogRepository logRepository) {
        this.fornecedorRepository = fornecedorRepository;
        this.logRepository = logRepository;
    }

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

        Fornecedor novoFornecedor = new Fornecedor(proximoId.getAndIncrement(), nome, cnpj, telefone);
        fornecedorRepository.salvar(novoFornecedor);

        logRepository.salvar(new LogAcao(
                0,
                usuarioLogado.getRu(),
                "FORNECEDOR_CADASTRADO",
                "Fornecedor cadastrado: " + novoFornecedor.getNome() + " (CNPJ: " + cnpj + ")"
        ));

        return novoFornecedor;
    }

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
    public boolean garantirProdutoVinculadoDuranteNota(long fornecedorId,
                                                       Produto produto,
                                                       Long usuarioRu) {
        return garantirProdutoVinculadoInterno(fornecedorId, produto, usuarioRu);
    }

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
