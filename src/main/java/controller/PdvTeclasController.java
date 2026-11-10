package controller;

import model.Permissao;
import model.Usuario;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import repository.UsuarioRepository;
import service.AutenticacaoService;
import service.TeclasRapidasService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Teclas rápidas do caixa. Ler vale para quem opera o caixa; mudar precisa de quem cuida do
 * estoque, ou do login dele na hora, como no cadastro feito pelo caixa.
 */
@RestController
@RequestMapping("/api/pdv/teclas")
public class PdvTeclasController {

    private final TeclasRapidasService teclasService;
    private final AutenticacaoService autenticacaoService;
    private final UsuarioRepository usuarioRepository;

    public PdvTeclasController(TeclasRapidasService teclasService,
                               AutenticacaoService autenticacaoService,
                               UsuarioRepository usuarioRepository) {
        this.teclasService = teclasService;
        this.autenticacaoService = autenticacaoService;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping
    public ResponseEntity<List<TeclasRapidasService.Tecla>> listar(
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        operador(ruHeader);
        return ResponseEntity.ok(teclasService.listar());
    }

    @GetMapping("/opcoes")
    public ResponseEntity<List<TeclasRapidasService.Tecla>> opcoes(
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        operador(ruHeader);
        return ResponseEntity.ok(teclasService.opcoes());
    }

    @PutMapping
    public ResponseEntity<List<TeclasRapidasService.Tecla>> salvar(
            @RequestBody SalvarTeclasRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario operador = operador(ruHeader);
        Usuario responsavel = responsavel(operador, resolverAutorizador(request == null ? null : request.autorizacao()));
        Map<Integer, Long> teclas = new LinkedHashMap<>();
        for (TeclaRequest tecla : request == null || request.teclas() == null ? List.<TeclaRequest>of() : request.teclas()) {
            if (tecla == null || tecla.posicao() == null || tecla.variacaoId() == null) continue;
            if (teclas.put(tecla.posicao(), tecla.variacaoId()) != null) {
                throw new IllegalArgumentException("A tecla " + tecla.posicao() + " apareceu duas vezes.");
            }
        }
        return ResponseEntity.ok(teclasService.salvar(responsavel, teclas));
    }

    public record TeclaRequest(Integer posicao, Long variacaoId) {}

    public record SalvarTeclasRequest(List<TeclaRequest> teclas, CaixaController.AutorizacaoRequest autorizacao) {}

    private Usuario operador(String ruHeader) {
        return ControllerUtils.resolveUserAndRequireAnyPermission(
                ruHeader, usuarioRepository, "Voce nao tem permissao para usar o caixa.",
                Permissao.VER_VENDAS, Permissao.EDITAR_ESTOQUE);
    }

    static Usuario responsavel(Usuario operador, Usuario autorizador) {
        if (podeMudar(operador)) {
            return operador;
        }
        if (autorizador != null && autorizador.isAtivo() && podeMudar(autorizador)) {
            return autorizador;
        }
        throw new SecurityException(autorizador == null
                ? "Mudar as teclas rápidas precisa do login do gerente."
                : "Quem autorizou não pode mudar as teclas rápidas.");
    }

    private static boolean podeMudar(Usuario usuario) {
        return usuario != null && usuario.getClasse() != null
                && usuario.getClasse().possuiPermissao(Permissao.EDITAR_ESTOQUE);
    }

    private Usuario resolverAutorizador(CaixaController.AutorizacaoRequest autorizacao) {
        if (autorizacao == null || autorizacao.nome() == null || autorizacao.nome().isBlank()
                || autorizacao.senha() == null || autorizacao.senha().isBlank()) {
            return null;
        }
        return autenticacaoService.conferirCredenciais(
                        autorizacao.nome().trim(),
                        autorizacao.sobrenome() == null ? "" : autorizacao.sobrenome().trim(),
                        autorizacao.senha())
                .orElseThrow(() -> new SecurityException("Login do gerente invalido."));
    }
}
