package service;

import model.GrupoModificador;
import model.LogAcao;
import model.Usuario;
import model.VariacaoGrupoModificador;
import model.VariacaoItemCardapio;
import org.springframework.transaction.annotation.Transactional;
import repository.GrupoModificadorRepository;
import repository.LogRepository;
import repository.VariacaoGrupoModificadorRepository;
import repository.VariacaoItemCardapioRepository;

import java.util.List;

@Transactional(readOnly = true)
public class AssociacaoVariacaoModificadorService {

    private final VariacaoItemCardapioRepository variacaoRepository;
    private final GrupoModificadorRepository grupoRepository;
    private final VariacaoGrupoModificadorRepository associacaoRepository;
    private final LogRepository logRepository;

    public AssociacaoVariacaoModificadorService(VariacaoItemCardapioRepository variacaoRepository,
                                                GrupoModificadorRepository grupoRepository,
                                                VariacaoGrupoModificadorRepository associacaoRepository,
                                                LogRepository logRepository) {
        this.variacaoRepository = variacaoRepository;
        this.grupoRepository = grupoRepository;
        this.associacaoRepository = associacaoRepository;
        this.logRepository = logRepository;
    }

    @Transactional
    public VariacaoGrupoModificador associar(long variacaoId,
                                             long grupoId,
                                             Boolean obrigatorioOverride,
                                             Integer quantidadeMinimaOverride,
                                             Integer quantidadeMaximaOverride,
                                             Integer ordemExibicao,
                                             Boolean ativo,
                                             Usuario usuario) {
        VariacaoItemCardapio variacao = buscarVariacao(variacaoId);
        GrupoModificador grupo = buscarGrupo(grupoId);
        VariacaoGrupoModificador associacao = associacaoRepository
                .buscarPorVariacaoEGrupo(variacaoId, grupoId)
                .orElse(new VariacaoGrupoModificador(
                        0,
                        variacao,
                        grupo,
                        obrigatorioOverride,
                        quantidadeMinimaOverride,
                        quantidadeMaximaOverride,
                        ordemExibicao,
                        ativo
                ));
        if (associacao.getId() != 0) {
            associacao.atualizar(variacao, grupo, obrigatorioOverride,
                    quantidadeMinimaOverride, quantidadeMaximaOverride, ordemExibicao, ativo);
        }
        associacao = associacaoRepository.salvar(associacao);
        auditar(usuario, "VARIACAO_GRUPO_MODIFICADOR_ASSOCIADO",
                "Grupo " + grupo.getNome() + " associado a variacao " + variacao.getNome());
        return associacao;
    }

    @Transactional
    public VariacaoGrupoModificador atualizar(long variacaoId,
                                              long associacaoId,
                                              Boolean obrigatorioOverride,
                                              Integer quantidadeMinimaOverride,
                                              Integer quantidadeMaximaOverride,
                                              Integer ordemExibicao,
                                              Boolean ativo,
                                              Usuario usuario) {
        VariacaoItemCardapio variacao = buscarVariacao(variacaoId);
        VariacaoGrupoModificador associacao = associacaoRepository.buscarPorId(associacaoId)
                .orElseThrow(() -> new ErroNegocioException("GRUPO_NAO_ASSOCIADO_VARIACAO", "Associacao nao encontrada."));
        if (associacao.getVariacaoItemCardapio().getId() != variacaoId) {
            throw new ErroNegocioException("GRUPO_NAO_ASSOCIADO_VARIACAO", "Associacao nao pertence a variacao.");
        }
        associacao.atualizar(variacao, associacao.getGrupoModificador(), obrigatorioOverride,
                quantidadeMinimaOverride, quantidadeMaximaOverride, ordemExibicao, ativo);
        associacao = associacaoRepository.salvar(associacao);
        auditar(usuario, "VARIACAO_GRUPO_MODIFICADOR_ATUALIZADO",
                "Associacao de grupo atualizada na variacao " + variacao.getNome());
        return associacao;
    }

    @Transactional
    public void inativar(long variacaoId, long associacaoId, Usuario usuario) {
        VariacaoGrupoModificador associacao = associacaoRepository.buscarPorId(associacaoId)
                .orElseThrow(() -> new ErroNegocioException("GRUPO_NAO_ASSOCIADO_VARIACAO", "Associacao nao encontrada."));
        if (associacao.getVariacaoItemCardapio().getId() != variacaoId) {
            throw new ErroNegocioException("GRUPO_NAO_ASSOCIADO_VARIACAO", "Associacao nao pertence a variacao.");
        }
        associacao.inativar();
        associacaoRepository.salvar(associacao);
        auditar(usuario, "VARIACAO_GRUPO_MODIFICADOR_REMOVIDO",
                "Associacao de grupo inativada na variacao " + associacao.getVariacaoItemCardapio().getNome());
    }

    public List<VariacaoGrupoModificador> listarPorVariacao(long variacaoId) {
        return associacaoRepository.listarPorVariacao(variacaoId);
    }

    private VariacaoItemCardapio buscarVariacao(long variacaoId) {
        return variacaoRepository.buscarPorId(variacaoId)
                .orElseThrow(() -> new ErroNegocioException("VARIACAO_NAO_ENCONTRADA", "Variacao nao encontrada."));
    }

    private GrupoModificador buscarGrupo(long grupoId) {
        return grupoRepository.buscarPorId(grupoId)
                .orElseThrow(() -> new ErroNegocioException("GRUPO_MODIFICADOR_NAO_ENCONTRADO", "Grupo nao encontrado."));
    }

    private void auditar(Usuario usuario, String acao, String descricao) {
        logRepository.salvar(new LogAcao(0, usuario == null ? null : usuario.getRu(), acao, descricao));
    }
}
