package service;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Lê o XML de uma NF-e (modelo 55) recebida do fornecedor.
 *
 * <p>O arquivo vem de fora, então o leitor recusa DOCTYPE e entidades externas
 * (ataques XXE e "billion laughs"). A chave de acesso tem o dígito verificador
 * conferido, mas a <b>assinatura digital não é validada</b> e a SEFAZ não é
 * consultada: uma nota cancelada depois de emitida não é detectada aqui.
 */
public final class NfeXmlParser {

    private static final int MAX_CARACTERES = 5_000_000;
    private static final int MAX_ITENS = 1_000;
    private static final String NS_NFE = "http://www.portalfiscal.inf.br/nfe";
    // Limites defensivos da aplicacao, nao substituem o schema fiscal.
    private static final Pattern DECIMAL = Pattern.compile("[0-9]{1,20}(?:\\.[0-9]{1,10})?");
    private static final String SEM_GTIN = "SEM GTIN";

    private NfeXmlParser() {
    }

    public record Documento(String chaveAcesso,
                            String numero,
                            String serie,
                            LocalDateTime dataEmissao,
                            boolean homologacao,
                            boolean notaDeSaidaNormal,
                            Parte emitente,
                            Parte destinatario,
                            BigDecimal valorTotal,
                            List<Item> itens) {}

    public record Parte(String documento, String nome, String telefone) {}

    /**
     * @param custoTotal estimativa de custo de entrada: produto + frete + seguro
     *                   + outras despesas + IPI + ICMS-ST + FCP-ST − desconto
     */
    public record Item(int numero,
                       String codigoFornecedor,
                       String descricao,
                       String ncm,
                       String cest,
                       String cfop,
                       String eanComercial,
                       String eanTributavel,
                       String unidadeComercial,
                       BigDecimal quantidadeComercial,
                       String unidadeTributavel,
                       BigDecimal quantidadeTributavel,
                       BigDecimal valorProdutos,
                       BigDecimal custoTotal) {}

    public static Documento ler(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("Envie o arquivo XML da nota.");
        }
        if (xml.length() > MAX_CARACTERES) {
            throw new IllegalArgumentException("Arquivo XML grande demais para uma NF-e.");
        }
        Document documento = interpretar(xml);
        Element raiz = documento.getDocumentElement();
        if (raiz == null || !NS_NFE.equals(raiz.getNamespaceURI())) {
            throw new IllegalArgumentException("Este XML nao e uma NF-e.");
        }
        Element nfe = switch (raiz.getLocalName()) {
            case "NFe" -> raiz;
            case "nfeProc" -> exigir(raiz, "NFe");
            default -> throw new IllegalArgumentException("Este XML nao e uma NF-e.");
        };
        Element infNFe = exigir(nfe, "infNFe");

        Element ide = exigir(infNFe, "ide");
        String modelo = texto(ide, "mod");
        if ("65".equals(modelo)) {
            throw new IllegalArgumentException("Este XML e uma NFC-e (cupom de consumidor), nao uma nota de compra.");
        }
        if (!"55".equals(modelo)) {
            throw new IllegalArgumentException("Modelo de nota nao suportado: " + modelo + ".");
        }

        String id = infNFe.getAttribute("Id");
        String chave = id.startsWith("NFe") ? id.substring(3) : "";
        if (!chaveValida(chave)) {
            throw new IllegalArgumentException("Chave de acesso invalida: formato ou digito verificador incorreto.");
        }

        Parte emitente = lerParte(exigir(infNFe, "emit"), "enderEmit");
        Element dest = primeiro(infNFe, "dest");
        Parte destinatario = dest == null ? new Parte("", "", "") : lerParte(dest, "enderDest");

        List<Item> itens = new ArrayList<>();
        Set<Integer> numeros = new HashSet<>();
        for (Node no = infNFe.getFirstChild(); no != null; no = no.getNextSibling()) {
            if (!(no instanceof Element det) || !ehTag(det, "det")) {
                continue;
            }
            if (itens.size() >= MAX_ITENS) {
                throw new IllegalArgumentException("Nota excede o limite de itens da importacao.");
            }
            Item item = lerItem(det);
            if (!numeros.add(item.numero())) {
                throw new IllegalArgumentException("Numero de item duplicado: " + item.numero() + ".");
            }
            itens.add(item);
        }
        if (itens.isEmpty()) {
            throw new IllegalArgumentException("A nota nao tem itens.");
        }

        Element totais = exigir(exigir(infNFe, "total"), "ICMSTot");
        BigDecimal total = decimalObrigatorio(totais, "vNF", "Total da nota");

        String ambiente = texto(ide, "tpAmb");
        if (!"1".equals(ambiente) && !"2".equals(ambiente)) {
            throw new IllegalArgumentException("Ambiente da nota invalido ou ausente.");
        }

