package service;

import model.CodigoVenda;
import model.Permissao;
import model.Usuario;
import model.VariacaoItemCardapio;
import org.springframework.transaction.annotation.Transactional;
import repository.CodigoVendaRepository;
import repository.VariacaoItemCardapioRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Códigos lidos no caixa: código de barras (EAN-8, UPC-A, EAN-13, GTIN-14)
 * ou código interno curto (ex.: "101" para o caldo, "GELO5" para o gelo).
 *
 * <p>Códigos numéricos com 8, 12, 13 ou 14 dígitos são tratados como código
 * de barras e o dígito verificador é conferido — leitura errada ou digitação
 * errada é recusada em vez de cadastrar um código que nunca vai bater.
 */
@Transactional(readOnly = true)
public class CodigoVendaService {

    private static final Pattern NUMERICO = Pattern.compile("\\d+");
    private static final Pattern INTERNO = Pattern.compile("[A-Za-z0-9._-]{1,32}");

    private final CodigoVendaRepository codigoVendaRepository;
    private final VariacaoItemCardapioRepository variacaoRepository;

    public CodigoVendaService(CodigoVendaRepository codigoVendaRepository,
                              VariacaoItemCardapioRepository variacaoRepository) {
        this.codigoVendaRepository = codigoVendaRepository;
        this.variacaoRepository = variacaoRepository;
    }

    /** Resultado de uma leitura: a variação padrão e as alternativas (ex.: Natural). */
    public record Leitura(String codigo, CodigoVenda.Tipo tipo, CodigoVenda padrao, List<CodigoVenda> alternativas) {}

    public Optional<Leitura> ler(String codigoLido) {
        String codigo = normalizar(codigoLido);
        if (codigo.isEmpty()) {
            return Optional.empty();
        }
        List<CodigoVenda> encontrados = codigoVendaRepository.listarAtivosPorCodigo(codigo).stream()
                .filter(c -> c.getVariacao().isAtiva())
                .filter(c -> c.getVariacao().getItemCardapio() == null || c.getVariacao().getItemCardapio().isAtivo())
                .toList();
        if (encontrados.isEmpty()) {
            return Optional.empty();
        }
        CodigoVenda padrao = encontrados.stream().filter(CodigoVenda::isPadrao).findFirst().orElse(encontrados.get(0));
        List<CodigoVenda> alternativas = encontrados.stream().filter(c -> c != padrao).toList();
        return Optional.of(new Leitura(codigo, padrao.getTipo(), padrao, alternativas));
    }

    public List<CodigoVenda> listarAtivos() {
        return codigoVendaRepository.listarAtivos();
    }

    /** Variações à venda sem nenhum código: gelo, copo, petiscos, caldos. */
    public List<VariacaoItemCardapio> listarSemCodigo() {
        java.util.Set<Long> comCodigo = codigoVendaRepository.listarAtivos().stream()
                .map(c -> c.getVariacao().getId())
                .collect(java.util.stream.Collectors.toSet());
        return variacaoRepository.listarTodos().stream()
                .filter(VariacaoItemCardapio::isAtiva)
                .filter(v -> v.getItemCardapio() == null || v.getItemCardapio().isAtivo())
                .filter(v -> !comCodigo.contains(v.getId()))
                .toList();
    }

    /**
     * Código de barras da loja para uma variação: EAN-13 começando com 20, faixa que o
     * padrão GS1 reserva para uso interno. Qualquer leitor bipa e ele nunca coincide com
     * o código de fábrica de um produto. É sempre o mesmo para a mesma variação.
     */
    public static String codigoInterno(long variacaoId) {
        String base = "20" + String.format("%010d", variacaoId);
        return base + digitoVerificador(base);
    }

    /** Cria o código interno de cada variação que ainda não tem código. */
    @Transactional
    public List<CodigoVenda> gerarInternos(Usuario usuario) {
        validarPermissaoEdicao(usuario);
        java.util.List<CodigoVenda> criados = new java.util.ArrayList<>();
        for (VariacaoItemCardapio variacao : listarSemCodigo()) {
            String codigo = codigoInterno(variacao.getId());
            if (!codigoVendaRepository.listarAtivosPorCodigo(codigo).isEmpty()) {
                continue;
            }
            criados.add(codigoVendaRepository.salvar(new CodigoVenda(codigo, CodigoVenda.Tipo.EAN13, variacao, true)));
        }
        return criados;
    }

    public List<CodigoVenda> listarPorVariacao(long variacaoId) {
        return codigoVendaRepository.listarAtivosPorVariacao(variacaoId);
    }

