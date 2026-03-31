package service;

import model.ClasseFuncionario;
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
 *
 * Responsabilidades:
 *  - Cadastrar novos fornecedores (sem duplicar CNPJ)
 *  - Vincular produtos a fornecedores
 *  - Desativar fornecedores (nunca deletar — histórico de notas depende deles)
 *  - Consultar fornecedores ativos e por ID
 */
public class FornecedorService {

    private final FornecedorRepository fornecedorRepository;
    private final LogRepository logRepository;

    // Sequência interna de IDs — AtomicLong garante segurança sob concorrência
    private final AtomicLong proximoId = new AtomicLong(1);

    public FornecedorService(FornecedorRepository fornecedorRepository,
                             LogRepository logRepository) {
        this.fornecedorRepository = fornecedorRepository;
        this.logRepository = logRepository;
    }

    // ── Cadastro ───────────────────────────────────────────────

    /**
     * Cadastra um novo fornecedor no sistema.
     *
     * Valida que:
     *  - O usuário tem permissão de GERENCIAR_FUNCIONARIOS (mesma permissão de gestão)
     *  - Não existe outro fornecedor com o mesmo CNPJ
     */
    public Fornecedor cadastrarFornecedor(Usuario usuarioLogado,
                                          String nome,
                                          String cnpj,
                                          String telefone) {
        validarPermissao(usuarioLogado);

        // CNPJ duplicado = erro antes de salvar qualquer coisa
        Optional<Fornecedor> existente = fornecedorRepository.buscarPorCnpj(cnpj);
        if (existente.isPresent()) {
            throw new IllegalArgumentException(
                    "Já existe um fornecedor cadastrado com o CNPJ: " + cnpj
            );
        }

        Fornecedor novoFornecedor = new Fornecedor(proximoId.getAndIncrement(), nome, cnpj, telefone);
        fornecedorRepository.salvar(novoFornecedor);

        logRepository.salvar(new LogAcao(
                0,
                usuarioLogado.getRu(),
                "FORNECEDOR_CADASTRADO",
                "Fornecedor cadastrado: " + novoFornecedor.getNome() +
                        " (CNPJ: " + cnpj + ")"
        ));

        return novoFornecedor;
    }

    // ── Vinculação de produtos ─────────────────────────────────

    /**
     * Vincula um produto a um fornecedor.
     *
     * Isso representa: "esse fornecedor vende esse produto".
     * Na hora de criar uma nota fiscal, só produtos vinculados
     * podem ser adicionados.
     */
    public void vincularProduto(Usuario usuarioLogado,
                                long fornecedorId,
                                Produto produto) {
        validarPermissao(usuarioLogado);

        Fornecedor fornecedor = buscarOuLancarExcecao(fornecedorId);

        fornecedor.adicionarProduto(produto);
        fornecedorRepository.salvar(fornecedor);

        logRepository.salvar(new LogAcao(
                0,
                usuarioLogado.getRu(),
                "PRODUTO_VINCULADO",
                "Produto '" + produto.getNome() + "' vinculado ao fornecedor '"
                        + fornecedor.getNome() + "'"
        ));
    }

    // ── Desativação ────────────────────────────────────────────

    /**
     * Desativa um fornecedor.
     *
     * Por que desativar em vez de deletar?
     * Porque as notas fiscais antigas guardam referência ao fornecedor.
     * Se você deletasse, o histórico financeiro ficaria inconsistente.
     * Desativar mantém o histórico intacto e impede novas notas.
     */
    public void desativarFornecedor(Usuario usuarioLogado, long fornecedorId) {
        validarPermissao(usuarioLogado);

        Fornecedor fornecedor = buscarOuLancarExcecao(fornecedorId);

        if (!fornecedor.isAtivo()) {
            throw new IllegalStateException(
                    "Fornecedor '" + fornecedor.getNome() + "' já está inativo."
            );
        }

        fornecedor.desativar();
        fornecedorRepository.salvar(fornecedor);

        logRepository.salvar(new LogAcao(
                0,
                usuarioLogado.getRu(),
                "FORNECEDOR_DESATIVADO",
                "Fornecedor desativado: " + fornecedor.getNome() +
                        " (ID: " + fornecedorId + ")"
        ));
    }

    // ── Reativação ─────────────────────────────────────────────

    /**
     * Reativa um fornecedor previamente desativado.
     *
     * Útil quando uma parceria é retomada — você não precisa
     * recadastrar tudo do zero, só reativa.
     */
    public void reativarFornecedor(Usuario usuarioLogado, long fornecedorId) {
        validarPermissao(usuarioLogado);

        Fornecedor fornecedor = buscarOuLancarExcecao(fornecedorId);

        if (fornecedor.isAtivo()) {
            throw new IllegalStateException(
                    "Fornecedor '" + fornecedor.getNome() + "' já está ativo."
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

    // ── Consultas ──────────────────────────────────────────────

    public Fornecedor buscarPorId(long id) {
        return buscarOuLancarExcecao(id);
    }

    public List<Fornecedor> listarAtivos() {
        return fornecedorRepository.listarAtivos();
    }

    public List<Fornecedor> listarTodos() {
        return fornecedorRepository.listarTodos();
    }

    // ── Utilitários internos ───────────────────────────────────

    /**
     * Busca o fornecedor ou lança exceção clara se não existir.
     *
     * Esse padrão — buscar e já lançar se não achar — evita repetir
     * o mesmo bloco if/orElseThrow em cada método do service.
     */
    private Fornecedor buscarOuLancarExcecao(long id) {
        return fornecedorRepository.buscarPorId(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Fornecedor não encontrado com ID: " + id
                ));
    }

    private void validarPermissao(Usuario usuario) {
        if (usuario == null
                || usuario.getClasse() == null
                || !usuario.getClasse().possuiPermissao(Permissao.GERENCIAR_FUNCIONARIOS)) {
            throw new SecurityException(
                    "Você não tem permissão para gerenciar fornecedores."
            );
        }
    }
}