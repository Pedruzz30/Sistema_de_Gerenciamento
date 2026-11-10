package service;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

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
     * @param custoTotal o que o item custou de verdade: produto + frete + seguro
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
        Element infNFe = primeiro(documento.getDocumentElement(), "infNFe");
        if (infNFe == null) {
            throw new IllegalArgumentException("Este XML nao e uma NF-e.");
        }

        Element ide = exigir(infNFe, "ide");
        String modelo = texto(ide, "mod");
        if ("65".equals(modelo)) {
            throw new IllegalArgumentException("Este XML e uma NFC-e (cupom de consumidor), nao uma nota de compra.");
        }
        if (!"55".equals(modelo)) {
            throw new IllegalArgumentException("Modelo de nota nao suportado: " + modelo + ".");
        }

        String chave = infNFe.getAttribute("Id").replaceFirst("^NFe", "");
        if (!chaveValida(chave)) {
            throw new IllegalArgumentException("Chave de acesso invalida: o arquivo esta incompleto ou foi alterado.");
        }

        Parte emitente = lerParte(exigir(infNFe, "emit"), "xNome");
        Element dest = primeiro(infNFe, "dest");
        Parte destinatario = dest == null ? new Parte("", "", "") : lerParte(dest, "xNome");

        List<Item> itens = new ArrayList<>();
        NodeList detalhes = infNFe.getElementsByTagNameNS("*", "det");
        for (int i = 0; i < detalhes.getLength(); i++) {
            itens.add(lerItem((Element) detalhes.item(i), i + 1));
        }
        if (itens.isEmpty()) {
            throw new IllegalArgumentException("A nota nao tem itens.");
        }

        Element totais = primeiro(infNFe, "ICMSTot");
        BigDecimal total = totais == null ? null : decimal(texto(totais, "vNF"));
        if (total == null) {
            total = itens.stream().map(Item::custoTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        return new Documento(
                chave,
                texto(ide, "nNF"),
                texto(ide, "serie"),
                lerData(texto(ide, "dhEmi"), texto(ide, "dEmi")),
                "2".equals(texto(ide, "tpAmb")),
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

    private static Item lerItem(Element det, int posicao) {
        Element prod = exigir(det, "prod");
        int numero = posicao;
        try {
            numero = Integer.parseInt(det.getAttribute("nItem"));
        } catch (NumberFormatException ignorado) {
            // Mantém a posição no arquivo.
        }
        BigDecimal quantidadeComercial = decimalObrigatorio(prod, "qCom", numero);
        BigDecimal valorProdutos = decimalObrigatorio(prod, "vProd", numero);
        BigDecimal quantidadeTributavel = decimal(texto(prod, "qTrib"));

        BigDecimal custo = valorProdutos
                .add(zeroSeNulo(decimal(texto(prod, "vFrete"))))
                .add(zeroSeNulo(decimal(texto(prod, "vSeg"))))
                .add(zeroSeNulo(decimal(texto(prod, "vOutro"))))
                .subtract(zeroSeNulo(decimal(texto(prod, "vDesc"))));
        Element imposto = primeiro(det, "imposto");
        if (imposto != null) {
            custo = custo
                    .add(zeroSeNulo(decimal(textoEmQualquerNivel(imposto, "vIPI"))))
                    .add(zeroSeNulo(decimal(textoEmQualquerNivel(imposto, "vICMSST"))))
                    .add(zeroSeNulo(decimal(textoEmQualquerNivel(imposto, "vFCPST"))));
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
                texto(prod, "uCom").toUpperCase(java.util.Locale.ROOT),
                quantidadeComercial,
                texto(prod, "uTrib").toUpperCase(java.util.Locale.ROOT),
                quantidadeTributavel == null ? quantidadeComercial : quantidadeTributavel,
                valorProdutos.setScale(2, RoundingMode.HALF_UP),
                custo.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
        );
    }

    private static Parte lerParte(Element parte, String tagNome) {
        String documento = texto(parte, "CNPJ");
        if (documento.isEmpty()) {
            documento = texto(parte, "CPF");
        }
        return new Parte(documento, texto(parte, tagNome), textoEmQualquerNivel(parte, "fone"));
    }

    private static Document interpretar(String xml) {
        try {
            DocumentBuilderFactory fabrica = DocumentBuilderFactory.newInstance();
            fabrica.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            fabrica.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            fabrica.setFeature("http://xml.org/sax/features/external-general-entities", false);
            fabrica.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            fabrica.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            fabrica.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            fabrica.setXIncludeAware(false);
            fabrica.setExpandEntityReferences(false);
            fabrica.setNamespaceAware(true);
            DocumentBuilder construtor = fabrica.newDocumentBuilder();
            construtor.setErrorHandler(null);
            // Lê do texto já decodificado: a declaração de encoding do arquivo é ignorada.
            return construtor.parse(new InputSource(new StringReader(xml.strip().replaceFirst("^﻿", ""))));
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

    private static Element primeiro(Element pai, String tag) {
        if (tag.equals(pai.getLocalName())) {
            return pai;
        }
        NodeList nos = pai.getElementsByTagNameNS("*", tag);
        return nos.getLength() == 0 ? null : (Element) nos.item(0);
    }

    /** Valor de um filho direto, para não confundir com tags de mesmo nome mais abaixo. */
    private static String texto(Element pai, String tag) {
        for (Node no = pai.getFirstChild(); no != null; no = no.getNextSibling()) {
            if (no instanceof Element elemento && tag.equals(elemento.getLocalName())) {
                return elemento.getTextContent().trim();
            }
        }
        return "";
    }

    private static String textoEmQualquerNivel(Element pai, String tag) {
        NodeList nos = pai.getElementsByTagNameNS("*", tag);
        return nos.getLength() == 0 ? "" : nos.item(0).getTextContent().trim();
    }

    private static BigDecimal decimalObrigatorio(Element prod, String tag, int item) {
        BigDecimal valor = decimal(texto(prod, tag));
        if (valor == null || valor.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Item " + item + " da nota sem " + tag + " valido.");
        }
        return valor;
    }

    private static BigDecimal decimal(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(texto.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal zeroSeNulo(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
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
                return java.time.LocalDate.parse(dataAntiga).atStartOfDay();
            }
        } catch (DateTimeParseException ignorado) {
            // Data ilegível não impede a entrada: usa o momento da importação.
        }
        return LocalDateTime.now();
    }
}
