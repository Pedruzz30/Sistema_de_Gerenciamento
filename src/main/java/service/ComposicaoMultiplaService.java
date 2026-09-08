package service;

import model.ConfiguracaoComposicaoMultipla;
import model.ConfiguracaoComposicaoMultiplaParte;
import model.FichaTecnica;
import model.FichaTecnicaVersao;
import model.ItemCardapio;
import model.PoliticaArredondamentoComposicaoMultipla;
import model.PoliticaPrecoComposicaoMultipla;
import model.VariacaoItemCardapio;
import repository.ConfiguracaoComposicaoMultiplaParteRepository;
import repository.ConfiguracaoComposicaoMultiplaRepository;
import repository.VariacaoItemCardapioRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ComposicaoMultiplaService {

    private static final int ESCALA_PROPORCAO = 6;
    private static final int ESCALA_DINHEIRO = 4;

    private final ConfiguracaoComposicaoMultiplaRepository configuracaoRepository;
    private final ConfiguracaoComposicaoMultiplaParteRepository parteRepository;
    private final VariacaoItemCardapioRepository variacaoRepository;
    private final ResolucaoComposicaoVariacaoService resolucaoService;

    public ComposicaoMultiplaService(ConfiguracaoComposicaoMultiplaRepository configuracaoRepository,
                                     ConfiguracaoComposicaoMultiplaParteRepository parteRepository,
                                     VariacaoItemCardapioRepository variacaoRepository,
                                     ResolucaoComposicaoVariacaoService resolucaoService) {
        this.configuracaoRepository = configuracaoRepository;
        this.parteRepository = parteRepository;
        this.variacaoRepository = variacaoRepository;
        this.resolucaoService = resolucaoService;
    }

    public ConfiguracaoComposicaoMultipla salvarConfiguracao(long variacaoId,
                                                             ConfiguracaoInput input) {
        VariacaoItemCardapio variacao = buscarVariacao(variacaoId);
        ConfiguracaoComposicaoMultipla existente = configuracaoRepository.buscarPorVariacao(variacaoId)
                .orElse(null);
        if (existente == null) {
            existente = new ConfiguracaoComposicaoMultipla(
                    0,
                    variacao,
                    input.ativo(),
                    input.minimoPartes(),
                    input.maximoPartes(),
                    normalizarQuantidades(input.quantidadesPermitidas()),
                    input.permiteRepetirPartes(),
                    input.proporcoesIguaisObrigatorias(),
                    input.permiteProporcaoPersonalizada(),
                    input.politicaPreco(),
                    input.politicaArredondamento() == null
                            ? PoliticaArredondamentoComposicaoMultipla.HALF_UP
                            : input.politicaArredondamento()
            );
        } else {
            existente.atualizar(
                    variacao,
                    input.ativo(),
                    input.minimoPartes(),
                    input.maximoPartes(),
                    normalizarQuantidades(input.quantidadesPermitidas()),
                    input.permiteRepetirPartes(),
                    input.proporcoesIguaisObrigatorias(),
                    input.permiteProporcaoPersonalizada(),
                    input.politicaPreco(),
                    input.politicaArredondamento() == null
                            ? PoliticaArredondamentoComposicaoMultipla.HALF_UP
                            : input.politicaArredondamento()
            );
        }
        return configuracaoRepository.salvar(existente);
    }

    public ConfiguracaoComposicaoMultiplaParte salvarPartePermitida(long variacaoId,
                                                                    long variacaoParteId,
                                                                    PartePermitidaInput input) {
        ConfiguracaoComposicaoMultipla configuracao = configuracaoRepository.buscarPorVariacao(variacaoId)
                .orElseThrow(() -> new ErroNegocioException(
                        "COMPOSICAO_MULTISSABOR_NAO_PERMITIDA",
                        "Variacao nao possui configuracao multissabor."
                ));
        VariacaoItemCardapio variacaoParte = buscarVariacao(variacaoParteId);
        ConfiguracaoComposicaoMultiplaParte existente = parteRepository
                .buscarPorConfiguracaoEVariacaoParte(configuracao.getId(), variacaoParteId)
                .orElse(null);
        if (existente == null) {
            existente = new ConfiguracaoComposicaoMultiplaParte(
                    0,
                    configuracao,
                    variacaoParte,
                    input.ativa(),
                    input.ordemExibicao()
            );
        } else {
            existente.atualizar(input.ativa(), input.ordemExibicao());
        }
        return parteRepository.salvar(existente);
    }

    public ConfiguracaoDetalhada buscarDetalhada(long variacaoId) {
        ConfiguracaoComposicaoMultipla configuracao = configuracaoRepository.buscarPorVariacao(variacaoId)
                .orElse(null);
        if (configuracao == null) {
            return null;
        }
        return new ConfiguracaoDetalhada(
                configuracao,
                parteRepository.listarPorConfiguracao(configuracao.getId())
        );
    }

    public PreparacaoComposicaoMultipla preparar(VariacaoItemCardapio variacaoPrincipal,
                                                 List<ParteInput> partesInput) {
        if (partesInput == null || partesInput.isEmpty()) {
            return null;
        }
        validarVariacaoVendavel(variacaoPrincipal, "VARIACAO_INATIVA");
        ConfiguracaoComposicaoMultipla configuracao = configuracaoRepository
                .buscarPorVariacao(variacaoPrincipal.getId())
                .orElseThrow(() -> new ErroNegocioException(
                        "COMPOSICAO_MULTISSABOR_NAO_PERMITIDA",
                        "Variacao nao permite composicao multissabor."
                ));
        if (!configuracao.isAtivo()) {
            throw new ErroNegocioException(
                    "COMPOSICAO_MULTISSABOR_NAO_PERMITIDA",
                    "Configuracao multissabor esta inativa."
            );
        }
        int quantidadePartes = partesInput.size();
        validarQuantidadePartes(configuracao, quantidadePartes);

        Map<Long, ConfiguracaoComposicaoMultiplaParte> permitidas = parteRepository
                .listarAtivasPorConfiguracao(configuracao.getId())
                .stream()
                .collect(Collectors.toMap(
                        parte -> parte.getVariacaoParte().getId(),
                        parte -> parte,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
        if (permitidas.isEmpty()) {
            throw new ErroNegocioException("PARTE_NAO_PERMITIDA", "Nenhuma parte ativa configurada para a variacao.");
        }

        List<BigDecimal> proporcoes = resolverProporcoes(configuracao, partesInput);
        List<Long> variacaoParteIds = partesInput.stream().map(ParteInput::variacaoParteId).toList();
        Map<Long, VariacaoItemCardapio> variacoesPorId = variacaoRepository.listarPorIds(variacaoParteIds).stream()
                .collect(Collectors.toMap(VariacaoItemCardapio::getId, v -> v));
        Set<Long> repetidas = new HashSet<>();
        List<ParteResolvida> partes = new ArrayList<>();
        for (int i = 0; i < partesInput.size(); i++) {
            ParteInput entrada = partesInput.get(i);
            ConfiguracaoComposicaoMultiplaParte permitida = permitidas.get(entrada.variacaoParteId());
            if (permitida == null) {
                throw new ErroNegocioException(
                        "PARTE_NAO_PERMITIDA",
                        "Variacao nao esta permitida como parte desta composicao.",
                        Map.of("variacaoParteId", entrada.variacaoParteId())
                );
            }
            if (!configuracao.isPermiteRepetirPartes() && !repetidas.add(entrada.variacaoParteId())) {
                throw new ErroNegocioException(
                        "PARTE_DUPLICADA_NAO_PERMITIDA",
                        "A configuracao nao permite repetir a mesma parte."
                );
            }
            VariacaoItemCardapio variacaoParte = variacoesPorId.get(entrada.variacaoParteId());
            validarVariacaoVendavel(variacaoParte, "PARTE_INATIVA");
            ResolucaoComposicaoVariacaoService.ResolucaoComposicao resolucao = resolucaoService
                    .resolverObrigatoria(variacaoParte);
            partes.add(new ParteResolvida(
                    i,
                    entrada,
                    permitida,
                    variacaoParte,
                    proporcoes.get(i),
                    resolucao
            ));
        }

        BigDecimal precoBase = calcularPrecoBase(variacaoPrincipal, configuracao, partes);
        String hash = calcularHash(variacaoPrincipal, configuracao, partes, precoBase);
        String resumo = resumo(partes);
        return new PreparacaoComposicaoMultipla(
                configuracao,
                List.copyOf(partes),
                precoBase,
                hash,
                resumo
        );
    }

    public void validarHashObservado(String hashObservado, String hashAtual) {
        String observado = normalizar(hashObservado);
        if (observado != null && !observado.equals(hashAtual)) {
            throw new ErroNegocioException(
                    "CONFIGURACAO_MULTISSABOR_ALTERADA",
                    "Configuracao multissabor foi alterada desde a inclusao no rascunho.",
                    Map.of("configuracaoObservada", observado, "configuracaoAtual", hashAtual)
            );
        }
    }

    private void validarQuantidadePartes(ConfiguracaoComposicaoMultipla configuracao, int quantidadePartes) {
        if (quantidadePartes < configuracao.getMinimoPartes() || quantidadePartes > configuracao.getMaximoPartes()) {
            throw new ErroNegocioException(
                    "QUANTIDADE_PARTES_INVALIDA",
                    "Quantidade de partes fora do minimo/maximo da configuracao.",
                    Map.of(
                            "quantidade", quantidadePartes,
                            "minimo", configuracao.getMinimoPartes(),
                            "maximo", configuracao.getMaximoPartes()
                    )
            );
        }
        Set<Integer> permitidas = quantidadesPermitidas(configuracao.getQuantidadesPermitidas());
        if (!permitidas.isEmpty() && !permitidas.contains(quantidadePartes)) {
            throw new ErroNegocioException(
                    "QUANTIDADE_PARTES_INVALIDA",
                    "Quantidade de partes nao esta entre as quantidades permitidas.",
                    Map.of("quantidade", quantidadePartes, "permitidas", permitidas)
            );
        }
    }

    private List<BigDecimal> resolverProporcoes(ConfiguracaoComposicaoMultipla configuracao,
                                                List<ParteInput> partesInput) {
        boolean usarIguais = configuracao.isProporcoesIguaisObrigatorias()
                || !configuracao.isPermiteProporcaoPersonalizada();
        if (usarIguais) {
            return proporcoesIguais(partesInput.size());
        }
        List<BigDecimal> proporcoes = partesInput.stream()
                .map(ParteInput::proporcao)
                .map(this::proporcaoObrigatoria)
                .toList();
        BigDecimal soma = proporcoes.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(ESCALA_PROPORCAO, RoundingMode.HALF_UP);
        if (soma.compareTo(BigDecimal.ONE.setScale(ESCALA_PROPORCAO, RoundingMode.HALF_UP)) != 0) {
            throw new ErroNegocioException(
                    "SOMA_PROPORCOES_INVALIDA",
                    "Soma das proporcoes deve ser igual a 1."
            );
        }
        return proporcoes;
    }

    private List<BigDecimal> proporcoesIguais(int quantidadePartes) {
        BigDecimal um = BigDecimal.ONE.setScale(ESCALA_PROPORCAO, RoundingMode.HALF_UP);
        BigDecimal base = um.divide(BigDecimal.valueOf(quantidadePartes), ESCALA_PROPORCAO, RoundingMode.DOWN);
        List<BigDecimal> proporcoes = new ArrayList<>();
        BigDecimal acumulado = BigDecimal.ZERO.setScale(ESCALA_PROPORCAO, RoundingMode.HALF_UP);
        for (int i = 0; i < quantidadePartes; i++) {
            BigDecimal proporcao = i == quantidadePartes - 1
                    ? um.subtract(acumulado).setScale(ESCALA_PROPORCAO, RoundingMode.HALF_UP)
                    : base;
            proporcoes.add(proporcao);
            acumulado = acumulado.add(proporcao).setScale(ESCALA_PROPORCAO, RoundingMode.HALF_UP);
        }
        return proporcoes;
    }

    private BigDecimal proporcaoObrigatoria(BigDecimal proporcao) {
        if (proporcao == null) {
            throw new ErroNegocioException("PROPORCAO_INVALIDA", "Todas as partes precisam de proporcao.");
        }
        BigDecimal normalizada = proporcao.setScale(ESCALA_PROPORCAO, RoundingMode.HALF_UP);
        if (normalizada.compareTo(BigDecimal.ZERO) <= 0 || normalizada.compareTo(BigDecimal.ONE) > 0) {
            throw new ErroNegocioException("PROPORCAO_INVALIDA", "Proporcao deve ser maior que zero e menor ou igual a 1.");
        }
        return normalizada;
    }

    private BigDecimal calcularPrecoBase(VariacaoItemCardapio variacaoPrincipal,
                                         ConfiguracaoComposicaoMultipla configuracao,
                                         List<ParteResolvida> partes) {
        return switch (configuracao.getPoliticaPreco()) {
            case MAIOR_PRECO -> partes.stream()
                    .map(parte -> dinheiro(parte.variacaoParte().getPrecoVenda()))
                    .max(Comparator.naturalOrder())
                    .orElse(BigDecimal.ZERO.setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP));
            case MEDIA_ARITMETICA -> partes.stream()
                    .map(parte -> dinheiro(parte.variacaoParte().getPrecoVenda()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(partes.size()), ESCALA_DINHEIRO, RoundingMode.HALF_UP);
            case MEDIA_PONDERADA, SOMA_PROPORCIONAL -> partes.stream()
                    .map(parte -> dinheiro(parte.variacaoParte().getPrecoVenda()).multiply(parte.proporcao()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
            case PRECO_FIXO_DA_VARIACAO -> dinheiro(variacaoPrincipal.getPrecoVenda());
        };
    }

    private String calcularHash(VariacaoItemCardapio variacaoPrincipal,
                                ConfiguracaoComposicaoMultipla configuracao,
                                List<ParteResolvida> partes,
                                BigDecimal precoBase) {
        StringBuilder sb = new StringBuilder()
                .append("V|").append(variacaoPrincipal.getId()).append('|')
                .append(variacaoPrincipal.getCodigo()).append('|')
                .append(dinheiroHash(variacaoPrincipal.getPrecoVenda())).append('\n')
                .append("CFG|").append(configuracao.getId()).append('|')
                .append(configuracao.isAtivo()).append('|')
                .append(configuracao.getMinimoPartes()).append('|')
                .append(configuracao.getMaximoPartes()).append('|')
                .append(configuracao.getQuantidadesPermitidas()).append('|')
                .append(configuracao.isPermiteRepetirPartes()).append('|')
                .append(configuracao.isProporcoesIguaisObrigatorias()).append('|')
                .append(configuracao.isPermiteProporcaoPersonalizada()).append('|')
                .append(configuracao.getPoliticaPreco()).append('|')
                .append(configuracao.getPoliticaArredondamento()).append('|')
                .append(dinheiroHash(precoBase)).append('\n');
        partes.stream()
                .sorted(Comparator
                        .comparingLong((ParteResolvida parte) -> parte.variacaoParte().getId())
                        .thenComparing(ParteResolvida::proporcao))
                .forEach(parte -> {
                    FichaTecnica ficha = parte.resolucao().fichaTecnica();
                    FichaTecnicaVersao versao = parte.resolucao().fichaTecnicaVersao();
                    sb.append("P|").append(parte.variacaoParte().getId()).append('|')
                            .append(parte.variacaoParte().getCodigo()).append('|')
                            .append(parte.proporcao().toPlainString()).append('|')
                            .append(dinheiroHash(parte.variacaoParte().getPrecoVenda())).append('|')
                            .append(ficha == null ? "" : ficha.getId()).append('|')
                            .append(versao == null ? "" : versao.getId()).append('|')
                            .append(versao == null ? "" : versao.getNumeroVersao()).append('\n');
                });
        return sha256(sb.toString());
    }

    private String resumo(List<ParteResolvida> partes) {
        return partes.stream()
                .sorted(Comparator.comparingInt(ParteResolvida::ordemVisual))
                .map(parte -> proporcaoResumo(parte.proporcao()) + " " + parte.variacaoParte().getNome())
                .collect(Collectors.joining("; "));
    }

    private String proporcaoResumo(BigDecimal proporcao) {
        BigDecimal dois = new BigDecimal("0.500000");
        BigDecimal tercoA = new BigDecimal("0.333333");
        BigDecimal tercoB = new BigDecimal("0.333334");
        if (proporcao.compareTo(dois) == 0) {
            return "1/2";
        }
        if (proporcao.compareTo(tercoA) == 0 || proporcao.compareTo(tercoB) == 0) {
            return "1/3";
        }
        return proporcao.multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString() + "%";
    }

    private VariacaoItemCardapio buscarVariacao(long variacaoId) {
        return variacaoRepository.buscarPorId(variacaoId)
                .orElseThrow(() -> new ErroNegocioException(
                        "VARIACAO_NAO_ENCONTRADA",
                        "Variacao de cardapio nao encontrada."
                ));
    }

    private void validarVariacaoVendavel(VariacaoItemCardapio variacao, String codigoErro) {
        if (variacao == null || !variacao.isAtiva()) {
            throw new ErroNegocioException(codigoErro, "Variacao inativa nao pode compor venda.");
        }
        ItemCardapio item = variacao.getItemCardapio();
        if (item == null || !item.isAtivo() || !item.isDisponivel()) {
            throw new ErroNegocioException(codigoErro, "Item da variacao esta inativo ou indisponivel.");
        }
        if (item.getCategoriaCardapio() != null && !item.getCategoriaCardapio().isAtivo()) {
            throw new ErroNegocioException(codigoErro, "Categoria da variacao esta inativa.");
        }
    }

    private Set<Integer> quantidadesPermitidas(String valor) {
        if (valor == null || valor.isBlank()) {
            return Set.of();
        }
        Set<Integer> permitidas = new HashSet<>();
        for (String parte : valor.split(",")) {
            permitidas.add(Integer.parseInt(parte.trim()));
        }
        return permitidas;
    }

    private String normalizarQuantidades(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return quantidadesPermitidas(valor).stream()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private static String normalizar(String valor) {
        if (valor == null) return null;
        String texto = valor.trim();
        return texto.isEmpty() ? null : texto;
    }

    private static BigDecimal dinheiro(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(ESCALA_DINHEIRO, RoundingMode.HALF_UP);
    }

    private static String dinheiroHash(BigDecimal valor) {
        return dinheiro(valor).toPlainString();
    }

    private static String sha256(String texto) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel para hash multissabor.", e);
        }
    }

    public record ConfiguracaoInput(
            boolean ativo,
            int minimoPartes,
            int maximoPartes,
            String quantidadesPermitidas,
            boolean permiteRepetirPartes,
            boolean proporcoesIguaisObrigatorias,
            boolean permiteProporcaoPersonalizada,
            PoliticaPrecoComposicaoMultipla politicaPreco,
            PoliticaArredondamentoComposicaoMultipla politicaArredondamento
    ) {}

    public record PartePermitidaInput(boolean ativa, int ordemExibicao) {}

    public record ParteInput(
            long variacaoParteId,
            BigDecimal proporcao,
            String configuracaoVersao,
            List<MontagemConfiguracaoItemService.SelecaoModificadorInput> selecoes
    ) {}

    public record PreparacaoComposicaoMultipla(
            ConfiguracaoComposicaoMultipla configuracao,
            List<ParteResolvida> partes,
            BigDecimal precoUnitarioBase,
            String configuracaoHash,
            String resumoPartes
    ) {}

    public record ParteResolvida(
            int ordemVisual,
            ParteInput entrada,
            ConfiguracaoComposicaoMultiplaParte partePermitida,
            VariacaoItemCardapio variacaoParte,
            BigDecimal proporcao,
            ResolucaoComposicaoVariacaoService.ResolucaoComposicao resolucao
    ) {
        public FichaTecnica fichaTecnica() {
            return resolucao.fichaTecnica();
        }

        public FichaTecnicaVersao fichaTecnicaVersao() {
            return resolucao.fichaTecnicaVersao();
        }
    }

    public record ConfiguracaoDetalhada(
            ConfiguracaoComposicaoMultipla configuracao,
            List<ConfiguracaoComposicaoMultiplaParte> partesPermitidas
    ) {
        public LocalDateTime atualizadoEm() {
            return configuracao.getAtualizadoEm();
        }
    }
}
