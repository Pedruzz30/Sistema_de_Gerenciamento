package service;

import model.CompraEstoque;
import model.EmbalagemProduto;
import model.EstoqueProduto;
import model.Fornecedor;
import model.ItemCompraEstoque;
import model.MovimentoEstoque;
import model.Permissao;
import model.Produto;
import model.RegistroIdempotencia;
import model.TipoMovimentoEstoque;
import model.UnidadeMedida;
import model.Usuario;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import repository.CompraEstoqueRepository;
import repository.EmbalagemProdutoRepository;
import repository.EstoqueProdutoRepository;
import repository.FornecedorRepository;
import repository.MovimentoEstoqueRepository;
import repository.ProdutoRepository;
import repository.RegistroIdempotenciaRepository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

@Transactional(readOnly = true)
public class CompraEstoqueService {

    private static final String OPERACAO_CONFIRMAR_COMPRA = "CONFIRMAR_COMPRA_ESTOQUE";
    private static final Long FILIAL_PADRAO_ID = ProdutoEstoqueService.FILIAL_PADRAO_ID;

    private final CompraEstoqueRepository compraEstoqueRepository;
    private final ProdutoRepository produtoRepository;
    private final FornecedorRepository fornecedorRepository;
    private final EmbalagemProdutoRepository embalagemProdutoRepository;
    private final EstoqueProdutoRepository estoqueProdutoRepository;
    private final MovimentoEstoqueRepository movimentoEstoqueRepository;
    private final RegistroIdempotenciaRepository registroIdempotenciaRepository;
    private final UnidadeMedidaService unidadeMedidaService;

    public CompraEstoqueService(CompraEstoqueRepository compraEstoqueRepository,
                                ProdutoRepository produtoRepository,
                                FornecedorRepository fornecedorRepository,
                                EmbalagemProdutoRepository embalagemProdutoRepository,
                                EstoqueProdutoRepository estoqueProdutoRepository,
                                MovimentoEstoqueRepository movimentoEstoqueRepository,
                                RegistroIdempotenciaRepository registroIdempotenciaRepository,
                                UnidadeMedidaService unidadeMedidaService) {
        this.compraEstoqueRepository = compraEstoqueRepository;
        this.produtoRepository = produtoRepository;
        this.fornecedorRepository = fornecedorRepository;
        this.embalagemProdutoRepository = embalagemProdutoRepository;
        this.estoqueProdutoRepository = estoqueProdutoRepository;
        this.movimentoEstoqueRepository = movimentoEstoqueRepository;
        this.registroIdempotenciaRepository = registroIdempotenciaRepository;
        this.unidadeMedidaService = unidadeMedidaService;
    }

    @Transactional
    public CompraEstoque criarCompra(Usuario usuario, Long fornecedorId) {
        validarPermissaoEdicao(usuario);
        Fornecedor fornecedor = null;
        if (fornecedorId != null) {
            fornecedor = fornecedorRepository.buscarPorId(fornecedorId)
                    .orElseThrow(() -> new IllegalArgumentException("Fornecedor nao encontrado com ID: " + fornecedorId));
            if (!fornecedor.isAtivo()) {
                throw new IllegalArgumentException("Fornecedor invalido ou inativo.");
            }
        }
        return compraEstoqueRepository.salvar(
                new CompraEstoque(0, FILIAL_PADRAO_ID, fornecedor, usuario != null ? usuario.getRu() : null)
        );
    }

