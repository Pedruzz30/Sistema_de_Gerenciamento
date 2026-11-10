package service;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Certificado da conexão segura do celular do atendente.
 *
 * <p>Na primeira vez, cria o certificado da loja (uma autoridade só desta loja, que vale 10
 * anos). É ele que se instala uma vez em cada celular. A cada início, se o endereço do
 * notebook na rede mudou ou o certificado do servidor está para vencer, cria outro
 * certificado de servidor assinado pelo da loja. Assim o celular continua confiando sem
 * instalar nada de novo.
 *
 * <p>Usa o {@code keytool} do próprio Java, que vem junto no pacote da loja.
 */
public class CertificadoCelular {

    public static final String ARQUIVO_CA = "stockos-loja.crt";
    public static final String ALIAS_SERVIDOR = "servidor";

    /** Um endereço para abrir no celular e a rede de onde ele vem (Wi-Fi, cabo...). */
    public record Endereco(String url, String rede) {}

    private record Rede(String ip, String nome) {}

    private final int porta;
    private final Path pasta;
    private volatile List<Endereco> enderecos = List.of();

    public CertificadoCelular(int porta, Path pasta) {
        this.porta = porta;
        this.pasta = pasta;
    }

    public boolean ativo() {
        return porta > 0 && pasta != null;
    }

    public int getPorta() {
        return porta;
    }

    public Path getArquivoCa() {
        return pasta.resolve(ARQUIVO_CA);
    }

    public Path getKeystoreServidor() {
        return pasta.resolve("servidor.p12");
    }

    public List<Endereco> getEnderecos() {
        return enderecos;
    }

    public String senha() throws IOException {
        return Files.readString(pasta.resolve("senha.txt"), StandardCharsets.UTF_8).trim();
    }

    /** Garante os certificados antes de abrir a porta. */
    public synchronized void preparar() throws IOException, InterruptedException {
        Files.createDirectories(pasta);
        Path arquivoSenha = pasta.resolve("senha.txt");
        if (!Files.exists(arquivoSenha)) {
            byte[] bytes = new byte[24];
            new SecureRandom().nextBytes(bytes);
            Files.writeString(arquivoSenha, HexFormat.of().formatHex(bytes), StandardCharsets.UTF_8);
        }
        Path ca = pasta.resolve("ca.p12");
        if (!Files.exists(ca)) {
            Files.deleteIfExists(getArquivoCa());
            Files.deleteIfExists(getKeystoreServidor());
            byte[] marca = new byte[3];
            new SecureRandom().nextBytes(marca);
            keytool("-genkeypair", "-alias", "ca", "-keyalg", "RSA", "-keysize", "2048", "-validity", "3650",
                    "-startdate", "-1d",
                    "-dname", "CN=StockOS certificado da loja " + HexFormat.of().formatHex(marca) + ", O=StockOS",
                    "-ext", "bc:c=ca:true,pathlen:0", "-ext", "ku:c=keyCertSign,cRLSign",
                    "-keystore", ca.toString(), "-storetype", "PKCS12", "-storepass:file", arquivoSenha.toString());
        }
        if (!Files.exists(getArquivoCa())) {
            keytool("-exportcert", "-alias", "ca", "-keystore", ca.toString(), "-storetype", "PKCS12",
                    "-storepass:file", arquivoSenha.toString(), "-file", getArquivoCa().toString());
        }

        List<Rede> redes = redesLocais();
        String san = san(redes.stream().map(Rede::ip).toList(), nomeDoComputador());
        Path arquivoSan = pasta.resolve("servidor.san");
        boolean mesmoEndereco = Files.exists(arquivoSan) && Files.readString(arquivoSan, StandardCharsets.UTF_8).equals(san);
        if (!Files.exists(getKeystoreServidor()) || !mesmoEndereco || venceEmBreve(getKeystoreServidor(), senha())) {
            gerarServidor(ca, arquivoSenha, san);
            Files.writeString(arquivoSan, san, StandardCharsets.UTF_8);
        }
        enderecos = redes.stream().map(r -> new Endereco("https://" + r.ip() + ":" + porta + "/", r.nome())).toList();
    }

