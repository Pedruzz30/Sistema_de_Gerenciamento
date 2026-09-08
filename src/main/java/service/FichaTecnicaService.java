package service;

import model.FichaTecnica;
import model.FichaTecnicaIngrediente;
import model.FichaTecnicaVersao;
import model.LogAcao;
import model.StatusFichaTecnicaVersao;
import model.Usuario;
import model.VariacaoItemCardapio;
import org.springframework.transaction.annotation.Transactional;
import repository.FichaTecnicaIngredienteRepository;
import repository.FichaTecnicaRepository;
import repository.FichaTecnicaVersaoRepository;
import repository.LogRepository;

import java.math.BigDecimal;
import java.util.List;

public class FichaTecnicaService {

    private final VariacaoItemCardapioService variacaoService;
    private final FichaTecnicaRepository fichaRepository;
    private final FichaTecnicaVersaoRepository versaoRepository;
    private final FichaTecnicaIngredienteRepository ingredienteRepository;
    private final UnidadeMedidaService unidadeMedidaService;
    private final LogRepository logRepository;

    public FichaTecnicaService(VariacaoItemCardapioService variacaoService,
                               FichaTecnicaRepository fichaRepository,
                               FichaTecnicaVersaoRepository versaoRepository,
                               FichaTecnicaIngredienteRepository ingredienteRepository,
                               UnidadeMedidaService unidadeMedidaService,
                               LogRepository logRepository) {
        this.variacaoService = variacaoService;
        this.fichaRepository = fichaRepository;
        this.versaoRepository = versaoRepository;
        this.ingredienteRepository = ingredienteRepository;
        this.unidadeMedidaService = unidadeMedidaService;
        this.logRepository = logRepository;
    }

    @Transactional
    public FichaTecnicaVersao criarFicha(long variacaoId,
                                         String nome,
                                         BigDecimal rendimentoQuantidade,
                                         Long rendimentoUnidadeId,
                                         BigDecimal quantidadePorPorcao,
                                         Integer tempoPreparoMinutos,
                                         String observacaoInterna,
                                         Usuario usuario) {
        VariacaoItemCardapio variacao = variacaoService.buscarObrigatoria(variacaoId);
        if (!variacao.isPreparada()) {
            throw new ErroNegocioException(
                    "VARIACAO_PRODUTO_DIRETO_NAO_ACEITA_FICHA",
                    "Variacao de produto direto nao aceita ficha tecnica."
            );
        }
        fichaRepository.buscarAtivaPorVariacao(variacaoId).ifPresent(ficha -> {
            throw new ErroNegocioException("FICHA_TECNICA_JA_EXISTE", "Ja existe ficha tecnica ativa para esta variacao.");
        });

        FichaTecnica ficha = fichaRepository.salvar(
                new FichaTecnica(0, variacao, nome, usuario == null ? null : usuario.getRu())
        );
        FichaTecnicaVersao versao = new FichaTecnicaVersao(
                0,
                ficha,
                1,
                rendimentoQuantidade,
                unidadeMedidaService.buscarPorIdObrigatorio(rendimentoUnidadeId),
                quantidadePorPorcao,
                tempoPreparoMinutos,
                observacaoInterna,
                null,
                usuario == null ? null : usuario.getRu()
        );
        versao = versaoRepository.salvar(versao);
        auditar(usuario, "FICHA_TECNICA_CRIADA",
                "Ficha tecnica " + ficha.getNome() + " criada para variacao " + variacao.getId() + ".");
        return versao;
    }

    @Transactional(readOnly = true)
    public FichaTecnica buscarFichaObrigatoria(long fichaId) {
        return fichaRepository.buscarPorId(fichaId)
                .orElseThrow(() -> new ErroNegocioException(
                        "FICHA_TECNICA_NAO_ENCONTRADA",
                        "Ficha tecnica nao encontrada."
                ));
    }

    @Transactional(readOnly = true)
    public FichaTecnicaVersao buscarVersaoObrigatoria(long fichaId, long versaoId) {
        FichaTecnicaVersao versao = versaoRepository.buscarPorId(versaoId)
                .orElseThrow(() -> new ErroNegocioException(
                        "FICHA_TECNICA_NAO_ENCONTRADA",
                        "Versao da ficha tecnica nao encontrada."
                ));
        if (versao.getFichaTecnica().getId() != fichaId) {
            throw new ErroNegocioException("FICHA_TECNICA_NAO_ENCONTRADA", "Versao nao pertence a ficha informada.");
        }
        return versao;
    }