        return new Documento(
                chave,
                texto(ide, "nNF"),
                texto(ide, "serie"),
                lerData(texto(ide, "dhEmi"), texto(ide, "dEmi")),
                "2".equals(ambiente),
                "1".equals(texto(ide, "tpNF")) && "1".equals(texto(ide, "finNFe")),
                emitente,
                destinatario,
                total.setScale(2, RoundingMode.HALF_UP),
                List.copyOf(itens)
        );
    }

    /** Dígito verificador (módulo 11) da chave de acesso de 44 dígitos. */
    public static boolean chaveValida(String chave) {
        if (chave == null || !chave.matches("\\d{44}")) {
            return false;
        }
        int soma = 0;
        int peso = 2;
        for (int i = 42; i >= 0; i--) {
            soma += (chave.charAt(i) - '0') * peso;
            peso = peso == 9 ? 2 : peso + 1;
        }
        int resto = soma % 11;
        int digito = resto < 2 ? 0 : 11 - resto;
        return digito == chave.charAt(43) - '0';
    }

    /** "SEM GTIN", vazio ou dígito verificador errado viram {@code null}. */
    static String gtinOuNulo(String valor) {
        if (valor == null) {
            return null;
        }
        String limpo = valor.trim();
        if (limpo.isEmpty() || SEM_GTIN.equalsIgnoreCase(limpo) || !limpo.matches("\\d{8}|\\d{12,14}")) {
            return null;
        }
        return CodigoVendaService.digitoVerificadorValido(limpo) ? limpo : null;
    }

    private static Item lerItem(Element det) {
        Element prod = exigir(det, "prod");
        int numero;
        try {
            String numeroXml = det.getAttribute("nItem");
            if (!numeroXml.matches("[0-9]{1,9}")) {
                throw new NumberFormatException();
            }
            numero = Integer.parseInt(numeroXml);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Numero de item invalido.");
        }
        if (numero <= 0) {
            throw new IllegalArgumentException("Numero de item invalido.");
        }
        String contexto = "Item " + numero + " da nota";
        BigDecimal quantidadeComercial = decimalObrigatorio(prod, "qCom", contexto);
        BigDecimal valorProdutos = decimalObrigatorio(prod, "vProd", contexto);
        BigDecimal quantidadeTributavel = decimalObrigatorio(prod, "qTrib", contexto);
        if (quantidadeComercial.signum() <= 0 || quantidadeTributavel.signum() <= 0) {
            throw new IllegalArgumentException(contexto + " possui quantidade nao positiva.");
        }
        String unidadeComercial = texto(prod, "uCom");
        String unidadeTributavel = texto(prod, "uTrib");
        if (unidadeComercial.isBlank() || unidadeTributavel.isBlank()) {
            throw new IllegalArgumentException(contexto + " sem unidade comercial ou tributavel.");
        }

        BigDecimal custo = valorProdutos
                .add(decimalOpcional(prod, "vFrete", contexto))
                .add(decimalOpcional(prod, "vSeg", contexto))
                .add(decimalOpcional(prod, "vOutro", contexto))
                .subtract(decimalOpcional(prod, "vDesc", contexto));
        Element imposto = primeiro(det, "imposto");
        if (imposto != null) {
            Element ipi = primeiro(imposto, "IPI");
            Element ipiTrib = ipi == null ? null : primeiro(ipi, "IPITrib");
            Element icms = primeiro(imposto, "ICMS");
            custo = custo
                    .add(decimalOpcional(ipiTrib, "vIPI", contexto))
                    .add(valorGrupoIcms(icms, "vICMSST", contexto))
                    .add(valorGrupoIcms(icms, "vFCPST", contexto));
        }
        if (custo.signum() < 0) {
            throw new IllegalArgumentException(contexto + " possui custo total negativo.");
        }

        return new Item(
                numero,
                texto(prod, "cProd"),
                texto(prod, "xProd"),
                texto(prod, "NCM"),
                vazioParaNulo(texto(prod, "CEST")),
                texto(prod, "CFOP"),
                gtinOuNulo(texto(prod, "cEAN")),
                gtinOuNulo(texto(prod, "cEANTrib")),
                unidadeComercial.toUpperCase(Locale.ROOT),
                quantidadeComercial,
                unidadeTributavel.toUpperCase(Locale.ROOT),
                quantidadeTributavel,
                valorProdutos.setScale(2, RoundingMode.HALF_UP),
                custo.setScale(2, RoundingMode.HALF_UP)
        );
    }

    private static Parte lerParte(Element parte, String tagEndereco) {
        String documento = texto(parte, "CNPJ");
        if (documento.isEmpty()) {
            documento = texto(parte, "CPF");
        }
        Element endereco = primeiro(parte, tagEndereco);
        return new Parte(documento, texto(parte, "xNome"),
                endereco == null ? "" : texto(endereco, "fone"));
    }

    private static Document interpretar(String xml) {
        try {
            DocumentBuilderFactory fabrica = DocumentBuilderFactory.newDefaultInstance();
            fabrica.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            fabrica.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            fabrica.setFeature("http://xml.org/sax/features/external-general-entities", false);
            fabrica.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            fabrica.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            fabrica.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            fabrica.setXIncludeAware(false);
            fabrica.setExpandEntityReferences(false);
            fabrica.setNamespaceAware(true);
            fabrica.setAttribute("jdk.xml.maxElementDepth", "64");
            fabrica.setAttribute("jdk.xml.elementAttributeLimit", "64");
            DocumentBuilder construtor = fabrica.newDocumentBuilder();
            construtor.setEntityResolver((publicId, systemId) -> {
                throw new SAXException("Resolucao externa de entidades proibida.");
            });
            construtor.setErrorHandler(new DefaultHandler() {
                @Override
                public void error(SAXParseException e) throws SAXException {
                    throw e;
                }

                @Override
                public void fatalError(SAXParseException e) throws SAXException {
                    throw e;
                }
            });
            // Lê do texto já decodificado: a declaração de encoding do arquivo é ignorada.
            return construtor.parse(new InputSource(new StringReader(xml.strip().replaceFirst("^\uFEFF", ""))));
        } catch (SAXException | IOException e) {
            throw new IllegalArgumentException("Arquivo XML invalido ou corrompido.");
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Nao foi possivel preparar o leitor de XML.", e);
        }
    }

    private static Element exigir(Element pai, String tag) {
        Element filho = primeiro(pai, tag);
        if (filho == null) {
            throw new IllegalArgumentException("XML incompleto: falta o bloco " + tag + ".");
        }
        return filho;
    }

    private static boolean ehTag(Element elemento, String tag) {
        return NS_NFE.equals(elemento.getNamespaceURI()) && tag.equals(elemento.getLocalName());
    }

    /** Somente filhos diretos no namespace fiscal; nao aceita campos ambiguos. */
    private static Element primeiro(Element pai, String tag) {
        Element encontrado = null;
        for (Node no = pai.getFirstChild(); no != null; no = no.getNextSibling()) {
            if (no instanceof Element elemento && ehTag(elemento, tag)) {
                if (encontrado != null) {
                    throw new IllegalArgumentException("XML ambiguo: bloco duplicado " + tag + ".");
                }
                encontrado = elemento;
            }
        }
        return encontrado;
    }

    private static String texto(Element pai, String tag) {
        Element filho = primeiro(pai, tag);
        return filho == null ? "" : textoSimples(filho);
    }

    private static String textoSimples(Element elemento) {
        for (Node no = elemento.getFirstChild(); no != null; no = no.getNextSibling()) {
            if (no instanceof Element) {
                throw new IllegalArgumentException("XML invalido: campo simples com elementos internos.");
            }
        }
        return elemento.getTextContent().trim();
    }

    private static BigDecimal decimalObrigatorio(Element pai, String tag, String contexto) {
        Element filho = primeiro(pai, tag);
        if (filho == null) {
            throw new IllegalArgumentException(contexto + " sem " + tag + " valido.");
        }
        return decimal(textoSimples(filho), tag, contexto);
    }

    /** Ausencia e zero sao permitidos; campo presente invalido nao e ignorado. */
    private static BigDecimal decimalOpcional(Element pai, String tag, String contexto) {
        Element filho = pai == null ? null : primeiro(pai, tag);
        return filho == null ? BigDecimal.ZERO : decimal(textoSimples(filho), tag, contexto);
    }

    private static BigDecimal decimal(String texto, String tag, String contexto) {
        if (!DECIMAL.matcher(texto).matches()) {
            throw new IllegalArgumentException(contexto + " sem " + tag + " valido.");
        }
        return new BigDecimal(texto);
    }

    /** ICMS possui um grupo de tributacao. Nao busca impostos retidos anteriormente. */
    private static BigDecimal valorGrupoIcms(Element icms, String tag, String contexto) {
        if (icms == null) {
            return BigDecimal.ZERO;
        }
        Element grupo = null;
        for (Node no = icms.getFirstChild(); no != null; no = no.getNextSibling()) {
            if (no instanceof Element elemento && NS_NFE.equals(elemento.getNamespaceURI())) {
                if (grupo != null) {
                    throw new IllegalArgumentException(contexto + " com grupos ICMS ambiguos.");
                }
                grupo = elemento;
            }
        }
        return decimalOpcional(grupo, tag, contexto);
    }

    private static String vazioParaNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private static LocalDateTime lerData(String dataHora, String dataAntiga) {
        try {
            if (dataHora != null && !dataHora.isBlank()) {
                return OffsetDateTime.parse(dataHora).toLocalDateTime();
            }
            if (dataAntiga != null && !dataAntiga.isBlank()) {
                return LocalDate.parse(dataAntiga).atStartOfDay();
            }
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Data de emissao da nota invalida.");
        }
        throw new IllegalArgumentException("Data de emissao da nota obrigatoria.");
    }
}