    private void gerarServidor(Path ca, Path arquivoSenha, String san) throws IOException, InterruptedException {
        Path servidor = getKeystoreServidor();
        Path pedido = pasta.resolve("servidor.csr");
        Path assinado = pasta.resolve("servidor.cer");
        Path novo = pasta.resolve("servidor-novo.p12");
        Files.deleteIfExists(novo);
        String senha = arquivoSenha.toString();
        keytool("-genkeypair", "-alias", ALIAS_SERVIDOR, "-keyalg", "RSA", "-keysize", "2048", "-validity", "397",
                "-dname", "CN=StockOS celular", "-keystore", novo.toString(), "-storetype", "PKCS12", "-storepass:file", senha);
        keytool("-certreq", "-alias", ALIAS_SERVIDOR, "-keystore", novo.toString(), "-storetype", "PKCS12",
                "-storepass:file", senha, "-file", pedido.toString());
        keytool("-gencert", "-alias", "ca", "-keystore", ca.toString(), "-storetype", "PKCS12", "-storepass:file", senha,
                "-infile", pedido.toString(), "-outfile", assinado.toString(), "-rfc",
                "-startdate", "-1d", "-validity", "397",
                "-ext", san, "-ext", "eku=serverAuth", "-ext", "ku:c=digitalSignature,keyEncipherment");
        keytool("-importcert", "-noprompt", "-alias", "ca", "-file", getArquivoCa().toString(),
                "-keystore", novo.toString(), "-storetype", "PKCS12", "-storepass:file", senha);
        keytool("-importcert", "-alias", ALIAS_SERVIDOR, "-file", assinado.toString(),
                "-keystore", novo.toString(), "-storetype", "PKCS12", "-storepass:file", senha);
        Files.move(novo, servidor, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(pedido);
        Files.deleteIfExists(assinado);
    }

    private static boolean venceEmBreve(Path keystore, String senha) {
        try (InputStream entrada = Files.newInputStream(keystore)) {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(entrada, senha.toCharArray());
            X509Certificate certificado = (X509Certificate) ks.getCertificate(ALIAS_SERVIDOR);
            return certificado == null
                    || certificado.getNotAfter().toInstant().isBefore(Instant.now().plus(Duration.ofDays(30)));
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Perfil de configuração do iPhone com o certificado da loja: aparece no iPhone com o nome
     * da loja. Os identificadores saem do próprio certificado, então baixar de novo não duplica.
     */
    public static String perfilApple(byte[] certificadoDer) {
        String base64 = java.util.Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(certificadoDer);
        String uuidCertificado = java.util.UUID.nameUUIDFromBytes(concat("cert:", certificadoDer)).toString().toUpperCase(Locale.ROOT);
        String uuidPerfil = java.util.UUID.nameUUIDFromBytes(concat("perfil:", certificadoDer)).toString().toUpperCase(Locale.ROOT);
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                <dict>
                  <key>PayloadContent</key>
                  <array>
                    <dict>
                      <key>PayloadCertificateFileName</key><string>stockos-loja.cer</string>
                      <key>PayloadContent</key>
                      <data>
                %s
                      </data>
                      <key>PayloadDescription</key><string>Certificado da loja para a conexão segura do celular do atendente.</string>
                      <key>PayloadDisplayName</key><string>StockOS - certificado da loja</string>
                      <key>PayloadIdentifier</key><string>br.stockos.loja.certificado.%s</string>
                      <key>PayloadType</key><string>com.apple.security.root</string>
                      <key>PayloadUUID</key><string>%s</string>
                      <key>PayloadVersion</key><integer>1</integer>
                    </dict>
                  </array>
                  <key>PayloadDescription</key><string>Instala o certificado da loja para abrir as comandas do StockOS com conexão segura.</string>
                  <key>PayloadDisplayName</key><string>StockOS - celular do atendente</string>
                  <key>PayloadIdentifier</key><string>br.stockos.loja.%s</string>
                  <key>PayloadRemovalDisallowed</key><false/>
                  <key>PayloadType</key><string>Configuration</string>
                  <key>PayloadUUID</key><string>%s</string>
                  <key>PayloadVersion</key><integer>1</integer>
                </dict>
                </plist>
                """.formatted(base64, uuidCertificado, uuidCertificado, uuidPerfil, uuidPerfil);
    }

    private static byte[] concat(String prefixo, byte[] dados) {
        byte[] inicio = prefixo.getBytes(StandardCharsets.US_ASCII);
        byte[] tudo = new byte[inicio.length + dados.length];
        System.arraycopy(inicio, 0, tudo, 0, inicio.length);
        System.arraycopy(dados, 0, tudo, inicio.length, dados.length);
        return tudo;
    }

    /** Os nomes que o certificado aceita: os endereços da rede, o próprio notebook e o nome dele. */
    static String san(List<String> ips, String nomeComputador) {
        Set<String> partes = new LinkedHashSet<>();
        for (String ip : ips) partes.add("ip:" + ip);
        partes.add("ip:127.0.0.1");
        partes.add("dns:localhost");
        if (nomeComputador != null && nomeComputador.matches("[A-Za-z0-9][A-Za-z0-9-]{0,62}")) {
            partes.add("dns:" + nomeComputador.toLowerCase(Locale.ROOT));
        }
        return "san=" + String.join(",", partes);
    }

    /** Endereços IPv4 de rede local (192.168, 10, 172.16-31), primeiro os do Wi-Fi. */
    private static List<Rede> redesLocais() {
        List<Rede> redes = new ArrayList<>();
        try {
            for (NetworkInterface interfaceRede : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!interfaceRede.isUp() || interfaceRede.isLoopback() || interfaceRede.isVirtual()) continue;
                for (InetAddress endereco : Collections.list(interfaceRede.getInetAddresses())) {
                    if (endereco instanceof Inet4Address && endereco.isSiteLocalAddress()) {
                        redes.add(new Rede(endereco.getHostAddress(), interfaceRede.getDisplayName()));
                    }
                }
            }
        } catch (SocketException e) {
            return List.of();
        }
        redes.sort(Comparator.comparing((Rede r) -> !ehSemFio(r.nome())).thenComparing(Rede::ip));
        return redes;
    }

    static boolean ehSemFio(String nome) {
        String texto = nome == null ? "" : nome.toLowerCase(Locale.ROOT);
        return texto.contains("wi-fi") || texto.contains("wifi") || texto.contains("wireless")
                || texto.contains("wlan") || texto.contains("802.11");
    }

    private static String nomeDoComputador() {
        String nome = System.getenv("COMPUTERNAME");
        if (nome == null || nome.isBlank()) {
            try {
                nome = InetAddress.getLocalHost().getHostName();
            } catch (IOException e) {
                return null;
            }
        }
        return nome;
    }

    private void keytool(String... argumentos) throws IOException, InterruptedException {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        Path programa = Path.of(System.getProperty("java.home"), "bin", windows ? "keytool.exe" : "keytool");
        List<String> comando = new ArrayList<>();
        comando.add(programa.toString());
        Collections.addAll(comando, argumentos);
        Process processo = new ProcessBuilder(comando).directory(pasta.toFile()).redirectErrorStream(true).start();
        String saida = new String(processo.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!processo.waitFor(120, TimeUnit.SECONDS)) {
            processo.destroyForcibly();
            throw new IOException("keytool demorou demais: " + argumentos[0]);
        }
        if (processo.exitValue() != 0) {
            throw new IOException("keytool " + argumentos[0] + " falhou: " + saida.trim());
        }
    }
}
