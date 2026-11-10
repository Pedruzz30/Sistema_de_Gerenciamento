package controller;

import model.Permissao;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import repository.UsuarioRepository;
import service.CertificadoCelular;

import java.nio.file.Files;
import java.util.List;

/** Celular do atendente: o certificado da loja e os endereços para o painel mostrar. */
@RestController
public class CelularController {

    private final CertificadoCelular certificado;
    private final UsuarioRepository usuarioRepository;

    public CelularController(CertificadoCelular certificado, UsuarioRepository usuarioRepository) {
        this.certificado = certificado;
        this.usuarioRepository = usuarioRepository;
    }

    /** O certificado público da loja, para instalar no celular. Não é segredo. */
    @GetMapping("/certificado")
    public ResponseEntity<FileSystemResource> baixarCertificado() {
        if (!certificado.ativo() || !Files.exists(certificado.getArquivoCa())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-x509-ca-cert"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + CertificadoCelular.ARQUIVO_CA + "\"")
                .body(new FileSystemResource(certificado.getArquivoCa()));
    }

    /** O mesmo certificado, como perfil do iPhone (instala pelo Safari, com o nome da loja). */
    @GetMapping("/certificado.mobileconfig")
    public ResponseEntity<byte[]> perfilIphone() throws java.io.IOException {
        if (!certificado.ativo() || !Files.exists(certificado.getArquivoCa())) {
            return ResponseEntity.notFound().build();
        }
        String perfil = CertificadoCelular.perfilApple(Files.readAllBytes(certificado.getArquivoCa()));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-apple-aspen-config"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"stockos-loja.mobileconfig\"")
                .body(perfil.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @GetMapping("/api/sistema/celular")
    public ResponseEntity<SituacaoResponse> situacao(@RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        ControllerUtils.resolveUserAndRequireAnyPermission(ruHeader, usuarioRepository,
                "Voce nao tem permissao para configurar o celular.", Permissao.VER_FINANCAS, Permissao.EDITAR_ESTOQUE);
        boolean ligado = certificado.ativo() && Files.exists(certificado.getKeystoreServidor());
        return ResponseEntity.ok(new SituacaoResponse(ligado, certificado.getPorta(),
                ligado ? certificado.getEnderecos() : List.of()));
    }

    public record SituacaoResponse(boolean ativo, int porta, List<CertificadoCelular.Endereco> enderecos) {}
}