    /**
     * Vincula um código a uma variação vendável.
     *
     * @param padrao {@code null}: vira padrão se o código ainda não tiver um.
     *               {@code true}: passa a ser o padrão (o anterior vira alternativa).
     */
    @Transactional
    public CodigoVenda cadastrar(Usuario usuario, String codigoInformado, long variacaoId, Boolean padrao) {
        validarPermissaoEdicao(usuario);
        String codigo = normalizar(codigoInformado);
        CodigoVenda.Tipo tipo = classificar(codigo);
        VariacaoItemCardapio variacao = variacaoRepository.buscarPorId(variacaoId)
                .filter(VariacaoItemCardapio::isAtiva)
                .orElseThrow(() -> new ErroNegocioException(
                        "VARIACAO_NAO_ENCONTRADA", "Variacao nao encontrada ou inativa.",
                        Map.of("variacaoId", variacaoId)));

        List<CodigoVenda> existentes = codigoVendaRepository.listarAtivosPorCodigo(codigo);
        if (existentes.stream().anyMatch(c -> c.getVariacao().getId() == variacaoId)) {
            throw new ErroNegocioException("CODIGO_VENDA_DUPLICADO",
                    "Este codigo ja esta vinculado a esta variacao.",
                    Map.of("codigo", codigo, "variacaoId", variacaoId));
        }
        boolean jaTemPadrao = existentes.stream().anyMatch(CodigoVenda::isPadrao);
        boolean seraPadrao = padrao == null ? !jaTemPadrao : padrao;
        if (seraPadrao) {
            for (CodigoVenda existente : existentes) {
                if (existente.isPadrao()) {
                    existente.definirPadrao(false);
                    codigoVendaRepository.salvar(existente);
                }
            }
        }
        return codigoVendaRepository.salvar(new CodigoVenda(codigo, tipo, variacao, seraPadrao));
    }

    @Transactional
    public void desativar(Usuario usuario, long id) {
        validarPermissaoEdicao(usuario);
        CodigoVenda codigo = codigoVendaRepository.buscarPorId(id)
                .orElseThrow(() -> new ErroNegocioException("CODIGO_VENDA_NAO_ENCONTRADO", "Codigo nao encontrado."));
        boolean eraPadrao = codigo.isPadrao();
        codigo.desativar();
        codigo.definirPadrao(false);
        codigoVendaRepository.salvar(codigo);
        if (eraPadrao) {
            // A primeira alternativa que sobrar vira o novo padrão.
            codigoVendaRepository.listarAtivosPorCodigo(codigo.getCodigo()).stream().findFirst().ifPresent(proximo -> {
                proximo.definirPadrao(true);
                codigoVendaRepository.salvar(proximo);
            });
        }
    }

    // ── Regras de código ──────────────────────────────────────────────────────

    /** Remove espaços e caracteres de controle que alguns leitores enviam. */
    public static String normalizar(String codigo) {
        if (codigo == null) {
            return "";
        }
        return codigo.replaceAll("[\\s\\p{Cntrl}]", "");
    }

    /** Classifica o código e confere o dígito verificador dos códigos de barras. */
    public static CodigoVenda.Tipo classificar(String codigo) {
        if (codigo == null || codigo.isEmpty()) {
            throw new IllegalArgumentException("Codigo e obrigatorio.");
        }
        if (NUMERICO.matcher(codigo).matches()) {
            CodigoVenda.Tipo tipo = switch (codigo.length()) {
                case 8 -> CodigoVenda.Tipo.EAN8;
                case 12 -> CodigoVenda.Tipo.UPCA;
                case 13 -> CodigoVenda.Tipo.EAN13;
                case 14 -> CodigoVenda.Tipo.GTIN14;
                default -> null;
            };
            if (tipo != null) {
                if (!digitoVerificadorValido(codigo)) {
                    throw new IllegalArgumentException(
                            "Codigo de barras invalido: o digito verificador nao confere. Bipe de novo ou confira a digitacao.");
                }
                return tipo;
            }
        }
        if (!INTERNO.matcher(codigo).matches()) {
            throw new IllegalArgumentException(
                    "Codigo interno invalido: use ate 32 letras, numeros, ponto, hifen ou sublinhado.");
        }
        return CodigoVenda.Tipo.INTERNO;
    }

    /** Dígito que completa o código (sem ele) pelo padrão GS1. */
    public static int digitoVerificador(String semDigito) {
        int soma = 0;
        int peso = 3;
        for (int i = semDigito.length() - 1; i >= 0; i--) {
            soma += (semDigito.charAt(i) - '0') * peso;
            peso = peso == 3 ? 1 : 3;
        }
        return (10 - (soma % 10)) % 10;
    }

    /** Dígito verificador GS1 (EAN-8, UPC-A, EAN-13, GTIN-14). */
    public static boolean digitoVerificadorValido(String codigo) {
        int soma = 0;
        int peso = 3;
        for (int i = codigo.length() - 2; i >= 0; i--) {
            soma += (codigo.charAt(i) - '0') * peso;
            peso = peso == 3 ? 1 : 3;
        }
        int esperado = (10 - (soma % 10)) % 10;
        return esperado == codigo.charAt(codigo.length() - 1) - '0';
    }

    private static void validarPermissaoEdicao(Usuario usuario) {
        if (usuario == null
                || usuario.getClasse() == null
                || !usuario.getClasse().possuiPermissao(Permissao.EDITAR_ESTOQUE)) {
            throw new SecurityException("Voce nao tem permissao para cadastrar codigos de venda.");
        }
    }
}
