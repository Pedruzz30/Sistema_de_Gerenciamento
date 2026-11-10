package service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Certificado do celular: o que se instala no celular faz a conexão ser confiável. */
class CelularNovoTest {

    @Test
    void sanTemOsEnderecosDaRedeEONotebook() {
        assertEquals("san=ip:192.168.0.10,ip:127.0.0.1,dns:localhost,dns:caixa-loja",
                CertificadoCelular.san(List.of("192.168.0.10"), "CAIXA-LOJA"));
        assertEquals("san=ip:127.0.0.1,dns:localhost", CertificadoCelular.san(List.of(), "nome com espaço"));
        assertTrue(CertificadoCelular.ehSemFio("Intel(R) Wi-Fi 6 AX201"));
        assertFalse(CertificadoCelular.ehSemFio("Realtek PCIe GbE"));
    }

    @Test
    void certificadoDaLojaTornaAConexaoConfiavel(@TempDir Path pasta) throws Exception {
        CertificadoCelular certificado = new CertificadoCelular(8443, pasta);
        certificado.preparar();
        assertTrue(Files.exists(certificado.getArquivoCa()), "Arquivo para instalar no celular");

        X509Certificate ca;
        try (InputStream entrada = Files.newInputStream(certificado.getArquivoCa())) {
            ca = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(entrada);
        }
        assertTrue(ca.getBasicConstraints() >= 0, "O da loja é uma autoridade");

        KeyStore servidor = KeyStore.getInstance("PKCS12");
        try (InputStream entrada = Files.newInputStream(certificado.getKeystoreServidor())) {
            servidor.load(entrada, certificado.senha().toCharArray());
        }
        Certificate[] cadeia = servidor.getCertificateChain(CertificadoCelular.ALIAS_SERVIDOR);
        assertEquals(2, cadeia.length, "Servidor + loja");
        X509Certificate folha = (X509Certificate) cadeia[0];
        folha.verify(ca.getPublicKey());
        assertTrue(folha.getExtendedKeyUsage().contains("1.3.6.1.5.5.7.3.1"), "Uso para servidor");
        assertTrue(folha.getSubjectAlternativeNames().stream().anyMatch(n -> "127.0.0.1".equals(n.get(1))));

        // Reiniciar sem mudar de rede não troca o certificado.
        FileTime antes = Files.getLastModifiedTime(certificado.getKeystoreServidor());
        new CertificadoCelular(8443, pasta).preparar();
        assertEquals(antes, Files.getLastModifiedTime(certificado.getKeystoreServidor()));

        // Um "celular" que só confia no certificado da loja conecta em localhost sem aviso.
        KeyManagerFactory chaves = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        chaves.init(servidor, certificado.senha().toCharArray());
        SSLContext contextoServidor = SSLContext.getInstance("TLS");
        contextoServidor.init(chaves.getKeyManagers(), null, null);
        try (SSLServerSocket socket = (SSLServerSocket) contextoServidor.getServerSocketFactory().createServerSocket(0)) {
            Thread atendente = new Thread(() -> {
                try (SSLSocket cliente = (SSLSocket) socket.accept()) {
                    cliente.getInputStream().read(new byte[512]);
                    OutputStream saida = cliente.getOutputStream();
                    saida.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".getBytes(StandardCharsets.US_ASCII));
                    saida.flush();
                } catch (Exception ignorada) {
                    // O teste falha pelo lado do cliente.
                }
            });
            atendente.start();

            KeyStore confianca = KeyStore.getInstance("PKCS12");
            confianca.load(null, null);
            confianca.setCertificateEntry("loja", ca);
            TrustManagerFactory confia = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            confia.init(confianca);
            SSLContext contextoCelular = SSLContext.getInstance("TLS");
            contextoCelular.init(null, confia.getTrustManagers(), null);
            HttpsURLConnection conexao = (HttpsURLConnection) new URL("https://localhost:" + socket.getLocalPort() + "/").openConnection();
            conexao.setSSLSocketFactory(contextoCelular.getSocketFactory());
            assertEquals(200, conexao.getResponseCode());
            atendente.join(5000);
        }

        // Sem o certificado da loja, a conexão é recusada: é o aviso do navegador.
        try (SSLServerSocket socket = (SSLServerSocket) contextoServidor.getServerSocketFactory().createServerSocket(0)) {
            Thread atendente = new Thread(() -> {
                try (SSLSocket cliente = (SSLSocket) socket.accept()) {
                    cliente.startHandshake();
                } catch (Exception ignorada) {
                    // Esperado.
                }
            });
            atendente.start();
            HttpsURLConnection conexao = (HttpsURLConnection) new URL("https://localhost:" + socket.getLocalPort() + "/").openConnection();
            assertThrows(javax.net.ssl.SSLHandshakeException.class, conexao::getResponseCode);
            atendente.join(5000);
        }
    }
}
