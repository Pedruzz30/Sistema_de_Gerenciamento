package controller;

import model.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import repository.UsuarioRepository;

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

    /**
     * Resolves the acting user with a fallback actor used by some legacy controllers.
     *
     * When the header is absent or invalid, the fallback user is returned if active.
     */
    public static Usuario resolveUser(String ruHeader, UsuarioRepository repo, Usuario fallbackUser) {
        if (fallbackUser == null) {
            return resolveUser(ruHeader, repo);
        }

        if (!fallbackUser.isAtivo()) {
            throw new SecurityException("Usuario fallback inativo.");
        }

        if (ruHeader == null || ruHeader.isBlank()) {
            return fallbackUser;
        }

        try {
            return resolveUser(ruHeader, repo);
        } catch (SecurityException ignored) {
            return fallbackUser;
        }
    }
}
