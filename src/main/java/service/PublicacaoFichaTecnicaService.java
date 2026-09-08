package service;

import model.FichaTecnica;
import model.FichaTecnicaCustoSnapshot;
import model.FichaTecnicaIngrediente;
import model.FichaTecnicaIngredienteCustoSnapshot;
import model.FichaTecnicaVersao;
import model.LogAcao;
import model.StatusFichaTecnicaVersao;
import model.Usuario;
import org.springframework.transaction.annotation.Transactional;
import repository.FichaTecnicaCustoSnapshotRepository;
import repository.FichaTecnicaIngredienteCustoSnapshotRepository;
import repository.FichaTecnicaIngredienteRepository;
import repository.FichaTecnicaRepository;
import repository.FichaTecnicaVersaoRepository;
import repository.LogRepository;

import java.util.List;
import java.util.Map;

public class PublicacaoFichaTecnicaService {

    private final FichaTecnicaRepository fichaRepository;
    private final FichaTecnicaVersaoRepository versaoRepository;
    private final FichaTecnicaIngredienteRepository ingredienteRepository;
    private final FichaTecnicaCustoSnapshotRepository custoSnapshotRepository;
    private final FichaTecnicaIngredienteCustoSnapshotRepository ingredienteSnapshotRepository;
    private final CalculadoraCustoFichaTecnicaService calculadoraCusto;
    private final LogRepository logRepository;

    public PublicacaoFichaTecnicaService(FichaTecnicaRepository fichaRepository,
                                         FichaTecnicaVersaoRepository versaoRepository,
                                         FichaTecnicaIngredienteRepository ingredienteRepository,
                                         FichaTecnicaCustoSnapshotRepository custoSnapshotRepository,
                                         FichaTecnicaIngredienteCustoSnapshotRepository ingredienteSnapshotRepository,
                                         CalculadoraCustoFichaTecnicaService calculadoraCusto,
                                         LogRepository logRepository) {
        this.fichaRepository = fichaRepository;
        this.versaoRepository = versaoRepository;
        this.ingredienteRepository = ingredienteRepository;
        this.custoSnapshotRepository = custoSnapshotRepository;
        this.ingredienteSnapshotRepository = ingredienteSnapshotRepository;
        this.calculadoraCusto = calculadoraCusto;
        this.logRepository = logRepository;
    }

    @Transactional
    public FichaTecnicaVersao publicar(long fichaId,
                                       long versaoId,
                                       Long filialReferenciaId,
                                       String motivo,
                                       Usuario usuario) {
        FichaTecnica ficha = fichaRepository.buscarPorIdComLock(fichaId)
                .orElseThrow(() -> new ErroNegocioException(
                        "FICHA_TECNICA_NAO_ENCONTRADA",
                        "Ficha tecnica nao encontrada."
                ));
        FichaTecnicaVersao versao = versaoRepository.buscarPorId(versaoId)
                .orElseThrow(() -> new ErroNegocioException(
                        "FICHA_TECNICA_NAO_ENCONTRADA",
                        "Versao da ficha tecnica nao encontrada."
                ));
        if (versao.getFichaTecnica().getId() != fichaId) {
            throw new ErroNegocioException("FICHA_TECNICA_NAO_ENCONTRADA", "Versao nao pertence a ficha informada.");
        }
        if (!ficha.isAtiva()) {
            throw new ErroNegocioException("FICHA_VERSAO_NAO_PUBLICAVEL", "Ficha tecnica inativa nao pode ser publicada.");
        }
        if (!ficha.getVariacaoItemCardapio().isAtiva()) {
            throw new ErroNegocioException("VARIACAO_INATIVA", "Variacao inativa nao pode ter ficha publicada.");
        }
        if (!ficha.getVariacaoItemCardapio().isPreparada()) {
            throw new ErroNegocioException(
                    "VARIACAO_PRODUTO_DIRETO_NAO_ACEITA_FICHA",
                    "Variacao de produto direto nao aceita ficha tecnica."
            );
        }
        if (versao.getStatus() != StatusFichaTecnicaVersao.RASCUNHO) {
            throw new ErroNegocioException(
                    "FICHA_VERSAO_NAO_PUBLICAVEL",
                    "Apenas versao em rascunho pode ser publicada."
            );
        }

        List<FichaTecnicaIngrediente> ingredientes = ingredienteRepository.listarPorVersao(versaoId);
        if (ingredientes.isEmpty()) {
            throw new ErroNegocioException("FICHA_SEM_INGREDIENTES", "Ficha tecnica precisa de pelo menos um ingrediente.");
        }

        CustoFichaTecnicaResultado custo = calculadoraCusto.calcular(
                versao,
                filialReferenciaId,
                ficha.getVariacaoItemCardapio().getPrecoVenda()
        );
        if (!custo.custoCompleto()) {
            throw new ErroNegocioException(
                    "CUSTO_FICHA_INCOMPLETO",
                    "Ficha tecnica possui ingredientes sem custo medio disponivel.",
                    Map.of("ingredientesPendentes", custo.ingredientesPendentes())
            );
        }

        FichaTecnicaCustoSnapshot snapshot = custoSnapshotRepository.salvar(new FichaTecnicaCustoSnapshot(
                0,
                versao,
                filialReferenciaId,
                custo.custoTotal(),
                custo.custoPorUnidadeRendimento(),
                custo.calculadoEm()
        ));
        for (CustoFichaTecnicaResultado.IngredienteCusto ingredienteCusto : custo.ingredientes()) {
            ingredienteSnapshotRepository.salvar(new FichaTecnicaIngredienteCustoSnapshot(
                    0,
                    snapshot,
                    ingredienteCusto.ingrediente(),
                    ingredienteCusto.ingrediente().getProduto(),
                    ingredienteCusto.quantidadeBrutaBase(),
                    ingredienteCusto.custoMedioUnitario(),
                    ingredienteCusto.custoTotal()
            ));
        }

        FichaTecnicaVersao anterior = ficha.getVersaoPublicadaAtual();
        if (anterior != null && anterior.getId() != versao.getId()) {
            anterior.substituir();
            versaoRepository.salvar(anterior);
        }
        versao.publicar(usuario == null ? null : usuario.getRu(), motivo);
        versao = versaoRepository.salvar(versao);
        ficha.definirVersaoPublicadaAtual(versao);
        fichaRepository.salvar(ficha);
        auditar(usuario, "FICHA_TECNICA_PUBLICADA",
                "Ficha " + fichaId + " publicada na versao " + versao.getNumeroVersao() + ".");
        return versao;
    }

    private void auditar(Usuario usuario, String acao, String descricao) {
        logRepository.salvar(new LogAcao(0, usuario == null ? null : usuario.getRu(), acao, descricao));
    }
}