    @Transactional
    public CompraEstoque adicionarItem(Usuario usuario,
                                       long compraId,
                                       int produtoId,
                                       Long embalagemId,
                                       String unidadeCompraCodigo,
                                       BigDecimal quantidadeComprada,
                                       BigDecimal custoTotal,
                                       String motivoEntradaGratuita) {
        validarPermissaoEdicao(usuario);
        CompraEstoque compra = buscarCompraObrigatoria(compraId);
        Produto produto = buscarProdutoObrigatorio(produtoId);
        validarProdutoParaCompra(produto);
        validarCusto(custoTotal, motivoEntradaGratuita);

        boolean temEmbalagem = embalagemId != null && embalagemId > 0;
        boolean temUnidade = unidadeCompraCodigo != null && !unidadeCompraCodigo.isBlank();
        if (temEmbalagem == temUnidade) {
            throw new IllegalArgumentException("Informe embalagem ou unidade de compra, nunca ambos.");
        }

        EmbalagemProduto embalagem = null;
        UnidadeMedida unidadeCompra = null;
        BigDecimal quantidadeBase;
        if (temEmbalagem) {
            embalagem = embalagemProdutoRepository.buscarPorId(embalagemId)
                    .orElseThrow(() -> new IllegalArgumentException("Embalagem nao encontrada com ID: " + embalagemId));
            if (!embalagem.pertenceAoProduto(produto.getId())) {
                throw new IllegalArgumentException("Embalagem nao pertence ao produto informado.");
            }
            BigDecimal quantidadeNaUnidadeDaEmbalagem = quantidadeComprada.multiply(embalagem.getQuantidadeBase());
            quantidadeBase = unidadeMedidaService.converterParaUnidadeBaseProduto(
                    quantidadeNaUnidadeDaEmbalagem,
                    embalagem.getUnidadeBase(),
                    produto.getUnidadeBase()
            );
        } else {
            unidadeCompra = unidadeMedidaService.buscarPorCodigoObrigatorio(unidadeCompraCodigo);
            quantidadeBase = unidadeMedidaService.converterParaUnidadeBaseProduto(
                    quantidadeComprada,
                    unidadeCompra,
                    produto.getUnidadeBase()
            );
        }

        compra.adicionarItem(new ItemCompraEstoque(
                0,
                produto,
                embalagem,
                unidadeCompra,
                quantidadeComprada,
                quantidadeBase,
                custoTotal
        ));
        return compraEstoqueRepository.salvar(compra);
    }

    @Transactional
    public synchronized CompraEstoque confirmarCompra(Usuario usuario,
                                                      long compraId,
                                                      String idempotencyKey,
                                                      String motivo) {
        validarPermissaoEdicao(usuario);
        String chave = normalizarIdempotencyKey(idempotencyKey);
        CompraEstoque compra = buscarCompraObrigatoria(compraId);
        String payloadHash = calcularPayloadHash(compra);

        RegistroIdempotencia registro = registroIdempotenciaRepository
                .buscar(FILIAL_PADRAO_ID, OPERACAO_CONFIRMAR_COMPRA, chave)
                .orElse(null);
        if (registro != null) {
            return resolverRegistroIdempotente(registro, payloadHash);
        }

        registro = reservarIdempotencia(chave, payloadHash, compra.getId());

        if (!compra.estaRascunho()) {
            throw new ErroNegocioException("COMPRA_JA_CONFIRMADA", "Compra ja confirmada ou indisponivel para confirmacao.");
        }
        if (compra.getItens().isEmpty()) {
            throw new IllegalStateException("Nao e possivel confirmar compra sem itens.");
        }

        List<ItemCompraEstoque> itensOrdenados = compra.getItens().stream()
                .sorted(Comparator.comparingInt(item -> item.getProduto().getId()))
                .toList();
        itensOrdenados.forEach(item -> validarProdutoParaCompra(item.getProduto()));

        for (ItemCompraEstoque item : itensOrdenados) {
            EstoqueProduto estoque = bloquearOuCriarEstoque(item.getProduto());
            BigDecimal saldoAnterior = estoque.getQuantidadeAtual();
            BigDecimal custoMedioAnterior = estoque.getCustoMedio();
            BigDecimal valorEstoqueAnterior = estoque.valorEstoque();
            estoque.aplicarEntrada(item.getQuantidadeBase(), item.getCustoUnitarioBase());
            BigDecimal saldoPosterior = estoque.getQuantidadeAtual();
            BigDecimal custoMedioPosterior = estoque.getCustoMedio();
            BigDecimal valorEstoquePosterior = estoque.valorEstoque();
            estoqueProdutoRepository.salvar(estoque);

            movimentoEstoqueRepository.salvar(new MovimentoEstoque(
                    0,
                    FILIAL_PADRAO_ID,
                    item.getProduto(),
                    TipoMovimentoEstoque.ENTRADA_COMPRA,
                    item.getQuantidadeBase(),
                    saldoAnterior,
                    saldoPosterior,
                    item.getCustoUnitarioBase(),
                    item.getCustoTotal(),
                    custoMedioAnterior,
                    custoMedioPosterior,
                    valorEstoqueAnterior,
                    valorEstoquePosterior,
                    "COMPRA_ESTOQUE",
                    compra.getId(),
                    usuario != null ? usuario.getRu() : null,
                    motivoConfirmacao(compra, motivo),
                    null,
                    chave,
                    null
            ));
        }

        compra.confirmar(usuario != null ? usuario.getRu() : null, chave, payloadHash);
        compra = compraEstoqueRepository.salvar(compra);
        registro.concluir(compra.getId());
        registroIdempotenciaRepository.salvar(registro);
        return buscarCompraObrigatoria(compra.getId());
    }

