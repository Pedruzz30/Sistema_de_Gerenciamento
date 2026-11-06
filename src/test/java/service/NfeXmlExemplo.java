package service;

/** Monta XMLs de NF-e fictícios, com chave de acesso válida, para os testes. */
public final class NfeXmlExemplo {

    public static final String CNPJ_DISTRIBUIDORA = "11222333000181";

    private NfeXmlExemplo() {
    }

    /** Linha da nota: 5 CX de 12 = uCom CX, qCom 5, uTrib UN, qTrib 60. */
    public record Linha(String codigo, String descricao, String eanCaixa, String eanUnidade,
                        String uCom, String qCom, String uTrib, String qTrib, String vProd, String impostos) {}

    /** Chave de 44 dígitos com o dígito verificador certo a partir de um número da nota. */
    public static String chave(long numero) {
        String base = String.format("35261111222333000181550010%017d", numero % 100_000_000_000_000_000L);
        int soma = 0;
        int peso = 2;
        for (int i = 42; i >= 0; i--) {
            soma += (base.charAt(i) - '0') * peso;
            peso = peso == 9 ? 2 : peso + 1;
        }
        int resto = soma % 11;
        return base + (resto < 2 ? 0 : 11 - resto);
    }

    public static String nota(long numero, Linha... linhas) {
        return nota(chave(numero), "55", numero, linhas);
    }

    public static String nota(String chave, String modelo, long numero, Linha... linhas) {
        StringBuilder itens = new StringBuilder();
        int item = 1;
        for (Linha l : linhas) {
            itens.append("<det nItem=\"").append(item++).append("\"><prod>")
                    .append("<cProd>").append(l.codigo()).append("</cProd>")
                    .append("<cEAN>").append(l.eanCaixa()).append("</cEAN>")
                    .append("<xProd>").append(l.descricao()).append("</xProd>")
                    .append("<NCM>22030000</NCM><CEST>0302100</CEST><CFOP>5405</CFOP>")
                    .append("<uCom>").append(l.uCom()).append("</uCom>")
                    .append("<qCom>").append(l.qCom()).append("</qCom>")
                    .append("<vProd>").append(l.vProd()).append("</vProd>")
                    .append("<cEANTrib>").append(l.eanUnidade()).append("</cEANTrib>")
                    .append("<uTrib>").append(l.uTrib()).append("</uTrib>")
                    .append("<qTrib>").append(l.qTrib()).append("</qTrib>")
                    .append("</prod>")
                    .append(l.impostos() == null ? "" : l.impostos())
                    .append("</det>");
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<nfeProc xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\"><NFe>"
                + "<infNFe Id=\"NFe" + chave + "\" versao=\"4.00\"><ide>"
                + "<mod>" + modelo + "</mod><serie>1</serie><nNF>" + numero + "</nNF>"
                + "<dhEmi>2026-11-05T10:30:00-03:00</dhEmi><tpNF>1</tpNF><tpAmb>1</tpAmb><finNFe>1</finNFe></ide>"
                + "<emit><CNPJ>" + CNPJ_DISTRIBUIDORA + "</CNPJ><xNome>Distribuidora Exemplo LTDA</xNome>"
                + "<enderEmit><fone>2133334444</fone></enderEmit></emit>"
                + "<dest><CNPJ>04252011000110</CNPJ><xNome>Deposito Exemplo</xNome></dest>"
                + itens
                + "<total><ICMSTot><vNF>0.00</vNF></ICMSTot></total>"
                + "</infNFe></NFe></nfeProc>";
    }
}
