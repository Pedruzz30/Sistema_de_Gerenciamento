package controller;

import model.Permissao;
import model.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import repository.UsuarioRepository;

import java.util.Arrays;
import java.util.Objects;

/**
 * Utility methods shared across REST controllers.
 */
public final class ControllerUtils {
    private static final Logger log = LoggerFactory.getLogger(ControllerUtils.class);

    private ControllerUtils() {
    }

    /**
     * Resolves the acting user from the X-User-RU request header.
     *
     * Requests are denied when identity is missing, malformed, unknown or inactive.
     */
    public static Usuario resolveUser(String ruHeader, UsuarioRepository repo) {
        if (ruHeader == null || ruHeader.isBlank()) {
            log.warn("X-User-RU ausente.");
            throw new SecurityException("Identidade ausente. Informe o cabecalho X-User-RU.");
        }

        final long ru;
        try {
            ru = Long.parseLong(ruHeader.trim());
        } catch (NumberFormatException ignored) {
            log.warn("X-User-RU invalido ('{}').", ruHeader);
            throw new SecurityException("Identidade invalida. X-User-RU deve ser numerico.");
        }

        Usuario usuario = repo.buscarPorRu(ru)
                .orElseThrow(() -> {
                    log.warn("X-User-RU '{}' nao encontrado.", ruHeader);
                    return new SecurityException("Usuario nao encontrado para o cabecalho X-User-RU.");
                });

        if (!usuario.isAtivo()) {
            log.warn("X-User-RU '{}' pertence a usuario inativo.", ruHeader);
            throw new SecurityException("Usuario inativo.");
        }

        return usuario;
    }

    public static Usuario resolveUserAndRequirePermission(String ruHeader,
                                                          UsuarioRepository repo,
                                                          Permissao permissao,
                                                          String mensagemErro) {
        Usuario usuario = resolveUser(ruHeader, repo);
        requirePermission(usuario, permissao, mensagemErro);
        return usuario;
    }

    public static Usuario resolveUserAndRequireAnyPermission(String ruHeader,
                                                             UsuarioRepository repo,
                                                             String mensagemErro,
                                                             Permissao... permissoes) {
        Usuario usuario = resolveUser(ruHeader, repo);
        requireAnyPermission(usuario, mensagemErro, permissoes);
        return usuario;
    }

    public static void requirePermission(Usuario usuario,
                                         Permissao permissao,
                                         String mensagemErro) {
        if (usuario == null
                || usuario.getClasse() == null
                || !usuario.getClasse().possuiPermissao(permissao)) {
            throw new SecurityException(mensagemErro);
        }
    }

    public static void requireAnyPermission(Usuario usuario,
                                            String mensagemErro,
                                            Permissao... permissoes) {
        boolean possuiAlgumaPermissao = usuario != null
                && usuario.getClasse() != null
                && Arrays.stream(permissoes)
                .filter(Objects::nonNull)
                .anyMatch(usuario.getClasse()::possuiPermissao);
        if (!possuiAlgumaPermissao) {
            throw new SecurityException(mensagemErro);
        }
    }
}