    public CompraEstoque buscarCompraObrigatoria(long compraId) {
        return compraEstoqueRepository.buscarPorId(compraId)
                .orElseThrow(() -> new IllegalArgumentException("Compra nao encontrada com ID: " + compraId));
    }

    public List<CompraEstoque> listarTodas() {
        return compraEstoqueRepository.listarTodos();
    }

    private EstoqueProduto bloquearOuCriarEstoque(Produto produto) {
        return estoqueProdutoRepository.buscarPorFilialEProdutoComLock(FILIAL_PADRAO_ID, produto.getId())
                .orElseGet(() -> estoqueProdutoRepository.salvar(new EstoqueProduto(
                        0,
                        FILIAL_PADRAO_ID,
                        produto,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                )));
    }

    private Produto buscarProdutoObrigatorio(int produtoId) {
        return produtoRepository.buscarPorId(produtoId)
                .filter(Produto::isControladoPorEstoque)
                .orElseThrow(() -> new IllegalArgumentException("Produto nao encontrado com ID: " + produtoId));
    }

    private void validarProdutoParaCompra(Produto produto) {
        if (produto.getUnidadeBase() == null) {
            throw new IllegalArgumentException("Produto precisa de unidade-base para registrar compra decimal.");
        }
        if (!produto.isAtivo()) {
            throw new IllegalArgumentException("Produto inativo nao pode receber compra.");
        }
    }

    private void validarCusto(BigDecimal custoTotal, String motivoEntradaGratuita) {
        if (custoTotal == null || custoTotal.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Custo total deve ser nao negativo.");
        }
        if (custoTotal.compareTo(BigDecimal.ZERO) == 0
                && (motivoEntradaGratuita == null || motivoEntradaGratuita.isBlank())) {
            throw new IllegalArgumentException("Entrada gratuita exige motivo.");
        }
    }

    private static String motivoConfirmacao(CompraEstoque compra, String motivo) {
        if (motivo != null && !motivo.isBlank()) {
            return motivo.trim();
        }
        return "Entrada por compra #" + compra.getId();
    }

    private static String normalizarIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key e obrigatoria para confirmar compra.");
        }
        return idempotencyKey.trim();
    }

    private static String calcularPayloadHash(CompraEstoque compra) {
        StringBuilder payload = new StringBuilder();
        payload.append("compra=").append(compra.getId()).append('|');
        compra.getItens().stream()
                .sorted(Comparator.comparingInt(item -> item.getProduto().getId()))
                .forEach(item -> payload
                        .append(item.getProduto().getId()).append(':')
                        .append(item.getQuantidadeComprada().toPlainString()).append(':')
                        .append(item.getQuantidadeBase().toPlainString()).append(':')
                        .append(item.getCustoTotal().toPlainString()).append(';'));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel para idempotencia.", e);
        }
    }

    private CompraEstoque resolverRegistroIdempotente(RegistroIdempotencia registro, String payloadHash) {
        if (!registro.mesmoPayload(payloadHash)) {
            throw new ConflitoIdempotenciaException("IDEMPOTENCY_KEY_REUSED: mesma chave de idempotencia com payload diferente.");
        }
        if (registro.estaProcessando()) {
            throw new ErroNegocioException(
                    "CONCORRENCIA_ESTOQUE",
                    "Operacao idempotente ainda esta em processamento. Reenvie a mesma requisicao em instantes."
            );
        }
        return compraEstoqueRepository.buscarPorId(registro.getRecursoId())
                .orElseThrow(() -> new IllegalStateException("Compra idempotente nao encontrada."));
    }

    private RegistroIdempotencia reservarIdempotencia(String chave, String payloadHash, long compraId) {
        try {
            return registroIdempotenciaRepository.salvarEFlush(RegistroIdempotencia.iniciar(
                    FILIAL_PADRAO_ID,
                    OPERACAO_CONFIRMAR_COMPRA,
                    chave,
                    payloadHash,
                    compraId
            ));
        } catch (DataIntegrityViolationException e) {
            throw new ErroNegocioException(
                    "CONCORRENCIA_ESTOQUE",
                    "A chave de idempotencia foi registrada por outra transacao concorrente. Reenvie a mesma requisicao."
            );
        }
    }

    private void validarPermissaoEdicao(Usuario usuario) {
        if (usuario == null
                || usuario.getClasse() == null
                || !usuario.getClasse().possuiPermissao(Permissao.EDITAR_ESTOQUE)) {
            throw new SecurityException("Voce nao tem permissao para editar compras e estoque.");
        }
    }
}
