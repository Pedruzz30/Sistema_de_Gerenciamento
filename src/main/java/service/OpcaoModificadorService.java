package service;

import model.GrupoModificador;
import model.LogAcao;
import model.OpcaoModificador;
import model.OpcaoModificadorComponente;
import model.PoliticaFaltaIngrediente;
import model.Produto;
import model.TipoEfeitoModificador;
import model.UnidadeMedida;
import model.Usuario;
import org.springframework.transaction.annotation.Transactional;
import repository.GrupoModificadorRepository;
import repository.LogRepository;
import repository.OpcaoModificadorComponenteRepository;
import repository.OpcaoModificadorRepository;
import repository.ProdutoRepository;
import repository.UnidadeMedidaRepository;

import java.math.BigDecimal;
import java.util.List;

@Transactional(readOnly = true)
public class OpcaoModificadorService {

    private final GrupoModificadorRepository grupoRepository;
    private final OpcaoModificadorRepository opcaoRepository;
    private final OpcaoModificadorComponenteRepository componenteRepository;
    private final ProdutoRepository produtoRepository;
    private final UnidadeMedidaRepository unidadeRepository;
    private final LogRepository logRepository;

    public OpcaoModificadorService(GrupoModificadorRepository grupoRepository,
                                   OpcaoModificadorRepository opcaoRepository,
                                   OpcaoModificadorComponenteRepository componenteRepository,
                                   ProdutoRepository produtoRepository,
                                   UnidadeMedidaRepository unidadeRepository,
                                   LogRepository logRepository) {
        this.grupoRepository = grupoRepository;
        this.opcaoRepository = opcaoRepository;
        this.componenteRepository = componenteRepository;
        this.produtoRepository = produtoRepository;
        this.unidadeRepository = unidadeRepository;
        this.logRepository = logRepository;
    }

    @Transactional
    public OpcaoModificador criarOpcao(long grupoId,
                                       String nome,
                                       String descricao,
                                       String codigo,
                                       BigDecimal acrescimoPreco,
                                       Integer quantidadeMaximaPorItem,
                                       Boolean selecionadaPorPadrao,
                                       Boolean ativa,
                                       Integer ordemExibicao,
                                       Usuario usuario) {
        GrupoModificador grupo = buscarGrupo(grupoId);
        validarCodigoUnico(grupoId, codigo, null);
        OpcaoModificador opcao = opcaoRepository.salvar(new OpcaoModificador(
                0,
                grupo,
                nome,
                descricao,
                codigo,
                acrescimoPreco,
                quantidadeMaximaPorItem,
                selecionadaPorPadrao,
                ativa,
                ordemExibicao,
                usuario == null ? null : usuario.getRu()
        ));
        auditar(usuario, "OPCAO_MODIFICADOR_CRIADA", "Opcao de modificador criada: " + opcao.getNome());
        return opcao;
    }

    @Transactional
    public OpcaoModificador atualizarOpcao(long opcaoId,
                                           String nome,
                                           String descricao,
                                           String codigo,
                                           BigDecimal acrescimoPreco,
                                           Integer quantidadeMaximaPorItem,
                                           Boolean selecionadaPorPadrao,
                                           Boolean ativa,
                                           Integer ordemExibicao,
                                           Usuario usuario) {
        OpcaoModificador opcao = buscarOpcao(opcaoId);
        validarCodigoUnico(opcao.getGrupoModificador().getId(), codigo, opcaoId);
        opcao.atualizar(opcao.getGrupoModificador(), nome, descricao, codigo, acrescimoPreco,
                quantidadeMaximaPorItem, selecionadaPorPadrao, ativa, ordemExibicao);
        opcao = opcaoRepository.salvar(opcao);
        auditar(usuario, "OPCAO_MODIFICADOR_ATUALIZADA", "Opcao de modificador atualizada: " + opcao.getNome());
        return opcao;
    }

    @Transactional
    public OpcaoModificador alterarStatus(long opcaoId, boolean ativa, Usuario usuario) {
        OpcaoModificador opcao = buscarOpcao(opcaoId);
        opcao.alterarStatus(ativa);
        opcao = opcaoRepository.salvar(opcao);
        auditar(usuario, "OPCAO_MODIFICADOR_STATUS", "Opcao de modificador " + (ativa ? "ativada: " : "inativada: ") + opcao.getNome());
        return opcao;
    }

    @Transactional
    public OpcaoModificadorComponente criarComponente(long opcaoId,
                                                      TipoEfeitoModificador tipoEfeito,
                                                      Integer produtoOrigemId,
                                                      Integer produtoDestinoId,
                                                      BigDecimal quantidadeBase,
                                                      Long unidadeBaseId,
                                                      BigDecimal multiplicador,
                                                      PoliticaFaltaIngrediente politicaFalta,
                                                      String observacao,
                                                      Integer ordem,
                                                      Usuario usuario) {
        OpcaoModificador opcao = buscarOpcao(opcaoId);
        Produto origem = produtoOrigemId == null ? null : buscarProduto(produtoOrigemId);
        Produto destino = produtoDestinoId == null ? null : buscarProduto(produtoDestinoId);
        UnidadeMedida unidade = unidadeBaseId == null ? null : buscarUnidade(unidadeBaseId);
        validarUnidadeComProdutos(unidade, origem, destino, tipoEfeito);
        OpcaoModificadorComponente componente = componenteRepository.salvar(new OpcaoModificadorComponente(
                0,
                opcao,
                tipoEfeito,
                origem,
                destino,
                quantidadeBase,
                unidade,
                multiplicador,
                politicaFalta,
                observacao,
                ordem
        ));
        auditar(usuario, "COMPONENTE_MODIFICADOR_CRIADO", "Componente criado para opcao " + opcao.getNome());
        return componente;
    }

