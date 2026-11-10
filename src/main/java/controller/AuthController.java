
package controller;

import model.ClasseFuncionario;
import model.Usuario;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import service.AutenticacaoService;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Controller responsável pelo login legado do StockOS.
 *
 * IMPORTANTE:
 * Esta API ainda não emite tokens nem cria sessões
 * autenticadas. Não deve ser exposta publicamente
 * sem a integração com autenticação verificada.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AutenticacaoService autenticacaoService;

    public AuthController(AutenticacaoService autenticacaoService) {
        this.autenticacaoService = autenticacaoService;
    }

    /**
     * Verifica as credenciais recebidas.
     *
     * 200 - Credenciais válidas e usuário ativo.
     * 400 - Dados obrigatórios ausentes.
     * 401 - Credenciais inválidas ou usuário inativo.
     */
    @PostMapping("/login")
    public ResponseEntity<UsuarioResponse> login(
            @RequestBody(required = false) LoginRequest request
    ) {

        if (credenciaisIncompletas(request)) {
            return ResponseEntity.badRequest().build();
        }

        String nome = request.nome().trim();
        String sobrenome = request.sobrenome().trim();

        // A senha não deve ser normalizada ou modificada.
        String senha = request.senha();

        return autenticacaoService
                .autenticar(nome, sobrenome, senha)
                .filter(Usuario::isAtivo)
                .map(usuario -> ResponseEntity.ok(
                        UsuarioResponse.from(usuario)
                ))
                .orElseGet(() -> ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .build());
    }

    /**
     * Valida a presença das credenciais obrigatórias.
     *
     * Não revela qual campo corresponde a um usuário
     * existente no sistema.
     */
    private static boolean credenciaisIncompletas(
            LoginRequest request
    ) {
        return request == null
                || estaEmBranco(request.nome())
                || estaEmBranco(request.sobrenome())
                || estaEmBranco(request.senha());
    }

    private static boolean estaEmBranco(String valor) {
        return valor == null || valor.isBlank();
    }

    /**
     * Dados recebidos no login.
     */
    public record LoginRequest(
            String nome,
            String sobrenome,
            String senha
    ) {
    }

    /**
     * Resposta pública com informações do usuário.
     *
     * Não contém senha, CPF ou dados internos
     * de autenticação.
     *
     * As permissões servem apenas para apresentação.
     * A autorização real deve ocorrer no backend.
     */
    public record UsuarioResponse(
            long ru,
            String nome,
            String sobrenome,
            String nomeClasse,
            Set<String> permissoes,
            String perfil,
            boolean ativo
    ) {

        /**
         * Converte a entidade de domínio em um DTO.
         */
        public static UsuarioResponse from(Usuario usuario) {

            ClasseFuncionario classe = usuario.getClasse();

            Set<String> permissoes = classe == null
                    ? Set.of()
                    : classe.getPermissoes()
                            .stream()
                            .map(Enum::name)
                            .collect(Collectors.toUnmodifiableSet());

            String nomeClasse = classe == null
                    ? null
                    : classe.getNome();

            String perfil = usuario.getPerfil() == null
                    ? null
                    : usuario.getPerfil().name();

            return new UsuarioResponse(
                    usuario.getRu(),
                    usuario.getNome(),
                    usuario.getSobrenome(),
                    nomeClasse,
                    permissoes,
                    perfil,
                    usuario.isAtivo()
            );
        }
    }
}