    @Transactional(readOnly = true)
    public List<FichaTecnicaVersao> listarVersoes(long fichaId) {
        buscarFichaObrigatoria(fichaId);
        return versaoRepository.listarPorFicha(fichaId);
    }

    @Transactional
    public FichaTecnicaVersao atualizarRascunho(long fichaId,
                                                long versaoId,
                                                BigDecimal rendimentoQuantidade,
                                                Long rendimentoUnidadeId,
                                                BigDecimal quantidadePorPorcao,
                                                Integer tempoPreparoMinutos,
                                                String observacaoInterna,
                                                String motivoAlteracao,
                                                Usuario usuario) {
        FichaTecnicaVersao versao = buscarVersaoObrigatoria(fichaId, versaoId);
        versao.atualizarRascunho(
                rendimentoQuantidade,
                unidadeMedidaService.buscarPorIdObrigatorio(rendimentoUnidadeId),
                quantidadePorPorcao,
                tempoPreparoMinutos,
                observacaoInterna,
                motivoAlteracao
        );
        versao = versaoRepository.salvar(versao);
        auditar(usuario, "FICHA_RASCUNHO_ALTERADO",
                "Rascunho " + versao.getId() + " alterado.");
        return versao;
    }

    @Transactional
    public FichaTecnicaVersao novaVersao(long fichaId, Usuario usuario) {
        FichaTecnica ficha = fichaRepository.buscarPorIdComLock(fichaId)
                .orElseThrow(() -> new ErroNegocioException(
                        "FICHA_TECNICA_NAO_ENCONTRADA",
                        "Ficha tecnica nao encontrada."
                ));
        versaoRepository.buscarRascunhoAtivoPorFicha(fichaId).ifPresent(rascunho -> {
            throw new ErroNegocioException(
                    "FICHA_RASCUNHO_JA_EXISTE",
                    "Ja existe rascunho ativo para esta ficha."
            );
        });
        FichaTecnicaVersao publicada = ficha.getVersaoPublicadaAtual();
        if (publicada == null || publicada.getStatus() != StatusFichaTecnicaVersao.PUBLICADA) {
            throw new ErroNegocioException(
                    "FICHA_VERSAO_NAO_PUBLICAVEL",
                    "Nao ha versao publicada para copiar."
            );
        }
        FichaTecnicaVersao nova = versaoRepository.salvar(new FichaTecnicaVersao(
                0,
                ficha,
                versaoRepository.proximoNumeroVersao(fichaId),
                publicada.getRendimentoQuantidade(),
                publicada.getRendimentoUnidade(),
                publicada.getQuantidadePorPorcao(),
                publicada.getTempoPreparoMinutos(),
                publicada.getObservacaoInterna(),
                publicada.getMotivoAlteracao(),
                usuario == null ? null : usuario.getRu()
        ));
        for (FichaTecnicaIngrediente ingrediente : ingredienteRepository.listarPorVersao(publicada.getId())) {
            ingredienteRepository.salvar(new FichaTecnicaIngrediente(
                    0,
                    nova,
                    ingrediente.getProduto(),
                    ingrediente.getUnidadeInformada(),
                    ingrediente.getQuantidadeInformada(),
                    ingrediente.getQuantidadeLiquidaBase(),
                    ingrediente.getPercentualPerda(),
                    ingrediente.getQuantidadeBrutaBase(),
                    ingrediente.isIngredienteCritico(),
                    ingrediente.getPoliticaFalta(),
                    ingrediente.getOrdem(),
                    ingrediente.getObservacao()
            ));
        }
        auditar(usuario, "FICHA_NOVA_VERSAO",
                "Nova versao " + nova.getNumeroVersao() + " criada para ficha " + fichaId + ".");
        return nova;
    }

    @Transactional
    public FichaTecnicaVersao descartarRascunho(long fichaId, long versaoId, Usuario usuario) {
        FichaTecnicaVersao versao = buscarVersaoObrigatoria(fichaId, versaoId);
        versao.descartar();
        versao = versaoRepository.salvar(versao);
        auditar(usuario, "FICHA_RASCUNHO_DESCARTADO",
                "Rascunho " + versaoId + " descartado da ficha " + fichaId + ".");
        return versao;
    }

    private void auditar(Usuario usuario, String acao, String descricao) {
        logRepository.salvar(new LogAcao(0, usuario == null ? null : usuario.getRu(), acao, descricao));
    }
}
