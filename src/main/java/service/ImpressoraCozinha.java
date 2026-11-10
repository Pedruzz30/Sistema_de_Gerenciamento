package service;

import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;

/**
 * Impressora térmica da cozinha (ESC/POS), pelo spooler do Windows, sem passar pelo navegador.
 * Funciona com as térmicas de 80 mm e 58 mm comuns no Brasil (Elgin, Bematech, Epson, Daruma...).
 * O texto sai sem acento, porque cada marca usa uma tabela de caracteres diferente.
 */
public class ImpressoraCozinha {

    private static final byte ESC = 0x1B;
    private static final byte GS = 0x1D;

    /** As impressoras instaladas no Windows deste notebook. */
    public List<String> impressoras() {
        return Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null))
                .map(PrintService::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public void imprimir(String impressora, String texto, int colunas) throws Exception {
        PrintService servico = Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null))
                .filter(s -> s.getName().equalsIgnoreCase(impressora))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Impressora \"" + impressora + "\" não encontrada no Windows."));
        DocPrintJob trabalho = servico.createPrintJob();
        trabalho.print(new SimpleDoc(escPos(texto, colunas), DocFlavor.BYTE_ARRAY.AUTOSENSE, null), null);
    }

    /**
     * O texto do pedido em comandos ESC/POS. Linhas que começam com "## " saem em letra dupla
     * (o tipo e a comanda), e no fim o papel é cortado.
     */
    static byte[] escPos(String texto, int colunas) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        saida.writeBytes(new byte[]{ESC, '@'});
        for (String linha : texto.split("\n", -1)) {
            boolean grande = linha.startsWith("## ");
            String conteudo = semAcento(grande ? linha.substring(3) : linha);
            if (grande) {
                saida.writeBytes(new byte[]{ESC, 'a', 1, GS, '!', 0x11});
                conteudo = cortar(conteudo, Math.max(8, colunas / 2));
            } else {
                conteudo = cortar(conteudo, colunas);
            }
            saida.writeBytes(conteudo.getBytes(StandardCharsets.US_ASCII));
            saida.write('\n');
            if (grande) saida.writeBytes(new byte[]{GS, '!', 0, ESC, 'a', 0});
        }
        // Avança o papel até a lâmina e corta (corte parcial, aceito por quase todas).
        saida.writeBytes(new byte[]{ESC, 'd', 4, GS, 'V', 66, 0});
        return saida.toByteArray();
    }

    private static String cortar(String texto, int colunas) {
        return texto.length() <= colunas ? texto : texto.substring(0, colunas);
    }

    static String semAcento(String texto) {
        String semMarcas = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        StringBuilder ascii = new StringBuilder(semMarcas.length());
        for (char c : semMarcas.toCharArray()) {
            if (c == '×') ascii.append('x');
            else if (c == '–' || c == '—') ascii.append('-');
            else if (c >= 32 && c < 127) ascii.append(c);
            else if (Character.isWhitespace(c)) ascii.append(' ');
            else ascii.append('?');
        }
        return ascii.toString();
    }
}
