package service;

import model.FichaTecnica;
import model.FichaTecnicaIngrediente;
import model.FichaTecnicaVersao;
import model.OrigemComposicaoVenda;
import model.Produto;
import model.StatusFichaTecnicaVersao;
import model.TipoComposicaoVariacao;
import model.UnidadeMedida;
import model.VariacaoItemCardapio;
import repository.FichaTecnicaIngredienteRepository;
import repository.FichaTecnicaRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ResolucaoComposicaoVariacaoService {

    private static final int ESCALA_QUANTIDADE = 6;

    private final FichaTecnicaRepository fichaTecnicaRepository;
    private final FichaTecnicaIngredienteRepository ingredienteRepository;

    public ResolucaoComposicaoVariacaoService(FichaTecnicaRepository fichaTecnicaRepository,
                                              FichaTecnicaIngredienteRepository ingredienteRepository) {
        this.fichaTecnicaRepository = fichaTecnicaRepository;
        this.ingredienteRepository = ingredienteRepository;
    }

    public ResolucaoComposicao resolverObrigatoria(VariacaoItemCardapio variacao) {
        ResolucaoComposicao resolucao = resolverVariacoes(List.of(variacao)).get(variacao.getId());
        if (resolucao == null || !resolucao.completa()) {
            throw new ErroNegocioException(
                    resolucao == null ? "CONFIGURACAO_INCOMPLETA" : resolucao.codigoErro(),
                    resolucao == null ? "Composicao da variacao nao encontrada." : resolucao.mensagemErro()
            );
        }
        return resolucao;
    }

    public Map<Long, ResolucaoComposicao> resolverVariacoes(Collection<VariacaoItemCardapio> variacoes) {
        if (variacoes == null || variacoes.isEmpty()) {
            return Map.of();
        }

        List<VariacaoItemCardapio> lista = variacoes.stream()
                .filter(v -> v != null)
                .toList();
        Map<Long, ResolucaoComposicao> resultado = new LinkedHashMap<>();
        List<Long> preparadasIds = lista.stream()
                .filter(v -> v.getTipoComposicao() == TipoComposicaoVariacao.PREPARADO)
                .map(VariacaoItemCardapio::getId)
                .toList();

        Map<Long, FichaTecnica> fichaPorVariacao = fichaTecnicaRepository.listarAtivasPorVariacoes(preparadasIds).stream()
                .collect(Collectors.toMap(
                        ficha -> ficha.getVariacaoItemCardapio().getId(),
                        Function.identity(),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
        List<Long> versaoIds = fichaPorVariacao.values().stream()
                .map(FichaTecnica::getVersaoPublicadaAtual)
                .filter(v -> v != null && v.getStatus() == StatusFichaTecnicaVersao.PUBLICADA)
                .map(FichaTecnicaVersao::getId)
                .toList();
        Map<Long, List<FichaTecnicaIngrediente>> ingredientesPorVersao = ingredienteRepository.listarPorVersoes(versaoIds).stream()
                .collect(Collectors.groupingBy(
                        ingrediente -> ingrediente.getFichaTecnicaVersao().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        for (VariacaoItemCardapio variacao : lista) {
            if (variacao.getTipoComposicao() == TipoComposicaoVariacao.PRODUTO_DIRETO) {
                resultado.put(variacao.getId(), resolverProdutoDireto(variacao));
            } else {
                resultado.put(variacao.getId(), resolverPreparado(variacao, fichaPorVariacao, ingredientesPorVersao));
            }
        }
        return resultado;
    }

    private ResolucaoComposicao resolverProdutoDireto(VariacaoItemCardapio variacao) {
        Produto produto = variacao.getProdutoDireto();
        if (produto == null) {
            return incompleta(variacao, "PRODUTO_DIRETO_NAO_CONFIGURADO",
                    "Variacao de produto direto nao possui produto configurado.");
        }
        UnidadeMedida unidadeBase = produto.getUnidadeBase();
        if (unidadeBase == null) {
            return incompleta(variacao, "ESTOQUE_PRODUTO_NAO_CONFIGURADO",
                    "Produto direto nao possui unidade-base configurada.");
        }
        BigDecimal quantidade = variacao.getQuantidadeProdutoDiretoBase();
        if (quantidade == null || quantidade.compareTo(BigDecimal.ZERO) <= 0) {
            return incompleta(variacao, "CONFIGURACAO_INCOMPLETA",
                    "Quantidade consumida pela variacao direta deve ser maior que zero.");
        }
        return completa(
                variacao,
                null,
                null,
                List.of(new ComponenteComposicao(
                        produto,
                        unidadeBase,
                        quantidade.setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP),
                        OrigemComposicaoVenda.PRODUTO_DIRETO,
                        null
                ))
        );
    }

    private ResolucaoComposicao resolverPreparado(VariacaoItemCardapio variacao,
                                                  Map<Long, FichaTecnica> fichaPorVariacao,
                                                  Map<Long, List<FichaTecnicaIngrediente>> ingredientesPorVersao) {
        FichaTecnica ficha = fichaPorVariacao.get(variacao.getId());
        if (ficha == null || !ficha.isAtiva()) {
            return incompleta(variacao, "FICHA_TECNICA_NAO_ENCONTRADA",
                    "Variacao preparada nao possui ficha tecnica ativa.");
        }
        FichaTecnicaVersao versao = ficha.getVersaoPublicadaAtual();
        if (versao == null) {
            return incompleta(variacao, "FICHA_SEM_VERSAO_PUBLICADA",
                    "Ficha tecnica nao possui versao publicada vigente.");
        }
        if (versao.getStatus() != StatusFichaTecnicaVersao.PUBLICADA) {
            return incompleta(variacao, "FICHA_SEM_VERSAO_PUBLICADA",
                    "Versao vigente da ficha tecnica nao esta publicada.");
        }
        if (versao.getRendimentoQuantidade() == null || versao.getRendimentoQuantidade().compareTo(BigDecimal.ZERO) <= 0) {
            return incompleta(variacao, "CONFIGURACAO_INCOMPLETA",
                    "Rendimento da ficha tecnica e invalido.");
        }

        List<FichaTecnicaIngrediente> ingredientes = ingredientesPorVersao.getOrDefault(versao.getId(), List.of());
        if (ingredientes.isEmpty()) {
            return incompleta(variacao, "FICHA_SEM_INGREDIENTES", "Ficha publicada nao possui ingredientes.");
        }

        List<ComponenteComposicao> componentes = new ArrayList<>();
        for (FichaTecnicaIngrediente ingrediente : ingredientes) {
            Produto produto = ingrediente.getProduto();
            if (produto == null || produto.getUnidadeBase() == null) {
                return incompleta(variacao, "CONFIGURACAO_INCOMPLETA",
                        "Ingrediente da ficha nao possui produto ou unidade-base configurada.");
            }
            BigDecimal porUnidade = ingrediente.getQuantidadeBrutaBase()
                    .divide(versao.getRendimentoQuantidade(), ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
            componentes.add(new ComponenteComposicao(
                    produto,
                    produto.getUnidadeBase(),
                    porUnidade,
                    OrigemComposicaoVenda.FICHA_TECNICA,
                    ingrediente
            ));
        }
        return completa(variacao, ficha, versao, componentes);
    }

    private static ResolucaoComposicao completa(VariacaoItemCardapio variacao,
                                                FichaTecnica ficha,
                                                FichaTecnicaVersao versao,
                                                List<ComponenteComposicao> componentes) {
        return new ResolucaoComposicao(variacao, true, null, null, ficha, versao, componentes);
    }

    private static ResolucaoComposicao incompleta(VariacaoItemCardapio variacao, String codigo, String mensagem) {
        return new ResolucaoComposicao(variacao, false, codigo, mensagem, null, null, List.of());
    }

    public record ResolucaoComposicao(
            VariacaoItemCardapio variacao,
            boolean completa,
            String codigoErro,
            String mensagemErro,
            FichaTecnica fichaTecnica,
            FichaTecnicaVersao fichaTecnicaVersao,
            List<ComponenteComposicao> componentes
    ) {}

    public record ComponenteComposicao(
            Produto produto,
            UnidadeMedida unidadeBase,
            BigDecimal quantidadeBasePorUnidadeComercial,
            OrigemComposicaoVenda origem,
            FichaTecnicaIngrediente ingrediente
    ) {}
}
