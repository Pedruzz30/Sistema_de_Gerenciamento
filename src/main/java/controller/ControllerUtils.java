package controller;

import model.Usuario;
import repository.UsuarioRepository;

/**
 * Utility methods shared across REST controllers.
 */
public class ControllerUtils {

    /**
     * Resolves the acting user from the X-User-RU request header.
     *
     * If the header is present and matches a known user, that user is returned.
     * Otherwise falls back to the supplied adminSuperior bean so that
     * existing behaviour is preserved for callers that do not send the header.
     */
    public static Usuario resolveUser(String ruHeader,
                                      UsuarioRepository repo,
                                      Usuario fallback) {
        if (ruHeader != null && !ruHeader.isBlank()) {
            try {
                long ru = Long.parseLong(ruHeader.trim());
                return repo.buscarPorRu(ru).orElse(fallback);
            } catch (NumberFormatException ignored) {
                // header value is not a valid number — fall through to fallback
            }
        }
        return fallback;
    }
}