    @Transactional
    public OpcaoModificadorComponente atualizarComponente(long opcaoId,
                                                          long componenteId,
                                                          TipoEfeitoModificador tipoEfeito,
                                                          Integer produtoOrigemId,
                                                          Integer produtoDestinoId,
                                                          BigDecimal quantidadeBase,
                                                          Long unidadeBaseId,
                                                          BigDecimal multiplicador,
                                                          PoliticaFaltaIngrediente politicaFalta,
                                                          String observacao,
                                                          Integer ordem,
                                                          Usuario usuario) {
        OpcaoModificador opcao = buscarOpcao(opcaoId);
        OpcaoModificadorComponente componente = componenteRepository.buscarPorId(componenteId)
                .orElseThrow(() -> new ErroNegocioException("EFEITO_MODIFICADOR_INVALIDO", "Componente de modificador nao encontrado."));
        if (!componente.getOpcaoModificador().equals(opcao)) {
            throw new ErroNegocioException("EFEITO_MODIFICADOR_INVALIDO", "Componente nao pertence a opcao informada.");
        }
        Produto origem = produtoOrigemId == null ? null : buscarProduto(produtoOrigemId);
        Produto destino = produtoDestinoId == null ? null : buscarProduto(produtoDestinoId);
        UnidadeMedida unidade = unidadeBaseId == null ? null : buscarUnidade(unidadeBaseId);
        validarUnidadeComProdutos(unidade, origem, destino, tipoEfeito);
        componente.atualizar(opcao, tipoEfeito, origem, destino, quantidadeBase, unidade,
                multiplicador, politicaFalta, observacao, ordem);
        componente = componenteRepository.salvar(componente);
        auditar(usuario, "COMPONENTE_MODIFICADOR_ATUALIZADO", "Componente atualizado para opcao " + opcao.getNome());
        return componente;
    }

    @Transactional
    public void removerComponente(long opcaoId, long componenteId, Usuario usuario) {
        OpcaoModificador opcao = buscarOpcao(opcaoId);
        OpcaoModificadorComponente componente = componenteRepository.buscarPorId(componenteId)
                .orElseThrow(() -> new ErroNegocioException("EFEITO_MODIFICADOR_INVALIDO", "Componente de modificador nao encontrado."));
        if (!componente.getOpcaoModificador().equals(opcao)) {
            throw new ErroNegocioException("EFEITO_MODIFICADOR_INVALIDO", "Componente nao pertence a opcao informada.");
        }
        componenteRepository.remover(componente);
        auditar(usuario, "COMPONENTE_MODIFICADOR_REMOVIDO", "Componente removido da opcao " + opcao.getNome());
    }

    public OpcaoModificador buscarOpcao(long opcaoId) {
        return opcaoRepository.buscarPorId(opcaoId)
                .orElseThrow(() -> new ErroNegocioException(
                        "OPCAO_MODIFICADOR_NAO_ENCONTRADA",
                        "Opcao de modificador nao encontrada."
                ));
    }

    public List<OpcaoModificador> listarPorGrupo(long grupoId) {
        return opcaoRepository.listarPorGrupo(grupoId);
    }

    public List<OpcaoModificadorComponente> listarComponentes(long opcaoId) {
        return componenteRepository.listarPorOpcao(opcaoId);
    }

    private GrupoModificador buscarGrupo(long grupoId) {
        return grupoRepository.buscarPorId(grupoId)
                .orElseThrow(() -> new ErroNegocioException(
                        "GRUPO_MODIFICADOR_NAO_ENCONTRADO",
                        "Grupo de modificador nao encontrado."
                ));
    }

    private Produto buscarProduto(int produtoId) {
        return produtoRepository.buscarPorId(produtoId)
                .orElseThrow(() -> new ErroNegocioException("ESTOQUE_PRODUTO_NAO_CONFIGURADO", "Produto nao encontrado."));
    }

    private UnidadeMedida buscarUnidade(long unidadeId) {
        return unidadeRepository.buscarPorId(unidadeId)
                .orElseThrow(() -> new ErroNegocioException("UNIDADE_INCOMPATIVEL", "Unidade de medida nao encontrada."));
    }

    private void validarCodigoUnico(long grupoId, String codigo, Long ignorarId) {
        if (codigo != null && opcaoRepository.existeCodigoNoGrupo(grupoId, codigo, ignorarId)) {
            throw new ErroNegocioException("CONFIGURACAO_ITEM_INVALIDA", "Codigo de opcao ja existe no grupo.");
        }
    }

    private void validarUnidadeComProdutos(UnidadeMedida unidade,
                                           Produto origem,
                                           Produto destino,
                                           TipoEfeitoModificador tipoEfeito) {
        if (tipoEfeito == TipoEfeitoModificador.SEM_IMPACTO_ESTOQUE) {
            return;
        }
        if (origem != null && origem.getUnidadeBase() != null && unidade != null
                && origem.getUnidadeBase().getDimensao() != unidade.getDimensao()) {
            throw new ErroNegocioException("UNIDADE_INCOMPATIVEL", "Unidade incompatível com produto de origem.");
        }
        if (destino != null && destino.getUnidadeBase() != null && unidade != null
                && destino.getUnidadeBase().getDimensao() != unidade.getDimensao()) {
            throw new ErroNegocioException("UNIDADE_INCOMPATIVEL", "Unidade incompatível com produto de destino.");
        }
    }

    private void auditar(Usuario usuario, String acao, String descricao) {
        logRepository.salvar(new LogAcao(0, usuario == null ? null : usuario.getRu(), acao, descricao));
    }
}
