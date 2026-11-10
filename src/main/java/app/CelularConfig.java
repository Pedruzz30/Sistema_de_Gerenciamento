package app;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import service.CertificadoCelular;

import java.nio.file.Path;

/**
 * Celular do atendente: uma segunda porta, com conexão segura, aberta para a rede da loja.
 * Fica desligada até {@code stockos.celular.porta} e {@code stockos.celular.pasta} serem
 * informados (o pacote da loja informa).
 */
@Configuration
public class CelularConfig {

    private static final Logger log = LoggerFactory.getLogger(CelularConfig.class);

    @Bean
    public CertificadoCelular certificadoCelular(@Value("${stockos.celular.porta:0}") int porta,
                                                 @Value("${stockos.celular.pasta:}") String pasta) {
        return new CertificadoCelular(porta, pasta == null || pasta.isBlank() ? null : Path.of(pasta).toAbsolutePath());
    }

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> portaDoCelular(
            CertificadoCelular certificado,
            @Value("${stockos.celular.endereco:0.0.0.0}") String endereco) {
        return fabrica -> {
            if (!certificado.ativo()) return;
            try {
                certificado.preparar();
                fabrica.addAdditionalTomcatConnectors(conector(certificado, endereco));
                log.info("Celular do atendente: porta {} aberta em {}", certificado.getPorta(), certificado.getEnderecos());
            } catch (Exception e) {
                // Sem certificado o caixa continua funcionando; só o celular fica de fora.
                log.error("Celular do atendente desligado: não foi possível preparar o certificado.", e);
            }
        };
    }

    private static Connector conector(CertificadoCelular certificado, String endereco) throws Exception {
        Connector conector = new Connector(Http11NioProtocol.class.getName());
        conector.setPort(certificado.getPorta());
        conector.setScheme("https");
        conector.setSecure(true);
        conector.setProperty("address", endereco);
        Http11NioProtocol protocolo = (Http11NioProtocol) conector.getProtocolHandler();
        protocolo.setSSLEnabled(true);
        SSLHostConfig ssl = new SSLHostConfig();
        ssl.setProtocols("TLSv1.2+TLSv1.3");
        SSLHostConfigCertificate chave = new SSLHostConfigCertificate(ssl, SSLHostConfigCertificate.Type.UNDEFINED);
        chave.setCertificateKeystoreFile(certificado.getKeystoreServidor().toString());
        chave.setCertificateKeystorePassword(certificado.senha());
        chave.setCertificateKeystoreType("PKCS12");
        chave.setCertificateKeyAlias(CertificadoCelular.ALIAS_SERVIDOR);
        ssl.addCertificate(chave);
        conector.addSslHostConfig(ssl);
        return conector;
    }

    @Bean
    public FilterRegistrationBean<controller.PortaCelularFilter> filtroPortaCelular(CertificadoCelular certificado) {
        var registro = new FilterRegistrationBean<>(new controller.PortaCelularFilter(certificado.ativo() ? certificado.getPorta() : 0));
        registro.addUrlPatterns("/*");
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registro;
    }
}
